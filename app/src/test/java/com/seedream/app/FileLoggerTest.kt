package com.seedream.app

import com.seedream.app.logging.FileLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class FileLoggerTest {
    private fun tempFile(): File = File.createTempFile("seedream-log-test", ".log")

    @Test
    fun logAppendsTimestampedLinesAndReadsBack() {
        val file = tempFile()
        var now = 1_700_000_000_000L
        val logger = FileLogger(file = { file }, now = { now })

        logger.log("first")
        now += 1000
        logger.log("second")
        logger.flush()

        val content = logger.readAll()
        assertTrue(content.contains("first"))
        assertTrue(content.contains("second"))
        // Each entry carries a yyyy-MM-dd HH:mm:ss.SSS timestamp (format only;
        // the absolute value depends on the JVM timezone).
        val line = content.lines().first { it.contains("first") }
        assertTrue(TS_REGEX.matches(line))
        assertEquals(2L, logger.entriesCount())
        assertTrue(logger.fileSize() > 0L)
        file.delete()
    }

    @Test
    fun logBlockAppendsRawTextWithoutTimestamp() {
        val file = tempFile()
        val logger = FileLogger(file = { file })
        logger.log("before")
        logger.logBlock("== CRASH ==")
        logger.logBlock("java.lang.RuntimeException: boom")

        val content = logger.readAll()
        assertTrue(content.contains("== CRASH =="))
        assertTrue(content.contains("java.lang.RuntimeException: boom"))
        file.delete()
    }

    @Test
    fun fileTrimsWhenOverLimitKeepingNewestTail() {
        val file = tempFile()
        // Small limit forces trimming after a few writes.
        val logger = FileLogger(file = { file }, maxBytes = 200)
        val sb = StringBuilder()
        repeat(50) {
            val line = "entry-$it-${"x".repeat(40)}"
            sb.append(line).append("\n")
            logger.log(line)
        }

        val content = logger.readAll()
        assertTrue(logger.fileSize() <= 200)
        // Newest entries survive the trim.
        assertTrue(content.contains("entry-49"))
        assertTrue(content.contains("entry-48"))
        // Some oldest entries are dropped.
        assertFalse(content.contains("entry-0"))
        file.delete()
    }

    @Test
    fun concurrentWritesAreThreadSafeAndAllReadBack() {
        val file = tempFile()
        val logger = FileLogger(file = { file })
        val threads = 8
        val perThread = 50
        val pool = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { t ->
            pool.execute {
                try {
                    repeat(perThread) { i ->
                        logger.log("thread-$t-$i")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        val content = logger.readAll()
        val lines = content.lines().filter { it.isNotBlank() }
        assertEquals(threads * perThread, lines.size)
        assertEquals(threads * perThread.toLong(), logger.entriesCount())
        file.delete()
    }

    @Test
    fun clearEmptiesFileAndCounters() {
        val file = tempFile()
        val logger = FileLogger(file = { file })
        logger.log("one")
        logger.log("two")

        logger.clear()

        assertEquals("", logger.readAll())
        assertEquals(0L, logger.entriesCount())
        assertEquals(0L, logger.fileSize())
        file.delete()
    }

    @Test
    fun missingFileParentIsCreated() {
        val base = File.createTempFile("seedream-log-dir", "").apply { delete() }
        val file = File(base, "sub/app.log")
        val logger = FileLogger(file = { file })
        logger.log("hello")
        assertTrue(file.exists())
        assertTrue(logger.readAll().contains("hello"))
        base.deleteRecursively()
    }

    @Test
    fun logsWithFixedTimeSource() {
        val file = tempFile()
        val now = AtomicLong(1_700_000_000_000L)
        val logger = FileLogger(file = { file }, now = { now.get() })
        logger.log("a")
        now.set(1_700_000_001_000L)
        logger.log("b")

        val content = logger.readAll()
        val lineA = content.lines().first { it.contains(" a") }
        val lineB = content.lines().first { it.contains(" b") }
        // Same date prefix; only the seconds field differs by one second.
        assertTrue(lineA.startsWith(lineB.substring(0, 18)))
        assertTrue(TS_REGEX.matches(lineA))
        assertTrue(TS_REGEX.matches(lineB))
        file.delete()
    }

    private companion object {
        val TS_REGEX = Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} .+""")
    }
}
