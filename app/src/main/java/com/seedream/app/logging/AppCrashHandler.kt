package com.seedream.app.logging

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs a default uncaught-exception handler that, before handing the crash
 * to the previous handler, writes the exception stack trace to the log file and
 * flushes it so the moments before and at the crash survive process death.
 */
object AppCrashHandler {
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private val logcatReaderRunning = AtomicBoolean(false)

    fun install() {
        if (previousHandler != null) return // already installed
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogEventBus.logBlock("== CRASH on ${thread.name} ==")
                // An OutOfMemoryError means the heap is already gone; building
                // the full stack trace string allocates and would fail the same
                // way, taking the report with it. The type and message cost
                // nothing extra and are the part that matters.
                val detail = if (throwable is OutOfMemoryError) {
                    "${throwable.javaClass.name}: ${throwable.message}"
                } else {
                    throwable.stackTraceToString()
                }
                LogEventBus.logBlock(detail)
                LogEventBus.flush()
            } catch (_: Throwable) {
                // Never let logging itself break the crash path.
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Background reader that tails system logcat for crash lines the process
     * itself may not observe (e.g. a service thread killed by the runtime). Only
     * crash-relevant lines are captured to keep the file small.
     *
     * Idempotent on purpose: logging can be switched on and off repeatedly, and
     * without this guard every toggle left another reader running, so one crash
     * ended up appended to the log once per reader.
     */
    fun startLogcatReader() {
        if (!logcatReaderRunning.compareAndSet(false, true)) return
        Thread {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "time", "*:E")
                )
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (isCrashLine(line)) {
                        LogEventBus.logBlock(line)
                    }
                }
            } catch (_: Throwable) {
                // logcat is best-effort; do not crash the app if unavailable.
            } finally {
                logcatReaderRunning.set(false)
            }
        }.apply {
            name = "Seedream-LogcatReader"
            isDaemon = true
            start()
        }
    }

    private fun isCrashLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("fatal exception") ||
            lower.contains("androidruntime") ||
            lower.contains("uncaught exception") ||
            lower.contains("process: com.seedream.app, died")
    }
}
