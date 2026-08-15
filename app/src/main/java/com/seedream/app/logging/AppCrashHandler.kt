package com.seedream.app.logging

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Installs a default uncaught-exception handler that, before handing the crash
 * to the previous handler, writes the exception stack trace to the log file and
 * flushes it so the moments before and at the crash survive process death.
 */
object AppCrashHandler {
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        if (previousHandler != null) return // already installed
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogEventBus.logBlock("== CRASH on ${thread.name} ==")
                LogEventBus.logBlock(throwable.stackTraceToString())
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
     */
    fun startLogcatReader() {
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
