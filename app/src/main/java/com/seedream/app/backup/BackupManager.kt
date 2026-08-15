package com.seedream.app.backup

import android.content.Context
import android.net.Uri
import com.seedream.app.network.SearchProvider
import com.seedream.app.storage.AppDatabase
import com.seedream.app.storage.HistoryEntity
import com.seedream.app.storage.KeyStorage
import com.seedream.app.storage.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Android-side orchestration of one-click backup/restore.
 *
 * Backup: reads all history records, the image cache directory, all settings
 * and the API keys (as plaintext), then produces a zip via [BackupCodec].
 *
 * Restore: parses the zip, replaces the history table, rewrites image cache
 * files (map backed-up file names to this device's cache directory), restores
 * settings, and re-encrypts the API keys into the Keystore of this device.
 */
class BackupManager(
    context: Context,
    private val settingsStorage: SettingsStorage = SettingsStorage(context),
    private val keyStorage: KeyStorage = KeyStorage(context)
) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.get(appContext).historyDao()
    private val cacheDir: File
        get() = File(appContext.filesDir, "history_images")

    /** Builds a backup zip from the current app state. */
    suspend fun createBackup(): ByteArray = withContext(Dispatchers.IO) {
        val records = dao.allOnce()
        val images = LinkedHashMap<String, ByteArray>()
        val imageIndex = mutableListOf<BackupImage>()

        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                runCatching { file.readBytes() }.getOrNull()?.let { bytes ->
                    images[file.name] = bytes
                }
            }
        }
        records.forEach { r ->
            r.localPath?.let { abs ->
                File(abs).takeIf { it.exists() }?.let { f ->
                    imageIndex.add(BackupImage(name = f.name, recordId = r.id))
                }
            }
        }

        val backupRecords = records.map {
            BackupRecord(
                id = it.id,
                source = it.source,
                fileName = it.localPath?.let { p -> File(p).name },
                prompt = it.prompt,
                model = it.model,
                timestamp = it.timestamp
            )
        }

        val manifest = BackupManifest(
            data = BackupData(
                version = BACKUP_FORMAT_VERSION,
                records = backupRecords,
                settings = settingsStorage.all(),
                apiKeys = mapOf("api_key" to keyStorage.loadApiKey()),
                searchApiKeys = SearchProvider.entries
                    .filter { it.requiresApiKey }
                    .associate { it.id to keyStorage.loadSearchApiKey(it.id) }
                    .filterValues { it.isNotBlank() }
            ),
            images = imageIndex
        )
        BackupCodec.buildZip(manifest, images)
    }

    /**
     * Restores state from a backup zip. Returns a human-readable summary on
     * success or an error message on failure. The DB is cleared and replaced,
     * so this is destructive — callers must confirm with the user first.
     */
    suspend fun restore(bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val content = BackupCodec.parseZip(bytes)
                ?: error("备份文件无效：不是有效的压缩包或缺少清单文件")
            val manifest = content.manifest
            val data = manifest.data

            // 1. Rewrite image cache files under this device's cache dir.
            val writtenFiles = HashMap<String, String>() // fileName -> new absolute path
            cacheDir.mkdirs()
            content.imageFiles.forEach { (name, bytes) ->
                val target = File(cacheDir, name)
                FileOutputStream(target).use { it.write(bytes) }
                writtenFiles[name] = target.absolutePath
            }

            // 2. Replace history table, rewriting localPath to this device.
            dao.clear()
            data.records.forEach { r ->
                val newLocalPath = r.fileName?.let { writtenFiles[it] }
                dao.insert(
                    HistoryEntity(
                        id = r.id,
                        source = r.source,
                        localPath = newLocalPath,
                        prompt = r.prompt,
                        model = r.model,
                        timestamp = r.timestamp
                    )
                )
            }

            // 3. Restore settings.
            data.settings.forEach { (key, value) ->
                settingsStorage.saveSetting(key, value)
            }

            // 4. Re-encrypt API keys into this device's Keystore.
            data.apiKeys["api_key"]?.let { keyStorage.saveApiKey(it) }
            data.searchApiKeys.forEach { (providerId, value) ->
                keyStorage.saveSearchApiKey(providerId, value)
            }

            "还原成功：${data.records.size} 条历史记录，${content.imageFiles.size} 张图片"
        }
    }
}
