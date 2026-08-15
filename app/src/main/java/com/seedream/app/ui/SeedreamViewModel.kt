package com.seedream.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seedream.app.model.DEFAULT_ENDPOINT
import com.seedream.app.model.MODEL_SEEDREAM_4_5
import com.seedream.app.model.MODEL_SEEDREAM_5
import com.seedream.app.model.ReferenceImage
import com.seedream.app.model.ReferenceKind
import com.seedream.app.model.RequestInput
import com.seedream.app.model.SeedreamRequest
import com.seedream.app.model.StatusKind
import com.seedream.app.model.buildSeedreamRequest
import com.seedream.app.model.parseUrlReferenceImages
import com.seedream.app.model.supportsStream
import com.seedream.app.model.supportsWebSearch
import com.seedream.app.network.SearchClient
import com.seedream.app.network.SearchProvider
import com.seedream.app.network.SearchResult
import com.seedream.app.network.SeedreamApiClient
import com.seedream.app.service.GenerationEvent
import com.seedream.app.service.GenerationEvents
import com.seedream.app.service.GenerationForegroundService
import com.seedream.app.storage.HistoryEntity
import com.seedream.app.storage.HistoryRepository
import com.seedream.app.storage.KeyStorage
import com.seedream.app.storage.SettingsStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.system.measureTimeMillis

class SeedreamViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStorage = KeyStorage(application)
    private val settingsStorage = SettingsStorage(application)
    private val historyRepository = HistoryRepository(application)
    private val latencyClient = SeedreamApiClient().newLatencyClient()
    private val searchClient = SearchClient()
    private val referencePayloads = mutableMapOf<String, String>()
    private var lastRequest: SeedreamRequest? = null
    private var latencyJob: Job? = null
    private var historyJob: Job? = null
    private var historyLimit = HISTORY_PAGE_SIZE

    private val _uiState = MutableStateFlow(
        SeedreamUiState(
            apiKey = keyStorage.loadApiKey(),
            endpoint = settingsStorage.getSetting("endpoint", DEFAULT_ENDPOINT),
            model = settingsStorage.getSetting("model", MODEL_SEEDREAM_5),
            size = settingsStorage.getSetting("size", "2K"),
            seed = settingsStorage.getSetting("seed", ""),
            responseFormat = settingsStorage.getSetting("responseFormat", "url"),
            watermark = settingsStorage.getSetting("watermark", "false"),
            stream = settingsStorage.getSetting("stream", "false"),
            sequentialMode = settingsStorage.getSetting("sequentialMode", "disabled"),
            maxImages = settingsStorage.getSetting("maxImages", ""),
            outputFormat = settingsStorage.getSetting("outputFormat", "jpeg"),
            webSearch = settingsStorage.getSetting("webSearch", "false"),
            externalSearch = settingsStorage.getSetting("externalSearch", "false"),
            searchProvider = settingsStorage.getSetting("searchProvider", "tavily"),
            searchApiKey = keyStorage.loadSearchApiKey(settingsStorage.getSetting("searchProvider", "tavily")),
            loggingEnabled = settingsStorage.getSetting("loggingEnabled", "false")
        )
    )
    val uiState: StateFlow<SeedreamUiState> = _uiState

    init {
        refreshHistory()
        viewModelScope.launch {
            GenerationEvents.events.collect(::handleGenerationEvent)
        }
    }

    fun setApiKey(value: String) = _uiState.update { it.copy(apiKey = value) }
    fun setEndpoint(value: String) {
        settingsStorage.saveSetting("endpoint", value)
        _uiState.update { it.copy(endpoint = value) }
    }
    fun setModel(value: String) {
        settingsStorage.saveSetting("model", value)
        _uiState.update {
            val nextWebSearch = if (!supportsWebSearch(value)) {
                settingsStorage.saveSetting("webSearch", "false")
                "false"
            } else {
                it.webSearch
            }
            val nextStream = if (!supportsStream(value)) {
                settingsStorage.saveSetting("stream", "false")
                "false"
            } else {
                it.stream
            }
            it.copy(
                model = value,
                webSearch = nextWebSearch,
                stream = nextStream
            )
        }
    }
    fun setPrompt(value: String) = _uiState.update { it.copy(prompt = value) }
    fun setSize(value: String) {
        settingsStorage.saveSetting("size", value)
        _uiState.update { it.copy(size = value) }
    }
    fun setSeed(value: String) {
        settingsStorage.saveSetting("seed", value)
        _uiState.update { it.copy(seed = value) }
    }
    fun setResponseFormat(value: String) {
        settingsStorage.saveSetting("responseFormat", value)
        _uiState.update { it.copy(responseFormat = value) }
    }
    fun setWatermark(value: String) {
        settingsStorage.saveSetting("watermark", value)
        _uiState.update { it.copy(watermark = value) }
    }
    fun setStream(value: String) {
        settingsStorage.saveSetting("stream", value)
        _uiState.update { it.copy(stream = value) }
    }
    fun setSequentialMode(value: String) {
        settingsStorage.saveSetting("sequentialMode", value)
        _uiState.update { it.copy(sequentialMode = value) }
    }
    fun setMaxImages(value: String) {
        settingsStorage.saveSetting("maxImages", value)
        _uiState.update { it.copy(maxImages = value) }
    }
    fun setOutputFormat(value: String) {
        settingsStorage.saveSetting("outputFormat", value)
        _uiState.update { it.copy(outputFormat = value) }
    }
    fun setWebSearch(value: String) {
        settingsStorage.saveSetting("webSearch", value)
        _uiState.update { it.copy(webSearch = value) }
    }
    fun setExternalSearch(value: String) {
        settingsStorage.saveSetting("externalSearch", value)
        _uiState.update { it.copy(externalSearch = value) }
    }
    fun setSearchProvider(value: String) {
        settingsStorage.saveSetting("searchProvider", value)
        _uiState.update {
            it.copy(
                searchProvider = value,
                searchApiKey = keyStorage.loadSearchApiKey(value)
            )
        }
    }
    fun setSearchApiKey(value: String) = _uiState.update { it.copy(searchApiKey = value) }

    fun setLoggingEnabled(value: String) {
        settingsStorage.saveSetting("loggingEnabled", value)
        _uiState.update { it.copy(loggingEnabled = value) }
        val app = getApplication<Application>()
        if (app is com.seedream.app.SeedreamApplication) {
            if (value == "true") {
                app.enableLogging()
                loadLogContent()
            } else {
                app.disableLogging()
                _uiState.update { it.copy(logContent = "", logFileInfo = "") }
            }
        }
    }

    fun loadLogContent() {
        val dir = getApplication<Application>().filesDir
        val logFile = java.io.File(dir, "logs/app.log")
        val content = if (logFile.exists()) logFile.readText() else "(暂无日志内容)"
        val info = if (logFile.exists()) {
            val kb = logFile.length() / 1024.0
            "日志文件：%.1f KB".format(kb)
        } else {
            "日志文件：不存在"
        }
        _uiState.update { it.copy(logContent = content, logFileInfo = info) }
    }

    fun clearLog() {
        val dir = getApplication<Application>().filesDir
        val logFile = java.io.File(dir, "logs/app.log")
        logFile.delete()
        _uiState.update { it.copy(logContent = "", logFileInfo = "日志文件：不存在") }
    }

    /** Shares the log file via the system share sheet (微信/QQ/邮件等). */
    fun exportLog(context: Context) {
        val logFile = java.io.File(context.filesDir, "logs/app.log")
        if (!logFile.exists()) {
            _uiState.update { it.copy(logFileInfo = "日志文件：不存在，无法导出") }
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Seedream 运行日志")
            putExtra(android.content.Intent.EXTRA_TEXT, "SeedreamAPP 运行日志（含崩溃信息），请查收")
        }
        val chooser = android.content.Intent.createChooser(intent, "导出日志")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun setHistorySearch(value: String) {
        historyLimit = HISTORY_PAGE_SIZE
        _uiState.update {
            it.copy(
                historySearch = value,
                history = emptyList(),
                historyTotalCount = 0,
                historyLoadedLimit = historyLimit
            )
        }
        refreshHistory()
    }
    fun toggleHistory() = _uiState.update { it.copy(historyOpen = !it.historyOpen) }
    fun openImage(src: String) = _uiState.update { it.copy(fullScreenImage = src) }
    fun closeImage() = _uiState.update { it.copy(fullScreenImage = null) }
    fun toggleMask() = _uiState.update { it.copy(keyMasked = !it.keyMasked) }

    fun pasteApiKey(text: String?) {
        if (text.isNullOrBlank()) {
            setStatus("剪贴板为空或无权限", StatusKind.Error)
        } else {
            _uiState.update { it.copy(apiKey = text.trim()) }
            setStatus("已从剪贴板读取 API Key", StatusKind.Ok)
        }
    }

    fun saveApiKey() {
        keyStorage.saveApiKey(_uiState.value.apiKey)
        setStatus("已保存 API Key 到本机加密存储", StatusKind.Ok)
    }

    fun clearApiKey() {
        keyStorage.clearApiKey()
        _uiState.update { it.copy(apiKey = "") }
        setStatus("已清空 API Key", StatusKind.Muted)
    }

    fun saveSearchApiKey() {
        val state = _uiState.value
        keyStorage.saveSearchApiKey(state.searchProvider, state.searchApiKey)
        setStatus("已保存搜索服务 API Key", StatusKind.Ok)
    }

    fun clearSearchApiKey() {
        val provider = _uiState.value.searchProvider
        keyStorage.clearSearchApiKey(provider)
        _uiState.update { it.copy(searchApiKey = "") }
        setStatus("已清空搜索服务 API Key", StatusKind.Muted)
    }

    fun setUrlImagesText(text: String) {
        val urlRefs = parseUrlReferenceImages(text)
        _uiState.update { state ->
            state.copy(
                urlImagesText = text,
                references = state.references.filter { it.kind == ReferenceKind.File } + urlRefs
            )
        }
    }

    fun addLocalImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            setStatus("正在读取本地图片...", StatusKind.Muted)
            val encodedItems = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { uri.toReferenceImage(context) }.getOrNull() }
            }
            encodedItems.forEach { referencePayloads[it.image.id] = it.payload }
            val localItems = encodedItems.map { it.image }
            if (localItems.isEmpty()) {
                setStatus("没有读到可用图片，请换一张图片或降低图片大小后重试", StatusKind.Error)
                return@launch
            }
            _uiState.update { state ->
                state.copy(references = state.references + localItems)
            }
            setStatus("已加入 ${localItems.size} 张本地图", StatusKind.Ok)
        }
    }

    fun clearReferences() {
        referencePayloads.clear()
        _uiState.update { it.copy(references = emptyList(), urlImagesText = "") }
        setStatus("已清空参考图", StatusKind.Muted)
    }

    fun moveReference(id: String, delta: Int) {
        _uiState.update { state ->
            val list = state.references.toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = from + delta
            if (from >= 0 && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            state.copy(
                references = list,
                urlImagesText = list.filter { it.kind == ReferenceKind.Url }.joinToString("\n") { it.value }
            )
        }
    }

    fun deleteReference(id: String) {
        referencePayloads.remove(id)
        _uiState.update { state ->
            val list = state.references.filterNot { it.id == id }
            state.copy(
                references = list,
                urlImagesText = list.filter { it.kind == ReferenceKind.Url }.joinToString("\n") { it.value }
            )
        }
    }

    fun deleteMultipleReferences(ids: List<String>) {
        ids.forEach { referencePayloads.remove(it) }
        _uiState.update { state ->
            val list = state.references.filterNot { it.id in ids }
            state.copy(
                references = list,
                urlImagesText = list.filter { it.kind == ReferenceKind.Url }.joinToString("\n") { it.value }
            )
        }
    }

    fun send(context: Context) {
        viewModelScope.launch {
            sendInternal(context.applicationContext)
        }
    }

    private suspend fun sendInternal(context: Context) {
        val state = _uiState.value
        val apiKey = state.apiKey.trim()
        val endpoint = state.endpoint.trim()
        if (apiKey.isBlank()) {
            setStatus("请先输入 API Key", StatusKind.Error)
            return
        }
        if (endpoint.isBlank()) {
            setStatus("请先输入接口地址", StatusKind.Error)
            return
        }

        val baseRequest = buildSeedreamRequest(state.toRequestInput())
        val request = withExternalSearchIfNeeded(baseRequest, state)
        if (request.prompt.isBlank()) {
            setStatus("prompt 不能为空", StatusKind.Error)
            return
        }
        if (request.image.size > 10 && request.model == MODEL_SEEDREAM_4_5) {
            setStatus("4.5 文档强调多图输入建议 2-10 张，当前超过 10 张，请检查", StatusKind.Error)
            return
        }

        lastRequest = request
        _uiState.update {
            it.copy(
                payloadPreview = request.toPreviewJsonString(),
                rawResponse = "(请求中...)",
                resultImages = emptyList(),
                retryMessage = null
            )
        }

        val intent = GenerationForegroundService.startIntent(
            context.applicationContext,
            request.toJsonString(),
            apiKey,
            endpoint
        )
        runCatching {
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }.onFailure {
            setStatus("启动前台服务失败：${it.message}", StatusKind.Error)
        }
    }

    fun retryLast(context: Context) {
        val request = lastRequest
        if (request == null) {
            setStatus("无历史请求可重试，请重新发送", StatusKind.Error)
            return
        }
        val state = _uiState.value
        if (state.apiKey.isBlank() || state.endpoint.isBlank()) {
            setStatus("API Key 或接口地址为空，无法重试", StatusKind.Error)
            return
        }
        _uiState.update { it.copy(retryMessage = null, resultImages = emptyList(), rawResponse = "(请求中...)") }
        ContextCompat.startForegroundService(
            context.applicationContext,
            GenerationForegroundService.startIntent(
                context.applicationContext,
                request.toJsonString(),
                state.apiKey.trim(),
                state.endpoint.trim()
            )
        )
    }

    fun stop(context: Context) {
        context.startService(GenerationForegroundService.stopIntent(context.applicationContext))
        _uiState.update { it.copy(isGenerating = false, retryMessage = null) }
        setStatus("已请求停止", StatusKind.Muted)
    }

    fun testNetworkLatency() {
        val endpoint = _uiState.value.endpoint.trim()
        if (endpoint.isBlank()) {
            _uiState.update { it.copy(netResult = "请先填写接口地址") }
            return
        }
        latencyJob?.cancel()
        latencyJob = viewModelScope.launch {
            _uiState.update { it.copy(netResult = "测试中...") }
            val results = withContext(Dispatchers.IO) {
                (1..3).map { measureLatency(endpoint) }
            }
            val valid = results.filterNotNull()
            val message = if (valid.isEmpty()) {
                "网络不通，3 次请求全部失败。请检查网络或接口地址。"
            } else {
                val avg = valid.average().toInt()
                val fail = results.size - valid.size
                val failNote = if (fail > 0) "（$fail 次失败）" else ""
                "平均 ${avg}ms，最快 ${valid.min()}ms，最慢 ${valid.max()}ms$failNote"
            }
            _uiState.update { it.copy(netResult = message) }
        }
    }

    fun saveResultImage(src: String) {
        viewModelScope.launch {
            val uri = historyRepository.saveToPictures(src)
            setStatus(if (uri != null) "已保存到相册 Pictures/Seedream" else "保存失败", if (uri != null) StatusKind.Ok else StatusKind.Error)
        }
    }

    fun deleteHistory(item: HistoryEntity) {
        viewModelScope.launch {
            historyRepository.delete(item.id)
            refreshHistory()
            setStatus("已删除历史图片 #${item.id}", StatusKind.Muted)
        }
    }

    fun deleteHistoryItems(items: List<HistoryEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            items.forEach { historyRepository.delete(it.id) }
            refreshHistory()
            setStatus("已删除 ${items.size} 条历史记录", StatusKind.Muted)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            historyLimit = HISTORY_PAGE_SIZE
            refreshHistory()
            setStatus("已清空全部历史记录", StatusKind.Muted)
        }
    }

    fun saveHistoryImage(item: HistoryEntity) {
        viewModelScope.launch {
            val uri = historyRepository.saveToPictures(historyRepository.displaySource(item))
            setStatus(if (uri != null) "已下载历史图片 #${item.id}" else "下载受限，无法保存", if (uri != null) StatusKind.Ok else StatusKind.Error)
        }
    }

    fun saveHistoryImages(items: List<HistoryEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            items.forEach { item ->
                if (historyRepository.saveToPictures(historyRepository.displaySource(item)) != null) ok += 1
            }
            setStatus("已下载 $ok/${items.size} 张历史图片", if (ok > 0) StatusKind.Ok else StatusKind.Error)
        }
    }

    fun notifyPromptCopied() {
        setStatus("已复制 Prompt", StatusKind.Ok)
    }

    fun notifyHistoryLinksCopied(count: Int) {
        setStatus(if (count > 0) "已复制 $count 个原始链接" else "选中记录没有原始链接", if (count > 0) StatusKind.Ok else StatusKind.Warn)
    }

    fun historyDisplaySource(item: HistoryEntity): String = historyRepository.displaySource(item)

    fun loadMoreHistory() {
        val state = _uiState.value
        if (state.historyLoading || state.history.size >= state.historyTotalCount) return
        historyLimit = (historyLimit + HISTORY_PAGE_SIZE).coerceAtMost(state.historyTotalCount)
        refreshHistory()
    }

    private fun refreshHistory() {
        historyJob?.cancel()
        val keyword = _uiState.value.historySearch
        val limit = historyLimit
        _uiState.update { it.copy(historyLoading = true, historyLoadedLimit = limit) }
        historyJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    historyRepository.load(limit, keyword)
                }
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        history = page.items,
                        historyTotalCount = page.totalCount,
                        historyLoading = false,
                        historyLoadedLimit = limit
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update { it.copy(historyLoading = false) }
                setStatus("历史记录加载失败：${error.message.orEmpty()}", StatusKind.Error)
            }
        }
    }

    private fun handleGenerationEvent(event: GenerationEvent) {
        when (event) {
            GenerationEvent.Started -> _uiState.update {
                it.copy(isGenerating = true, status = "请求发送中...", statusKind = StatusKind.Muted)
            }
            GenerationEvent.Completed -> _uiState.update { it.copy(isGenerating = false, retryMessage = null) }
            GenerationEvent.Cancelled -> _uiState.update { it.copy(isGenerating = false, retryMessage = null) }
            is GenerationEvent.Failed -> _uiState.update {
                it.copy(isGenerating = false, retryMessage = event.message)
            }
            is GenerationEvent.RawResponse -> _uiState.update { it.copy(rawResponse = event.text) }
            is GenerationEvent.Result -> {
                _uiState.update {
                    it.copy(resultImages = it.resultImages + event.image)
                }
                refreshHistory()
            }
            is GenerationEvent.RetryScheduled -> _uiState.update {
                it.copy(
                    status = "第 ${event.attempt}/${event.maxRetries} 次重试中（${event.delayMillis / 1000}s 退避）...",
                    statusKind = StatusKind.Warn
                )
            }
            is GenerationEvent.Status -> _uiState.update {
                it.copy(status = event.message, statusKind = event.kind)
            }
        }
    }

    private fun setStatus(message: String, kind: StatusKind) {
        _uiState.update { it.copy(status = message, statusKind = kind) }
    }

    private fun SeedreamUiState.toRequestInput(): RequestInput {
        return RequestInput(
            model = model,
            prompt = prompt,
            references = references.map { reference ->
                referencePayloads[reference.id]?.let { payload -> reference.copy(value = payload) } ?: reference
            },
            size = size,
            seed = seed,
            responseFormat = responseFormat,
            watermark = watermark,
            stream = stream,
            sequentialImageGeneration = sequentialMode,
            maxImages = maxImages,
            outputFormat = outputFormat,
            webSearch = webSearch
        )
    }

    private suspend fun withExternalSearchIfNeeded(request: SeedreamRequest, state: SeedreamUiState): SeedreamRequest {
        if (state.externalSearch != "true") {
            _uiState.update { it.copy(searchSummary = "") }
            return request
        }

        val provider = SearchProvider.fromId(state.searchProvider)
        val searchKey = state.searchApiKey.trim()
        if (provider.requiresApiKey && searchKey.isBlank()) {
            setStatus("请先填写并保存 ${provider.label} 的搜索 API Key", StatusKind.Error)
            return request.copy(prompt = "")
        }

        setStatus("正在通过 ${provider.label} 联网搜索...", StatusKind.Muted)
        val results = runCatching {
            withContext(Dispatchers.IO) {
                searchClient.search(provider, request.prompt, searchKey)
            }
        }.getOrElse { error ->
            _uiState.update { it.copy(searchSummary = "搜索失败：${error.message.orEmpty()}") }
            setStatus("${provider.label} 搜索失败：${error.message}", StatusKind.Error)
            return request.copy(prompt = "")
        }
        if (results.isEmpty()) {
            _uiState.update { it.copy(searchSummary = "未检索到可用结果，已使用原 Prompt。") }
            setStatus("${provider.label} 未返回可用搜索结果，继续使用原 Prompt", StatusKind.Warn)
            return request
        }

        val contextBlock = results.toPromptContext(provider)
        _uiState.update { it.copy(searchSummary = contextBlock) }
        setStatus("已检索 ${results.size} 条结果，正在发送到 302.ai...", StatusKind.Ok)
        return request.copy(prompt = request.prompt.appendSearchContext(contextBlock))
    }

    private fun List<SearchResult>.toPromptContext(provider: SearchProvider): String {
        return buildString {
            appendLine("Search provider: ${provider.label}")
            this@toPromptContext.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.title}".take(240))
                if (result.snippet.isNotBlank()) appendLine("   ${result.snippet}".take(700))
                if (result.url.isNotBlank()) appendLine("   Source: ${result.url}".take(400))
            }
        }.trim()
    }

    private fun String.appendSearchContext(contextBlock: String): String {
        return """
            $this

            [联网检索资料]
            $contextBlock

            请优先参考以上联网检索资料中的事实、时间、名称、颜色、外观和场景信息来生成图片；不要在画面中绘制网址或检索列表文字，除非原始提示词明确要求。
        """.trimIndent()
    }

    private fun Uri.toReferenceImage(context: Context): EncodedReferenceImage? {
        val resolver = context.contentResolver
        val name = resolver.query(this, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: lastPathSegment ?: "local-image"
        val bytes = encodeReferenceImage(context, this) ?: return null
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return EncodedReferenceImage(
            image = ReferenceImage(
                kind = ReferenceKind.File,
                name = name,
                value = toString(),
                preview = toString()
            ),
            payload = "data:image/jpeg;base64,$b64"
        )
    }

    private fun encodeReferenceImage(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_REFERENCE_DIMENSION)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val scaled = scaleDownIfNeeded(decoded, MAX_REFERENCE_DIMENSION)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, REFERENCE_JPEG_QUALITY, out)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            out.toByteArray()
        }.getOrNull()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sample >= maxDimension || halfHeight / sample >= maxDimension) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun measureLatency(endpoint: String): Long? {
        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            measureTimeMillis {
                latencyClient.newCall(request).execute().use { }
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_REFERENCE_DIMENSION = 1280
        const val REFERENCE_JPEG_QUALITY = 82
        const val HISTORY_PAGE_SIZE = 100
    }

    private data class EncodedReferenceImage(
        val image: ReferenceImage,
        val payload: String
    )
}
