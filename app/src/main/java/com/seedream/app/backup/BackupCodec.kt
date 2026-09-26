package com.seedream.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure-JVM serialization for the backup zip: builds a zip from a
 * [BackupManifest] plus image bytes, and parses a zip back into a
 * [BackupContent]. Uses only `java.util.zip` and `org.json`, so it runs in
 * plain JVM unit tests (org.json is provided by the test dependency there and
 * by the Android SDK in the app).
 *
 * Everything is stream based. Nothing here ever holds more than one copy
 * buffer plus the manifest, so a backup of a few hundred megabytes costs the
 * same heap as a backup of a few kilobytes.
 */
object BackupCodec {
    private const val COPY_BUFFER_BYTES = 64 * 1024

    /**
     * In-memory convenience wrapper around [writeZip], kept for tests and tiny
     * payloads. Production code streams through [writeZip] instead so the whole
     * archive never has to exist as a byte array.
     */
    fun buildZip(manifest: BackupManifest, imageFiles: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        writeZip(out, manifest, imageFiles.keys, openImage = { imageFiles[it]?.let(::ByteArrayInputStream) })
        return out.toByteArray()
    }

    /**
     * Streams a backup zip into [out]: the manifest first, then every image
     * [openImage] can supply. Image contents are copied straight through a
     * fixed-size buffer, so peak memory is independent of the archive size.
     *
     * Closing the returned [ZipOutputStream] also closes [out], which is what
     * SAF output streams need in order to flush their data.
     *
     * @param onProgress called with (written, total) after each image lands.
     * @param openImage supplies one image at a time; a null result skips it,
     *   which keeps a missing cache file from aborting the whole backup.
     * @return the names actually written, in order.
     */
    fun writeZip(
        out: OutputStream,
        manifest: BackupManifest,
        imageNames: Collection<String>,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
        openImage: (String) -> InputStream?
    ): List<String> {
        val written = ArrayList<String>(imageNames.size)
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        val total = imageNames.size

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifestToJson(manifest).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            imageNames.forEach { rawName ->
                val name = sanitizeImageName(rawName) ?: return@forEach
                val source = openImage(name) ?: return@forEach
                zip.putNextEntry(ZipEntry(IMAGE_DIR + name))
                source.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        zip.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
                written.add(name)
                onProgress(written.size, total)
            }
        }
        return written
    }

    /**
     * Streams a backup zip out of [source]. The manifest is buffered because it
     * is small; every image entry is handed to [onImage] as a live stream that
     * is only valid for the duration of that call, so the caller can copy it
     * straight to disk.
     *
     * A backup always stores the manifest first. An image encountered before
     * the manifest means the archive was not produced by this app, so parsing
     * stops and returns null rather than letting a foreign zip write files.
     *
     * @return the manifest, or null when [source] is not a valid backup.
     */
    fun readZip(
        source: InputStream,
        onImage: (name: String, data: InputStream) -> Unit = { _, _ -> }
    ): BackupManifest? {
        return runCatching {
            var manifest: BackupManifest? = null

            ZipInputStream(source).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == MANIFEST_NAME -> {
                            if (manifest == null) {
                                manifest = manifestFromJson(
                                    JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                                )
                            }
                        }

                        entry.name.startsWith(IMAGE_DIR) && !entry.isDirectory -> {
                            // Refuse to hand out image data before the archive has
                            // proven it is one of ours.
                            if (manifest == null) return null
                            sanitizeImageName(entry.name.removePrefix(IMAGE_DIR))
                                ?.let { name -> onImage(name, zip) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val parsed = manifest ?: return null
            if (parsed.data.version > BACKUP_FORMAT_VERSION) return null
            parsed
        }.getOrNull()
    }

    /**
     * Parses a backup zip held in memory. Returns null when the bytes are not a
     * valid zip or the manifest is missing/corrupt, so callers can report a
     * clean error.
     */
    fun parseZip(bytes: ByteArray): BackupContent? {
        val images = LinkedHashMap<String, ByteArray>()
        val manifest = readZip(ByteArrayInputStream(bytes)) { name, data ->
            images[name] = data.readBytes()
        } ?: return null
        return BackupContent(manifest = manifest, imageFiles = images)
    }

    /**
     * Guards against zip entries that try to escape the destination directory
     * (`../../x`, absolute paths) or that carry no usable name. Returns the
     * bare file name, or null when the entry must be skipped.
     */
    fun sanitizeImageName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "." || trimmed == "..") return null
        if (trimmed.contains('/') || trimmed.contains('\\')) return null
        if (trimmed.contains('\u0000')) return null
        return trimmed
    }

    private fun manifestToJson(manifest: BackupManifest): JSONObject {
        val data = manifest.data
        val records = JSONArray()
        data.records.forEach { r ->
            records.put(
                JSONObject()
                    .put("id", r.id)
                    .put("source", r.source)
                    .put("fileName", r.fileName)
                    .put("prompt", r.prompt)
                    .put("model", r.model)
                    .put("timestamp", r.timestamp)
            )
        }
        val images = JSONArray()
        manifest.images.forEach { img ->
            images.put(JSONObject().put("name", img.name).put("recordId", img.recordId))
        }
        return JSONObject()
            .put("formatVersion", BACKUP_FORMAT_VERSION)
            .put(
                "data",
                JSONObject()
                    .put("version", data.version)
                    .put("records", records)
                    .put("settings", JSONObject(data.settings))
                    .put("apiKeys", JSONObject(data.apiKeys))
                    .put("searchApiKeys", JSONObject(data.searchApiKeys))
            )
            .put("images", images)
    }

    private fun manifestFromJson(json: JSONObject): BackupManifest {
        val dataJson = json.getJSONObject("data")
        val records = mutableListOf<BackupRecord>()
        val recordsArr = dataJson.getJSONArray("records")
        for (i in 0 until recordsArr.length()) {
            val r = recordsArr.getJSONObject(i)
            records.add(
                BackupRecord(
                    id = r.optLong("id", 0L),
                    source = r.optString("source", ""),
                    fileName = r.optString("fileName", "").ifBlank { null },
                    prompt = r.optString("prompt", ""),
                    model = r.optString("model", ""),
                    timestamp = r.optLong("timestamp", 0L)
                )
            )
        }
        val images = mutableListOf<BackupImage>()
        val imagesArr = json.optJSONArray("images") ?: JSONArray()
        for (i in 0 until imagesArr.length()) {
            val img = imagesArr.getJSONObject(i)
            images.add(
                BackupImage(
                    name = img.optString("name", ""),
                    recordId = img.optLong("recordId", 0L)
                )
            )
        }
        return BackupManifest(
            data = BackupData(
                version = dataJson.optInt("version", BACKUP_FORMAT_VERSION),
                records = records,
                settings = jsonToMap(dataJson.optJSONObject("settings") ?: JSONObject()),
                apiKeys = jsonToMap(dataJson.optJSONObject("apiKeys") ?: JSONObject()),
                searchApiKeys = jsonToMap(dataJson.optJSONObject("searchApiKeys") ?: JSONObject())
            ),
            images = images
        )
    }

    private fun jsonToMap(obj: JSONObject): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        obj.keys().forEach { key ->
            map[key] = obj.optString(key, "")
        }
        return map
    }
}
