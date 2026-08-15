package com.seedream.app.logging

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure-JVM, Android-free file logger used to capture app runtime logs plus the
 * last moments before a crash. All file access is synchronized so it is safe to
 * call from the service (IO thread), the main thread and the crash handler
 * (uncaught-exception thread) concurrently.
 *
 * The file supplier is injected so tests can point at a temp file; production
 * passes `File(context.filesDir, "logs/app.log")`.
 */
class FileLogger(
    private val file: () -> File?,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {
    private val entries = AtomicLong(0)
    private val bytesWritten = AtomicLong(0)

    /** Appends one timestamped line to the log file. */
    fun log(message: String) {
        val f = file() ?: return
        val line = "${timestamp(now())} $message${System.lineSeparator()}"
        write(f, line)
        entries.incrementAndGet()
    }

    /** Appends a block of text as-is (used for crash stack traces). */
    fun logBlock(block: String) {
        if (block.isBlank()) return
        val f = file() ?: return
        write(f, block.trimEnd() + System.lineSeparator())
    }

    /** Forced flush of OS buffers so a crash does not lose the tail. */
    fun flush() {
        val f = file() ?: return
        synchronized(lock) {
            RandomAccessFile(f, "rw").use { raf ->
                raf.getFD().sync()
            }
        }
    }

    fun readAll(): String {
        val f = file() ?: return ""
        return synchronized(lock) {
            if (!f.exists()) "" else f.readText()
        }
    }

    fun clear() {
        val f = file() ?: return
        synchronized(lock) {
            if (f.exists()) {
                f.writeText("")
            }
        }
        entries.set(0)
        bytesWritten.set(0)
    }

    fun entriesCount(): Long = entries.get()

    fun fileSize(): Long {
        val f = file() ?: return 0L
        return synchronized(lock) { if (f.exists()) f.length() else 0L }
    }

    private fun write(f: File, data: String) {
        synchronized(lock) {
            f.parentFile?.mkdirs()
            FileOutputStream(f, true).use { out ->
                out.write(data.toByteArray(StandardCharsets.UTF_8))
            }
            bytesWritten.addAndGet(data.toByteArray(StandardCharsets.UTF_8).size.toLong())
            trimIfNeeded(f)
        }
    }

    /**
     * Keeps the file bounded. When it exceeds [maxBytes], trims from the head
     * down to half the limit so the newest entries (and any crash) are retained.
     */
    private fun trimIfNeeded(f: File) {
        if (bytesWritten.get() <= maxBytes) return
        val bytes = f.readBytes()
        if (bytes.size <= maxBytes) return
        val keepStart = (bytes.size - (maxBytes / 2).toInt()).coerceAtLeast(0)
        val kept = bytes.copyOfRange(keepStart, bytes.size)
        FileOutputStream(f, false).use { out ->
            out.write(kept)
        }
        bytesWritten.set(kept.size.toLong())
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))

    private companion object {
        const val DEFAULT_MAX_BYTES = 2L * 1024 * 1024 // 2 MiB
        val lock = Any()
    }
}
