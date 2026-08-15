package com.seedream.app.backup

/**
 * Pure-JVM, Android-free data model for one-click backup/restore.
 *
 * A backup is a zip file containing:
 *  - `manifest.json`: a [BackupManifest] with history records, settings,
 *    API keys (plaintext), and an image index
 *  - `images/<name>`: the cached image files
 *
 * History record DTO mirrors `HistoryEntity` but stores the cache file as a
 * bare file name rather than an absolute path, because absolute paths are
 * device-specific and would break across devices / reinstalls.
 */
data class BackupRecord(
    val id: Long,
    val source: String,
    val fileName: String?,   // cache file name only (e.g. seedream_123_456.jpg)
    val prompt: String,
    val model: String,
    val timestamp: Long
)

data class BackupImage(
    val name: String,
    val recordId: Long
)

data class BackupData(
    val version: Int,
    val records: List<BackupRecord>,
    val settings: Map<String, String>,
    val apiKeys: Map<String, String>,          // plaintext, re-encrypted on restore
    val searchApiKeys: Map<String, String>     // providerId -> plaintext
)

data class BackupManifest(
    val data: BackupData,
    val images: List<BackupImage>
)

/** Result of unpacking a backup zip: manifest plus raw image bytes by name. */
data class BackupContent(
    val manifest: BackupManifest,
    val imageFiles: Map<String, ByteArray>
)

const val BACKUP_FORMAT_VERSION = 1
const val MANIFEST_NAME = "manifest.json"
const val IMAGE_DIR = "images/"
