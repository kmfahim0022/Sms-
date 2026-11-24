package com.example.data.repository

import android.content.Context
import com.example.data.backup.BackupExportResult
import com.example.data.backup.BackupRestoreResult
import com.example.data.backup.LocalBackupFileInfo
import com.example.data.backup.LocalBackupManager
import com.example.data.local.AppDatabase
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.CachedSmsEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.data.sms.AndroidSmsDataSource
import com.example.data.sms.SmsReadResult
import com.example.data.telegram.TelegramBackupManager
import com.example.data.telegram.TelegramResult
import com.example.notifications.AfxNotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Ready(val count: Int) : SyncState
    data object PermissionRequired : SyncState
    data class Error(val message: String) : SyncState
}

class SmsRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val smsDao = db.smsDao()
    private val historyDao = db.backupHistoryDao()
    val preferences = UserPreferencesRepository(context)
    val smsDataSource = AndroidSmsDataSource(context)
    val localBackupManager = LocalBackupManager(context)
    val telegramBackupManager = TelegramBackupManager(context)

    val allMessagesFlow: Flow<List<CachedSmsEntity>> = smsDao.observeAllMessages()
    val favoriteMessagesFlow: Flow<List<CachedSmsEntity>> = smsDao.observeFavoriteMessages()
    val totalCountFlow: Flow<Int> = smsDao.observeTotalCount()
    val inboxCountFlow: Flow<Int> = smsDao.observeInboxCount()
    val unreadCountFlow: Flow<Int> = smsDao.observeUnreadCount()
    val backupHistoryFlow: Flow<List<BackupHistoryEntity>> = historyDao.observeAllHistory()
    val telegramHistoryFlow: Flow<List<BackupHistoryEntity>> = historyDao.observeTelegramHistory()
    val settingsFlow: Flow<UserSettings> = preferences.settingsFlow

    fun observeMessage(uniqueKey: String): Flow<CachedSmsEntity?> =
        smsDao.observeMessageByKey(uniqueKey)

    suspend fun syncInboxFromDevice(): SyncState {
        if (!smsDataSource.hasReadSmsPermission()) {
            return SyncState.PermissionRequired
        }
        val settings = settingsFlow.first()
        val existingFavorites = smsDao.getFavoriteKeys().toSet()

        return when (val result = smsDataSource.readDeviceSms(existingFavorites, settings.resolveContactsEnabled)) {
            is SmsReadResult.PermissionDenied -> SyncState.PermissionRequired
            is SmsReadResult.Error -> SyncState.Error(result.message)
            is SmsReadResult.Success -> {
                smsDao.clearNonRestoredCache()
                if (result.messages.isNotEmpty()) {
                    smsDao.upsertAll(result.messages)
                }
                preferences.updateLastInboxSync(System.currentTimeMillis())
                SyncState.Ready(result.messages.size)
            }
        }
    }

    suspend fun toggleFavorite(uniqueKey: String) {
        val current = smsDao.getMessageByKey(uniqueKey) ?: return
        smsDao.setFavorite(uniqueKey, !current.isFavorite)
    }

    suspend fun markReadLocally(uniqueKey: String) {
        smsDao.markAsReadLocal(uniqueKey)
    }

    /**
     * Deletes a message from the system SMS provider if this app is the active Default SMS app,
     * or deletes restored-only archived messages from the local Room vault.
     * Returns true if deleted, or false with a descriptive explanation when OS restricts system deletion.
     */
    suspend fun deleteMessage(message: CachedSmsEntity): Pair<Boolean, String> {
        return if (message.isRestoredOnly) {
            smsDao.deleteByKey(message.uniqueKey)
            true to "Removed restored message from local archive."
        } else if (smsDataSource.isDefaultSmsApp()) {
            val removedFromOs = smsDataSource.deleteFromSystemProviderIfPermitted(message.systemSmsId)
            if (removedFromOs) {
                smsDao.deleteByKey(message.uniqueKey)
                true to "Message deleted from device SMS database."
            } else {
                false to "Unable to delete message from device SMS database."
            }
        } else {
            false to "Android OS only allows the active Default SMS app to delete system SMS messages."
        }
    }

    suspend fun createLocalBackup(
        messages: List<CachedSmsEntity>,
        encryptPassphrase: String?
    ): BackupExportResult {
        val settings = settingsFlow.first()
        val result = localBackupManager.createBackupFile(messages, encryptPassphrase)
        val now = System.currentTimeMillis()
        when (result) {
            is BackupExportResult.Success -> {
                val dest = if (!encryptPassphrase.isNullOrBlank()) "LOCAL_ENCRYPTED" else "LOCAL_JSON"
                historyDao.insert(
                    BackupHistoryEntity(
                        timestamp = now,
                        destination = dest,
                        messageCount = result.exportedCount,
                        excludedSensitiveCount = 0,
                        status = "SUCCESS",
                        fileName = result.fileName
                    )
                )
                preferences.updateBackupStatus(now, "Local backup completed (${result.exportedCount} msgs)")
                if (settings.notificationsEnabled && settings.notifyBackupStatus) {
                    AfxNotificationHelper.showBackupResultNotification(
                        context = context,
                        success = true,
                        title = "Local Backup Completed",
                        message = "Saved ${result.exportedCount} messages to ${result.fileName}",
                        soundEnabled = settings.notificationSoundEnabled
                    )
                }
            }
            is BackupExportResult.Failure -> {
                historyDao.insert(
                    BackupHistoryEntity(
                        timestamp = now,
                        destination = if (!encryptPassphrase.isNullOrBlank()) "LOCAL_ENCRYPTED" else "LOCAL_JSON",
                        messageCount = 0,
                        excludedSensitiveCount = 0,
                        status = "FAILED",
                        errorMessage = result.errorMessage
                    )
                )
                preferences.updateBackupStatus(now, "Last backup failed")
                if (settings.notificationsEnabled && settings.notifyBackupStatus) {
                    AfxNotificationHelper.showBackupResultNotification(
                        context = context,
                        success = false,
                        title = "Local Backup Failed",
                        message = result.errorMessage,
                        soundEnabled = settings.notificationSoundEnabled
                    )
                }
            }
        }
        return result
    }

    suspend fun restoreBackupFromText(rawContent: String, passphrase: String?): BackupRestoreResult {
        val existingHashes = smsDao.getAllBodyHashes().toSet()
        val result = localBackupManager.parseAndPrepareRestore(rawContent, passphrase, existingHashes)
        if (result is BackupRestoreResult.Success && result.messages.isNotEmpty()) {
            smsDao.insertIfAbsent(result.messages)
        }
        return result
    }

    fun listLocalBackups(): List<LocalBackupFileInfo> = localBackupManager.listSavedBackups()

    fun getStorageBytes(): Long = localBackupManager.getLocalStorageBytes()

    suspend fun clearLocalCache() {
        smsDao.clearNonRestoredCache()
    }

    suspend fun clearBackupHistory() {
        historyDao.clearHistory()
    }

    suspend fun testAndSaveTelegramConnection(token: String, chatId: String): TelegramResult<String> {
        val result = telegramBackupManager.testConnection(token, chatId)
        if (result is TelegramResult.Success) {
            preferences.saveTelegramCredentials(
                botToken = token.trim(),
                chatId = chatId.trim(),
                botUsername = result.data,
                connected = true
            )
        }
        return result
    }

    /**
     * Sends user-selected, explicitly confirmed messages to the user's personal Telegram bot.
     * Sensitive OTP/verification/banking messages are filtered out automatically when protection is enabled.
     */
    suspend fun sendConfirmedTelegramBackup(
        selectedMessages: List<CachedSmsEntity>,
        asFile: Boolean,
        encryptFilePassphrase: String? = null
    ): TelegramResult<String> {
        val settings = settingsFlow.first()
        val creds = preferences.getDecryptedTelegramCredentials()
            ?: return TelegramResult.Error("Telegram bot is not configured. Save and test your bot credentials first.")

        val excludedCount = if (settings.telegramExcludeSensitive) {
            selectedMessages.count { it.isSensitive }
        } else 0

        val eligibleMessages = if (settings.telegramExcludeSensitive) {
            selectedMessages.filterNot { it.isSensitive }
        } else {
            selectedMessages
        }

        if (eligibleMessages.isEmpty()) {
            return TelegramResult.Error(
                "All selected messages were excluded by Sensitive/OTP Protection ($excludedCount filtered)."
            )
        }

        val now = System.currentTimeMillis()
        val (token, chatId) = creds

        if (asFile) {
            val exportRes = localBackupManager.createBackupFile(eligibleMessages, encryptFilePassphrase)
            if (exportRes is BackupExportResult.Failure) {
                return TelegramResult.Error(exportRes.errorMessage)
            }
            val successExport = exportRes as BackupExportResult.Success
            val sendRes = telegramBackupManager.sendBackupDocument(
                token = token,
                chatId = chatId,
                fileName = successExport.fileName,
                fileContent = successExport.jsonContent,
                messageCount = eligibleMessages.size
            )
            recordTelegramResult(
                now = now,
                destination = "TELEGRAM_FILE",
                sentCount = eligibleMessages.size,
                excludedCount = excludedCount,
                fileName = successExport.fileName,
                result = sendRes,
                settings = settings
            )
            return sendRes
        } else {
            val sendRes = telegramBackupManager.sendSelectedMessagesAsText(
                token = token,
                chatId = chatId,
                messages = eligibleMessages
            )
            val mapped: TelegramResult<String> = when (sendRes) {
                is TelegramResult.Success -> TelegramResult.Success("Backed up ${sendRes.data} messages to Telegram")
                is TelegramResult.Error -> sendRes
            }
            recordTelegramResult(
                now = now,
                destination = "TELEGRAM_TEXT",
                sentCount = eligibleMessages.size,
                excludedCount = excludedCount,
                fileName = null,
                result = mapped,
                settings = settings
            )
            return mapped
        }
    }

    private suspend fun recordTelegramResult(
        now: Long,
        destination: String,
        sentCount: Int,
        excludedCount: Int,
        fileName: String?,
        result: TelegramResult<String>,
        settings: UserSettings
    ) {
        when (result) {
            is TelegramResult.Success -> {
                historyDao.insert(
                    BackupHistoryEntity(
                        timestamp = now,
                        destination = destination,
                        messageCount = sentCount,
                        excludedSensitiveCount = excludedCount,
                        status = "SUCCESS",
                        fileName = fileName
                    )
                )
                preferences.updateBackupStatus(now, "Telegram backup completed ($sentCount msgs)")
                if (settings.notificationsEnabled && settings.notifyBackupStatus) {
                    AfxNotificationHelper.showBackupResultNotification(
                        context = context,
                        success = true,
                        title = "Telegram Backup Completed",
                        message = "Sent $sentCount messages ($excludedCount sensitive excluded).",
                        soundEnabled = settings.notificationSoundEnabled
                    )
                }
            }
            is TelegramResult.Error -> {
                historyDao.insert(
                    BackupHistoryEntity(
                        timestamp = now,
                        destination = destination,
                        messageCount = 0,
                        excludedSensitiveCount = excludedCount,
                        status = "FAILED",
                        fileName = fileName,
                        errorMessage = result.message
                    )
                )
                preferences.updateBackupStatus(now, "Telegram backup failed")
                if (settings.notificationsEnabled && settings.notifyBackupStatus) {
                    AfxNotificationHelper.showBackupResultNotification(
                        context = context,
                        success = false,
                        title = "Telegram Backup Failed",
                        message = result.message,
                        soundEnabled = settings.notificationSoundEnabled
                    )
                }
            }
        }
    }
}
