package com.example.data.backup

import android.content.Context
import android.os.Build
import com.example.data.local.CachedSmsEntity
import com.example.data.sms.AndroidSmsDataSource
import com.example.security.KeyStoreCryptoManager
import com.example.security.SensitiveMessageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class BackupSmsItem(
    val address: String,
    val displayName: String,
    val body: String,
    val timestamp: Long,
    val type: Int,
    val isRead: Boolean,
    val isFavorite: Boolean,
    val bodyHash: String
)

@Serializable
data class AfxSmsBackupPayload(
    val app: String = "AFX SMS Manager",
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val deviceLabel: String,
    val messageCount: Int,
    val messages: List<BackupSmsItem>
)

@Serializable
data class EncryptedBackupEnvelope(
    val app: String = "AFX SMS Manager",
    val formatVersion: Int = 1,
    val encrypted: Boolean = true,
    val exportedAt: Long,
    val messageCount: Int,
    val ciphertext: String
)

data class LocalBackupFileInfo(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isEncrypted: Boolean
)

sealed interface BackupExportResult {
    data class Success(
        val fileName: String,
        val filePath: String,
        val jsonContent: String,
        val exportedCount: Int
    ) : BackupExportResult

    data class Failure(val errorMessage: String) : BackupExportResult
}

sealed interface BackupRestoreResult {
    data class Success(
        val restoredCount: Int,
        val duplicateSkippedCount: Int,
        val messages: List<CachedSmsEntity>
    ) : BackupRestoreResult

    data object PassphraseRequired : BackupRestoreResult
    data object WrongPassphrase : BackupRestoreResult
    data class InvalidFile(val reason: String) : BackupRestoreResult
}

class LocalBackupManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun backupsDir(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listSavedBackups(): List<LocalBackupFileInfo> {
        return backupsDir()
            .listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".afxsms")) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                LocalBackupFileInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    isEncrypted = file.name.endsWith(".afxsms") || file.name.contains("_enc")
                )
            }
            ?: emptyList()
    }

    fun getLocalStorageBytes(): Long {
        val dbBytes = context.getDatabasePath("afx_sms_manager.db").takeIf { it.exists() }?.length() ?: 0L
        val backupBytes = backupsDir().listFiles()?.sumOf { it.length() } ?: 0L
        return dbBytes + backupBytes
    }

    suspend fun createBackupFile(
        messages: List<CachedSmsEntity>,
        encryptWithPassphrase: String?
    ): BackupExportResult = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) {
            return@withContext BackupExportResult.Failure("No messages selected to back up.")
        }

        return@withContext runCatching {
            val now = System.currentTimeMillis()
            val payload = AfxSmsBackupPayload(
                exportedAt = now,
                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                messageCount = messages.size,
                messages = messages.map { msg ->
                    BackupSmsItem(
                        address = msg.address,
                        displayName = msg.displayName,
                        body = msg.body,
                        timestamp = msg.timestamp,
                        type = msg.type,
                        isRead = msg.isRead,
                        isFavorite = msg.isFavorite,
                        bodyHash = msg.bodyHash
                    )
                }
            )
            val rawJson = json.encodeToString(payload)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))

            val (fileName, finalContent) = if (!encryptWithPassphrase.isNullOrBlank()) {
                val encrypted = KeyStoreCryptoManager.encryptWithPassphrase(rawJson, encryptWithPassphrase)
                val envelope = EncryptedBackupEnvelope(
                    exportedAt = now,
                    messageCount = messages.size,
                    ciphertext = encrypted
                )
                "afx_sms_backup_${stamp}_enc.afxsms" to json.encodeToString(envelope)
            } else {
                "afx_sms_backup_${stamp}.json" to rawJson
            }

            val outFile = File(backupsDir(), fileName)
            outFile.writeText(finalContent, Charsets.UTF_8)

            BackupExportResult.Success(
                fileName = fileName,
                filePath = outFile.absolutePath,
                jsonContent = finalContent,
                exportedCount = messages.size
            )
        }.getOrElse { err ->
            BackupExportResult.Failure(err.localizedMessage ?: "Failed to write local backup.")
        }
    }

    suspend fun parseAndPrepareRestore(
        rawContent: String,
        passphrase: String?,
        existingHashes: Set<String>
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        if (rawContent.isBlank()) {
            return@withContext BackupRestoreResult.InvalidFile("Backup file is empty.")
        }

        val trimmed = rawContent.trim()
        val payloadJson: String = if (trimmed.contains("\"encrypted\": true") || trimmed.contains("\"encrypted\":true")) {
            val envelope = runCatching {
                json.decodeFromString<EncryptedBackupEnvelope>(trimmed)
            }.getOrNull() ?: return@withContext BackupRestoreResult.InvalidFile("Invalid encrypted AFX backup format.")

            if (passphrase.isNullOrBlank()) {
                return@withContext BackupRestoreResult.PassphraseRequired
            }
            KeyStoreCryptoManager.decryptWithPassphrase(envelope.ciphertext, passphrase)
                ?: return@withContext BackupRestoreResult.WrongPassphrase
        } else {
            trimmed
        }

        val payload = runCatching {
            json.decodeFromString<AfxSmsBackupPayload>(payloadJson)
        }.getOrNull() ?: return@withContext BackupRestoreResult.InvalidFile("Unrecognized backup JSON structure.")

        if (payload.app != "AFX SMS Manager" || payload.formatVersion < 1) {
            return@withContext BackupRestoreResult.InvalidFile("Unsupported backup file header.")
        }

        var skipped = 0
        val seenInBatch = mutableSetOf<String>()
        val newEntities = mutableListOf<CachedSmsEntity>()

        for (item in payload.messages) {
            val hash = item.bodyHash.ifBlank {
                AndroidSmsDataSource.computeBodyHash(item.address, item.timestamp, item.type, item.body)
            }
            if (existingHashes.contains(hash) || !seenInBatch.add(hash)) {
                skipped++
                continue
            }
            newEntities.add(
                CachedSmsEntity(
                    uniqueKey = "rst_${hash.take(16)}",
                    systemSmsId = -1L,
                    threadId = -1L,
                    address = item.address,
                    displayName = item.displayName.ifBlank { item.address },
                    body = item.body,
                    timestamp = item.timestamp,
                    type = item.type,
                    isRead = item.isRead,
                    isFavorite = item.isFavorite,
                    isRestoredOnly = true,
                    isSensitive = SensitiveMessageFilter.isSensitive(item.displayName, item.body),
                    bodyHash = hash
                )
            )
        }

        BackupRestoreResult.Success(
            restoredCount = newEntities.size,
            duplicateSkippedCount = skipped,
            messages = newEntities
        )
    }

    suspend fun deleteBackupFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(filePath).canonicalFile
            val base = backupsDir().canonicalFile
            if (target.path.startsWith(base.path + File.separator) && target.exists()) {
                target.delete()
            } else false
        }.getOrDefault(false)
    }

    suspend fun readBackupFileText(filePath: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(filePath).canonicalFile
            val base = backupsDir().canonicalFile
            if (target.path.startsWith(base.path + File.separator) && target.exists()) {
                target.readText(Charsets.UTF_8)
            } else null
        }.getOrNull()
    }
}
