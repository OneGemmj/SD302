package com.seedream.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure-JVM serialization for the backup zip: builds a zip from a
 * [BackupManifest] plus image bytes, and parses a zip back into a
 * [BackupContent]. Uses only `java.util.zip` and `org.json`, so it runs in
 * plain JVM unit tests (org.json is provided by the test dependency there and
 * by the Android SDK in the app).
 */
object BackupCodec {
    fun buildZip(manifest: BackupManifest, imageFiles: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifestToJson(manifest).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            imageFiles.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(IMAGE_DIR + name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * Parses a backup zip. Returns null when the bytes are not a valid zip or
     * the manifest is missing/corrupt, so callers can report a clean error.
     */
    fun parseZip(bytes: ByteArray): BackupContent? {
        return runCatching {
            var manifest: BackupManifest? = null
            val images = LinkedHashMap<String, ByteArray>()

            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == MANIFEST_NAME -> {
                            val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                            manifest = manifestFromJson(json)
                        }
                        entry.name.startsWith(IMAGE_DIR) && !entry.isDirectory -> {
                            val name = entry.name.removePrefix(IMAGE_DIR)
                            if (name.isNotBlank()) images[name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val m = manifest ?: return null
            if (m.data.version > BACKUP_FORMAT_VERSION) return null
            BackupContent(manifest = m, imageFiles = images)
        }.getOrNull()
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
