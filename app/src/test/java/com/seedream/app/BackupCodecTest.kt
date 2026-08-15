package com.seedream.app

import com.seedream.app.backup.BackupCodec
import com.seedream.app.backup.BackupData
import com.seedream.app.backup.BackupImage
import com.seedream.app.backup.BackupManifest
import com.seedream.app.backup.BackupRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun sampleManifest(): BackupManifest {
        return BackupManifest(
            data = BackupData(
                version = 1,
                records = listOf(
                    BackupRecord(
                        id = 1L,
                        source = "https://example.com/a.png",
                        fileName = "seedream_100_123.jpg",
                        prompt = "a red fox",
                        model = "doubao-seedream-5-0-260128",
                        timestamp = 1_700_000_000_000L
                    ),
                    BackupRecord(
                        id = 2L,
                        source = "",
                        fileName = null,
                        prompt = "cat with no cache",
                        model = "doubao-seedream-4-5-251128",
                        timestamp = 1_700_000_001_000L
                    )
                ),
                settings = mapOf(
                    "endpoint" to "https://api.302.ai/foo",
                    "model" to "doubao-seedream-5-0-pro-260628",
                    "stream" to "true"
                ),
                apiKeys = mapOf("api_key" to "sk-123-secret"),
                searchApiKeys = mapOf("tavily" to "tv-456")
            ),
            images = listOf(
                BackupImage(name = "seedream_100_123.jpg", recordId = 1L)
            )
        )
    }

    @Test
    fun roundTripPreservesAllFields() {
        val manifest = sampleManifest()
        val images = mapOf(
            "seedream_100_123.jpg" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        )

        val zip = BackupCodec.buildZip(manifest, images)
        assertTrue(zip.isNotEmpty())

        val content = BackupCodec.parseZip(zip)
        assertNotNull(content)

        val parsed = content!!.manifest
        assertEquals(1, parsed.data.version)
        assertEquals(2, parsed.data.records.size)

        val r1 = parsed.data.records[0]
        assertEquals(1L, r1.id)
        assertEquals("https://example.com/a.png", r1.source)
        assertEquals("seedream_100_123.jpg", r1.fileName)
        assertEquals("a red fox", r1.prompt)
        assertEquals("doubao-seedream-5-0-260128", r1.model)
        assertEquals(1_700_000_000_000L, r1.timestamp)

        val r2 = parsed.data.records[1]
        assertEquals(2L, r2.id)
        assertNull(r2.fileName)

        assertEquals("https://api.302.ai/foo", parsed.data.settings["endpoint"])
        assertEquals("sk-123-secret", parsed.data.apiKeys["api_key"])
        assertEquals("tv-456", parsed.data.searchApiKeys["tavily"])

        assertEquals(1, parsed.images.size)
        assertEquals("seedream_100_123.jpg", parsed.images[0].name)
        assertEquals(1L, parsed.images[0].recordId)

        assertTrue(content.imageFiles.containsKey("seedream_100_123.jpg"))
        assertEquals(4, content.imageFiles["seedream_100_123.jpg"]!!.size)
    }

    @Test
    fun emptyStateRoundTrips() {
        val manifest = BackupManifest(
            data = BackupData(
                version = 1,
                records = emptyList(),
                settings = emptyMap(),
                apiKeys = emptyMap(),
                searchApiKeys = emptyMap()
            ),
            images = emptyList()
        )
        val zip = BackupCodec.buildZip(manifest, emptyMap())
        val content = BackupCodec.parseZip(zip)
        assertNotNull(content)
        assertEquals(0, content!!.manifest.data.records.size)
        assertTrue(content.imageFiles.isEmpty())
    }

    @Test
    fun specialCharactersInPromptAndSourceSurvive() {
        val manifest = BackupManifest(
            data = BackupData(
                version = 1,
                records = listOf(
                    BackupRecord(
                        id = 7L,
                        source = "https://example.com/a b.png?x=1&y=2",
                        fileName = "seedream_1_2.jpg",
                        prompt = "line1\nline2\t中文 表情🙂 \"quotes\" \\backslash",
                        model = "m",
                        timestamp = 123L
                    )
                ),
                settings = emptyMap(),
                apiKeys = emptyMap(),
                searchApiKeys = emptyMap()
            ),
            images = listOf(BackupImage(name = "seedream_1_2.jpg", recordId = 7L))
        )
        val zip = BackupCodec.buildZip(manifest, mapOf("seedream_1_2.jpg" to byteArrayOf(1, 2, 3)))
        val parsed = BackupCodec.parseZip(zip)!!.manifest

        assertEquals("https://example.com/a b.png?x=1&y=2", parsed.data.records[0].source)
        assertEquals("line1\nline2\t中文 表情🙂 \"quotes\" \\backslash", parsed.data.records[0].prompt)
    }

    @Test
    fun nonZipBytesReturnNull() {
        val garbage = "this is not a zip file at all".toByteArray()
        assertNull(BackupCodec.parseZip(garbage))
    }

    @Test
    fun newerFormatVersionIsRejected() {
        // A zip whose manifest declares a newer format version must be rejected
        // so older app versions never mis-restore forward data.
        val zip = BackupCodec.buildZip(
            BackupManifest(
                data = BackupData(
                    version = 99,
                    records = emptyList(),
                    settings = emptyMap(),
                    apiKeys = emptyMap(),
                    searchApiKeys = emptyMap()
                ),
                images = emptyList()
            ),
            emptyMap()
        )
        assertNull(BackupCodec.parseZip(zip))
    }

    @Test
    fun multipleImagesRoundTrip() {
        val manifest = BackupManifest(
            data = BackupData(
                version = 1,
                records = listOf(
                    BackupRecord(1L, "s1", "a.jpg", "p1", "m", 1L),
                    BackupRecord(2L, "s2", "b.jpg", "p2", "m", 2L)
                ),
                settings = emptyMap(),
                apiKeys = emptyMap(),
                searchApiKeys = emptyMap()
            ),
            images = listOf(
                BackupImage("a.jpg", 1L),
                BackupImage("b.jpg", 2L)
            )
        )
        val images = mapOf(
            "a.jpg" to ByteArray(1024) { 0x01 },
            "b.jpg" to ByteArray(2048) { 0x02 }
        )
        val parsed = BackupCodec.parseZip(BackupCodec.buildZip(manifest, images))!!
        assertEquals(1024, parsed.imageFiles["a.jpg"]!!.size)
        assertEquals(2048, parsed.imageFiles["b.jpg"]!!.size)
        assertEquals(2, parsed.manifest.images.size)
    }
}
