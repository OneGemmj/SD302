package com.seedream.app.backup

import android.content.Context
import androidx.room.withTransaction
import com.seedream.app.logging.LogEventBus
import com.seedream.app.network.SearchProvider
import com.seedream.app.storage.AppDatabase
import com.seedream.app.storage.HistoryEntity
import com.seedream.app.storage.KeyStorage
import com.seedream.app.storage.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Android-side orchestration of one-click backup/restore.
 *
 * Backup: reads all history records, the image cache directory, all settings
 * and the API keys (as plaintext), then streams a zip via [BackupCodec].
 *
 * Restore: streams the zip through a staging directory, promotes the images
 * into this device's cache directory, replaces the history table inside a
 * single transaction, restores settings, and re-encrypts the API keys into the
 * Keystore of this device.
 *
 * Both directions stream the payload: images are copied through one buffer at
 * a time, so a multi-hundred-megabyte history costs the same heap as an empty
 * one. Everything here hops to [Dispatchers.IO] itself, so callers may invoke
 * it from the main thread.
 */
class BackupManager(
    context: Context,
    private val settingsStorage: SettingsStorage = SettingsStorage(context),
    private val keyStorage: KeyStorage = KeyStorage(context)
) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.get(appContext)
    private val dao = database.historyDao()
    private val cacheDir: File
        get() = File(appContext.filesDir, "history_images")

    /**
     * Streams a backup of the current app state into [out].
     *
     * Only cache files that a history record actually references are exported,
     * so orphans left behind by earlier crashes never bloat the archive.
     *
     * @return the number of image files written.
     */
    suspend fun createBackupTo(
        out: OutputStream,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val records = dao.allOnce()

        // fileName -> recordId for every cache file the history still points at.
        val referenced = LinkedHashMap<String, Long>()
        records.forEach { record ->
            val path = record.localPath ?: return@forEach
            val file = File(path)
            if (file.isFile) referenced[file.name] = record.id
        }
        LogEventBus.log("backup: started, records=${records.size}, images=${referenced.size}")

        val manifest = BackupManifest(
            data = BackupData(
                version = BACKUP_FORMAT_VERSION,
                records = records.map {
                    BackupRecord(
                        id = it.id,
                        source = it.source,
                        fileName = it.localPath?.let { path -> File(path).name },
                        prompt = it.prompt,
                        model = it.model,
                        timestamp = it.timestamp
                    )
                },
                settings = settingsStorage.all(),
                apiKeys = mapOf("api_key" to keyStorage.loadApiKey()),
                searchApiKeys = SearchProvider.entries
                    .filter { it.requiresApiKey }
                    .associate { it.id to keyStorage.loadSearchApiKey(it.id) }
                    .filterValues { it.isNotBlank() }
            ),
            images = referenced.map { (name, recordId) -> BackupImage(name = name, recordId = recordId) }
        )

        val written = BackupCodec.writeZip(
            out = out,
            manifest = manifest,
            imageNames = referenced.keys,
            onProgress = onProgress,
            openImage = { name -> File(cacheDir, name).takeIf { it.isFile }?.inputStream() }
        ).size
        LogEventBus.log("backup: finished, wrote $written files")
        written
    }

    /**
     * Restores state from a backup produced by [createBackupTo].
     *
     * [openInput] is called to obtain the archive; images are staged in a
     * temporary directory and promoted only once the whole archive has been
     * read and validated, so a truncated or foreign file cannot wipe the
     * current history. The history table is then replaced inside one
     * transaction. Returns a human-readable summary on success or an error
     * message on failure. Destructive — callers must confirm with the user
     * first.
     */
    suspend fun restoreFrom(openInput: () -> InputStream?): Result<String> = withContext(Dispatchers.IO) {
        LogEventBus.log("restore: started")
        runCatching {
            val staging = File(appContext.cacheDir, STAGING_DIR)
            staging.deleteRecursively()
            if (!staging.mkdirs() && !staging.isDirectory) {
                error("无法创建还原临时目录")
            }

            val manifest = try {
                val input = openInput() ?: error("无法读取所选文件")
                input.use { stream ->
                    BackupCodec.readZip(stream) { name, data ->
                        FileOutputStream(File(staging, name)).use { target -> data.copyTo(target) }
                    }
                } ?: error("备份文件无效：不是有效的压缩包或缺少清单文件")
            } catch (t: Throwable) {
                staging.deleteRecursively()
                throw t
            }

            val data = manifest.data

            // 1. Promote staged images into this device's cache directory.
            cacheDir.mkdirs()
            val promoted = HashMap<String, String>() // fileName -> new absolute path
            staging.listFiles()?.forEach { file ->
                val target = File(cacheDir, file.name)
                if (!file.renameTo(target)) {
                    file.inputStream().use { source ->
                        FileOutputStream(target).use { dest -> source.copyTo(dest) }
                    }
                    file.delete()
                }
                promoted[file.name] = target.absolutePath
            }
            staging.deleteRecursively()

            val restored = data.records.map { r ->
                HistoryEntity(
                    id = r.id,
                    source = r.source,
                    localPath = r.fileName?.let { promoted[it] },
                    prompt = r.prompt,
                    model = r.model,
                    timestamp = r.timestamp
                )
            }

            // 2. Replace history atomically: either every record lands or none
            //    does, so a failure mid-way cannot leave a half-restored list.
            database.withTransaction {
                dao.clear()
                restored.forEach { dao.insert(it) }
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

            // 5. Drop cache files no restored record points at any more.
            val keep = restored.mapNotNull { it.localPath }.toHashSet()
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.absolutePath !in keep) file.delete()
            }

            LogEventBus.log("restore: finished, records=${restored.size}, images=${promoted.size}")
            "还原成功：" + restored.size + " 条历史记录，" + promoted.size + " 张图片"
        }.onFailure {
            LogEventBus.log("restore: failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private companion object {
        const val STAGING_DIR = "restore_staging"
    }
}
