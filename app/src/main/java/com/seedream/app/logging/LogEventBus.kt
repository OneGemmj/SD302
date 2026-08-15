package com.seedream.app.logging

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide logging entry point. The app and the foreground service call
 * [log] from any thread; if a [FileLogger] is attached, the message is written
 * to disk. When logging is toggled off the bus is simply detached and [log]
 * becomes a no-op.
 */
object LogEventBus {
    @Volatile private var logger: FileLogger? = null
    private val attached = AtomicBoolean(false)

    val isAttached: Boolean get() = attached.get()

    fun attach(fileLogger: FileLogger) {
        logger = fileLogger
        attached.set(true)
        fileLogger.log("== 日志记录已开启 ==")
        fileLogger.flush()
    }

    fun detach() {
        val current = logger
        if (current != null) {
            current.log("== 日志记录已关闭 ==")
            current.flush()
        }
        attached.set(false)
        logger = null
    }

    fun log(message: String) {
        logger?.log(message)
    }

    fun logBlock(block: String) {
        logger?.logBlock(block)
    }

    fun flush() {
        logger?.flush()
    }
}
