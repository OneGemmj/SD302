package com.seedream.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.seedream.app.MainActivity
import com.seedream.app.R
import com.seedream.app.model.ResultImage
import com.seedream.app.model.StatusKind
import com.seedream.app.logging.LogEventBus
import com.seedream.app.network.ImageExtractor
import com.seedream.app.network.RetryPolicy
import com.seedream.app.network.SeedreamApiClient
import com.seedream.app.network.SseParser
import com.seedream.app.storage.HistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class GenerationForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apiClient = SeedreamApiClient()
    private lateinit var historyRepository: HistoryRepository
    private var generationJob: Job? = null
    @Volatile private var currentCall: Call? = null
    @Volatile private var currentResponse: Response? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Routes service-lifecycle calls to the main thread and runs teardown exactly once. */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopCoordinator = ServiceStopCoordinator(
        onStop = { teardownService() },
        dispatchToMain = { action -> runOnMain(action) }
    )

    /** Set while the generation coroutine is deliberately cancelled; guards
     *  against a close-induced IOException being misread as a network error. */
    private val cancellationRequested = AtomicBoolean(false)

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    /** Releases resources and stops the service. Called exactly once (guarded by
     *  [stopCoordinator]) and always on the main thread. */
    private fun teardownService() {
        releaseWakeLock()
        currentCall = null
        currentResponse = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        historyRepository = HistoryRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelGeneration()
            return START_NOT_STICKY
        }

        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        val pending = GenerationRequestStore.take(requestId)
        if (pending == null) {
            GenerationEvents.tryEmit(GenerationEvent.Failed("请求参数不完整"))
            // A service started via startForegroundService() must call
            // startForeground() before it can be torn down, otherwise API 26+
            // throws ForegroundServiceDidNotStartInTimeException. Promote to
            // foreground first, then stop.
            runOnMain {
                startForeground(NOTIFICATION_ID, buildNotification("请求已结束", false))
                stopCoordinator.stop()
            }
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("请求发送中...", true))
        generationJob?.cancel()
        generationJob = scope.launch {
            runGeneration(pending.payloadJson, pending.apiKey, pending.endpoint)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // The service is already being torn down; cancel in-flight network work
        // and release the wake lock, but never call stopForeground/stopSelf
        // from onDestroy.
        cancellationRequested.set(true)
        LogEventBus.log("onDestroy: cancelling generation job")
        currentCall?.cancel()
        closeResponseAsync()
        generationJob?.cancel()
        generationJob = null
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runGeneration(payloadJson: String, apiKey: String, endpoint: String) {
        val payload = JSONObject(payloadJson)
        val prompt = payload.optString("prompt")
        val model = payload.optString("model")
        val responseFormat = payload.optString("response_format", "url")
        val stream = payload.optBoolean("stream", false)
        var lastError: Throwable? = null

        acquireWakeLock()
        LogEventBus.log("runGeneration: started model=$model stream=$stream")
        GenerationEvents.emit(GenerationEvent.Started)

        try {
            for (attempt in 0..RetryPolicy.maxRetries) {
                if (attempt > 0) {
                    val delayMillis = RetryPolicy.delayMillis(attempt)
                    LogEventBus.log("runGeneration: retry $attempt/${RetryPolicy.maxRetries} after ${delayMillis}ms")
                    updateNotification("第 $attempt/${RetryPolicy.maxRetries} 次重试中...")
                    GenerationEvents.emit(
                        GenerationEvent.RetryScheduled(attempt, RetryPolicy.maxRetries, delayMillis)
                    )
                    delay(delayMillis)
                } else {
                    GenerationEvents.emit(GenerationEvent.Status("请求发送中...", StatusKind.Muted))
                }

                try {
                    apiClient.postJson(endpoint, apiKey, payloadJson) { currentCall = it }.use { response ->
                        currentCall = null
                        currentResponse = response
                        if (!response.isSuccessful) {
                            val bodyText = response.body?.string().orEmpty()
                            GenerationEvents.emit(GenerationEvent.RawResponse(bodyText.compactForUi().ifBlank { "HTTP ${response.code}" }))
                            if (response.code in 400..499) {
                                val message = "请求失败：HTTP ${response.code}（客户端错误，不重试）"
                                GenerationEvents.emit(GenerationEvent.Status(message, StatusKind.Error))
                                GenerationEvents.emit(GenerationEvent.Failed(message))
                                return
                            }
                            if (RetryPolicy.shouldRetryHttp(response.code) && attempt < RetryPolicy.maxRetries) {
                                lastError = IllegalStateException("HTTP ${response.code}")
                                return@use
                            }
                            val message = "请求失败：HTTP ${response.code}"
                            GenerationEvents.emit(GenerationEvent.Status(message, StatusKind.Error))
                            GenerationEvents.emit(GenerationEvent.Failed(message))
                            return
                        }

                        if (stream) {
                            readSseStream(response, responseFormat, prompt, model)
                        } else {
                            readJsonResponse(response, responseFormat, prompt, model, attempt)
                        }
                        GenerationEvents.emit(GenerationEvent.Completed)
                        currentResponse = null
                        return
                    }
                    currentResponse = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // Closing the response while the streaming coroutine is
                    // blocked on stream.read() surfaces as an IOException. When
                    // a stop was requested, that is a cancellation, not a
                    // network error to retry. Surface it as a CancellationException
                    // so it follows the normal cancellation path (rethrowing the
                    // raw IOException would escape the coroutine and crash).
                    if (cancellationRequested.get()) {
                        LogEventBus.log("runGeneration: cancelled while in flight: ${error.javaClass.simpleName}: ${error.message}")
                        throw CancellationException("Generation cancelled", error)
                    }
                    LogEventBus.log("runGeneration: attempt $attempt failed: ${error.javaClass.simpleName}: ${error.message}")
                    currentCall = null
                    currentResponse = null
                    lastError = error
                    GenerationEvents.emit(GenerationEvent.RawResponse(error.stackTraceToString()))
                    if (attempt >= RetryPolicy.maxRetries) break
                }
            }

            val message = "请求异常（已重试${RetryPolicy.maxRetries}次）：${lastError?.message ?: "未知错误"}"
            GenerationEvents.emit(GenerationEvent.Status(message, StatusKind.Error))
            GenerationEvents.emit(GenerationEvent.Failed(message))
        } catch (_: CancellationException) {
            LogEventBus.log("runGeneration: cancelled, entering cancellation path")
            GenerationEvents.emit(GenerationEvent.Status("请求已取消", StatusKind.Muted))
            GenerationEvents.emit(GenerationEvent.Cancelled)
        } finally {
            LogEventBus.log("runGeneration: finally teardown via stopCoordinator")
            stopCoordinator.stop()
        }
    }

    private suspend fun readJsonResponse(
        response: Response,
        responseFormat: String,
        prompt: String,
        model: String,
        attempt: Int
    ) {
        val bodyText = response.body?.string().orEmpty()
        GenerationEvents.emit(GenerationEvent.RawResponse(bodyText.compactForUi().ifBlank { "(空响应)" }))
        val parsed = ImageExtractor.parseJson(bodyText)
        val direct = ImageExtractor.directImagesFromResponse(parsed, responseFormat)
        val images = direct.ifEmpty { ImageExtractor.findImageUrlsDeep(parsed) }

        images.forEachIndexed { index, src ->
            val displaySrc = historyRepository.saveGeneratedImage(src, prompt, model).ifBlank { src }
            val image = ResultImage(src = displaySrc, note = if (direct.isEmpty()) "auto-detected-url" else responseFormat)
            GenerationEvents.emit(GenerationEvent.Result(image))
        }

        val error = (parsed as? JSONObject)?.optJSONObject("error")
        if (error != null) {
            GenerationEvents.emit(
                GenerationEvent.Status(
                    "接口错误：${error.optString("code")} ${error.optString("message")}",
                    StatusKind.Error
                )
            )
        } else {
            val retryNote = if (attempt > 0) "（经${attempt}次重试）" else ""
            GenerationEvents.emit(
                GenerationEvent.Status("成功，得到 ${images.size} 张图片$retryNote", if (images.isNotEmpty()) StatusKind.Ok else StatusKind.Muted)
            )
        }
    }

    private suspend fun readSseStream(
        response: Response,
        responseFormat: String,
        prompt: String,
        model: String
    ) {
        val parser = SseParser()
        val rawChunks = StringBuilder()
        val stream = response.body?.byteStream() ?: return
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var imageIndex = 0

        while (currentCoroutineContext().isActive) {
            val read = stream.read(buffer)
            if (read <= 0) break
            val chunk = String(buffer, 0, read, Charsets.UTF_8)
            rawChunks.append(chunk)
            parser.accept(chunk).forEach { json ->
                val direct = ImageExtractor.directImagesFromResponse(json, responseFormat)
                val images = direct.ifEmpty { ImageExtractor.findImageUrlsDeep(json) }
                images.forEach { src ->
                    val note = if (direct.isEmpty()) "stream-auto-detected-url" else "stream-$responseFormat"
                    val displaySrc = historyRepository.saveGeneratedImage(src, prompt, model).ifBlank { src }
                    val image = ResultImage(src = displaySrc, note = note)
                    imageIndex += 1
                    GenerationEvents.emit(GenerationEvent.Result(image))
                }
                json.optJSONObject("error")?.let { error ->
                    GenerationEvents.emit(
                        GenerationEvent.Status(
                            "流式返回错误：${error.optString("code")} ${error.optString("message")}",
                            StatusKind.Error
                        )
                    )
                }
            }
        }

        GenerationEvents.emit(GenerationEvent.RawResponse(rawChunks.toString().compactForUi().ifBlank { "(流式无文本返回)" }))
        GenerationEvents.emit(GenerationEvent.Status("流式请求完成，得到 $imageIndex 张图片", StatusKind.Ok))
    }

    private fun String.compactForUi(): String {
        if (length <= MAX_RAW_RESPONSE_PREVIEW) return this
        return take(MAX_RAW_RESPONSE_PREVIEW) +
            "\n\n...（响应内容较大，界面已截断 ${length - MAX_RAW_RESPONSE_PREVIEW} 个字符以避免卡顿；图片已解析并缓存）"
    }

    private fun cancelGeneration() {
        cancellationRequested.set(true)
        LogEventBus.log("cancelGeneration: stop requested")
        currentCall?.cancel()
        closeResponseAsync()
        generationJob?.cancel()
        generationJob = null
        GenerationEvents.tryEmit(GenerationEvent.Cancelled)
        stopCoordinator.stop()
    }

    /**
     * Closes the in-flight response off the main thread. Closing an HTTP/2
     * response that is still transferring writes a RST_STREAM frame to the
     * socket; doing that on the main thread throws NetworkOnMainThreadException
     * (see the crash: cancelGeneration -> Response.close -> Http2Writer.rstStream).
     * A daemon thread is used so the close is not dropped when the service scope
     * is cancelled in onDestroy.
     */
    private fun closeResponseAsync() {
        val response = currentResponse
        currentResponse = null
        if (response != null) {
            Thread {
                runCatching { response.close() }
            }.apply {
                name = "Seedream-ResponseClose"
                isDaemon = true
                start()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_generation),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content, true))
    }

    private fun buildNotification(content: String, cancellable: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, GenerationForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(cancellable)
            .setOnlyAlertOnce(true)

        if (cancellable) {
            builder.addAction(0, "停止", stopIntent)
        }
        return builder.build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Seedream:Generation").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        // Guard against "WakeLock under-locked": release() throws when the
        // lock is no longer held. The coordinator already serializes calls,
        // but keep this defensive for any residual concurrent path.
        val lock = wakeLock
        if (lock != null && lock.isHeld) {
            lock.release()
        }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "seedream_generation"
        private const val NOTIFICATION_ID = 30250
        private const val ACTION_STOP = "com.seedream.app.action.STOP_GENERATION"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val MAX_RAW_RESPONSE_PREVIEW = 16_000

        fun startIntent(context: Context, payloadJson: String, apiKey: String, endpoint: String): Intent {
            val requestId = GenerationRequestStore.put(payloadJson, apiKey, endpoint)
            return Intent(context, GenerationForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, GenerationForegroundService::class.java).setAction(ACTION_STOP)
        }
    }
}
