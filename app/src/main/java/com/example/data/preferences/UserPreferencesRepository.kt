package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.KeyStoreCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.afxDataStore: DataStore<Preferences> by preferencesDataStore(name = "afx_sms_prefs")

enum class ThemeMode(val label: String) {
    DARK_AMOLED("AMOLED Dark"),
    LIGHT("Light"),
    SYSTEM("System Default")
}

enum class AccentPreset(val label: String, val hexColor: Long) {
    CYAN_ELECTRIC("Electric Cyan", 0xFF22D3EE),
    INDIGO_VIOLET("Neon Indigo", 0xFF818CF8),
    EMERALD_MINT("Cyber Emerald", 0xFF34D399),
    AMBER_GOLD("Solar Amber", 0xFFFBBF24),
    ROSE_CRIMSON("Pulse Rose", 0xFFFB7185)
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK_AMOLED,
    val accentPreset: AccentPreset = AccentPreset.CYAN_ELECTRIC,
    val notificationsEnabled: Boolean = true,
    val notifyNewSms: Boolean = true,
    val notifyBackupStatus: Boolean = true,
    val notifyBackupReminder: Boolean = false,
    val notificationSoundEnabled: Boolean = true,
    val backupReminderDays: Int = 7,
    val privacyModeHidePreviews: Boolean = false,
    val appLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 30,
    val hasPinConfigured: Boolean = false,
    val resolveContactsEnabled: Boolean = false,
    val lastInboxSyncTimestamp: Long = 0L,
    val lastBackupTimestamp: Long = 0L,
    val lastBackupStatus: String = "No backup yet",
    val telegramConnected: Boolean = false,
    val telegramBotUsername: String = "",
    val telegramMaskedToken: String = "",
    val telegramChatIdPreview: String = "",
    val telegramExcludeSensitive: Boolean = true
)

@Serializable
data class ExportedAppSettings(
    val themeMode: String,
    val accentPreset: String,
    val notificationsEnabled: Boolean,
    val notifyNewSms: Boolean,
    val notifyBackupStatus: Boolean,
    val notifyBackupReminder: Boolean,
    val notificationSoundEnabled: Boolean,
    val backupReminderDays: Int,
    val privacyModeHidePreviews: Boolean,
    val appLockTimeoutSeconds: Int,
    val resolveContactsEnabled: Boolean,
    val telegramExcludeSensitive: Boolean
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_NEW_SMS = booleanPreferencesKey("notify_new_sms")
        val NOTIFY_BACKUP_STATUS = booleanPreferencesKey("notify_backup_status")
        val NOTIFY_BACKUP_REMINDER = booleanPreferencesKey("notify_backup_reminder")
        val NOTIFICATION_SOUND = booleanPreferencesKey("notification_sound")
        val BACKUP_REMINDER_DAYS = intPreferencesKey("backup_reminder_days")
        val PRIVACY_HIDE_PREVIEWS = booleanPreferencesKey("privacy_hide_previews")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val APP_LOCK_TIMEOUT_SEC = intPreferencesKey("app_lock_timeout_sec")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val RESOLVE_CONTACTS = booleanPreferencesKey("resolve_contacts")
        val LAST_INBOX_SYNC = longPreferencesKey("last_inbox_sync")
        val LAST_BACKUP_TS = longPreferencesKey("last_backup_ts")
        val LAST_BACKUP_STATUS = stringPreferencesKey("last_backup_status")
        val TELEGRAM_ENC_TOKEN = stringPreferencesKey("telegram_enc_token")
        val TELEGRAM_ENC_CHAT_ID = stringPreferencesKey("telegram_enc_chat_id")
        val TELEGRAM_MASKED_TOKEN = stringPreferencesKey("telegram_masked_token")
        val TELEGRAM_CHAT_PREVIEW = stringPreferencesKey("telegram_chat_preview")
        val TELEGRAM_CONNECTED = booleanPreferencesKey("telegram_connected")
        val TELEGRAM_BOT_USERNAME = stringPreferencesKey("telegram_bot_username")
        val TELEGRAM_EXCLUDE_SENSITIVE = booleanPreferencesKey("telegram_exclude_sensitive")
    }

    val settingsFlow: Flow<UserSettings> = context.afxDataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.find { it.name == name }
            } ?: ThemeMode.DARK_AMOLED,
            accentPreset = prefs[Keys.ACCENT_PRESET]?.let { name ->
                AccentPreset.entries.find { it.name == name }
            } ?: AccentPreset.CYAN_ELECTRIC,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            notifyNewSms = prefs[Keys.NOTIFY_NEW_SMS] ?: true,
            notifyBackupStatus = prefs[Keys.NOTIFY_BACKUP_STATUS] ?: true,
            notifyBackupReminder = prefs[Keys.NOTIFY_BACKUP_REMINDER] ?: false,
            notificationSoundEnabled = prefs[Keys.NOTIFICATION_SOUND] ?: true,
            backupReminderDays = prefs[Keys.BACKUP_REMINDER_DAYS] ?: 7,
            privacyModeHidePreviews = prefs[Keys.PRIVACY_HIDE_PREVIEWS] ?: false,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK_ENABLED] ?: false,
            appLockTimeoutSeconds = prefs[Keys.APP_LOCK_TIMEOUT_SEC] ?: 30,
            hasPinConfigured = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            resolveContactsEnabled = prefs[Keys.RESOLVE_CONTACTS] ?: false,
            lastInboxSyncTimestamp = prefs[Keys.LAST_INBOX_SYNC] ?: 0L,
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TS] ?: 0L,
            lastBackupStatus = prefs[Keys.LAST_BACKUP_STATUS] ?: "No backup yet",
            telegramConnected = prefs[Keys.TELEGRAM_CONNECTED] ?: false,
            telegramBotUsername = prefs[Keys.TELEGRAM_BOT_USERNAME] ?: "",
            telegramMaskedToken = prefs[Keys.TELEGRAM_MASKED_TOKEN] ?: "",
            telegramChatIdPreview = prefs[Keys.TELEGRAM_CHAT_PREVIEW] ?: "",
            telegramExcludeSensitive = prefs[Keys.TELEGRAM_EXCLUDE_SENSITIVE] ?: true
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.afxDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateAccentPreset(preset: AccentPreset) {
        context.afxDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateNotifyNewSms(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.NOTIFY_NEW_SMS] = enabled }
    }

    suspend fun updateNotifyBackupStatus(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.NOTIFY_BACKUP_STATUS] = enabled }
    }

    suspend fun updateNotifyBackupReminder(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.NOTIFY_BACKUP_REMINDER] = enabled }
    }

    suspend fun updateNotificationSound(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.NOTIFICATION_SOUND] = enabled }
    }

    suspend fun updateBackupReminderDays(days: Int) {
        context.afxDataStore.edit { it[Keys.BACKUP_REMINDER_DAYS] = days.coerceIn(1, 30) }
    }

    suspend fun updatePrivacyHidePreviews(hide: Boolean) {
        context.afxDataStore.edit { it[Keys.PRIVACY_HIDE_PREVIEWS] = hide }
    }

    suspend fun updateResolveContacts(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.RESOLVE_CONTACTS] = enabled }
    }

    suspend fun setPin(pin: String) {
        val salt = KeyStoreCryptoManager.generateSaltBase64()
        val hash = KeyStoreCryptoManager.hashPin(pin, salt)
        context.afxDataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
            it[Keys.APP_LOCK_ENABLED] = true
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.afxDataStore.data.first()
        val salt = prefs[Keys.PIN_SALT].orEmpty()
        val hash = prefs[Keys.PIN_HASH].orEmpty()
        return KeyStoreCryptoManager.verifyPin(pin, salt, hash)
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.afxDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = enabled
            if (!enabled) {
                it[Keys.BIOMETRIC_LOCK_ENABLED] = false
            }
        }
    }

    suspend fun clearPin() {
        context.afxDataStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
            it[Keys.APP_LOCK_ENABLED] = false
            it[Keys.BIOMETRIC_LOCK_ENABLED] = false
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.afxDataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockTimeout(seconds: Int) {
        context.afxDataStore.edit { it[Keys.APP_LOCK_TIMEOUT_SEC] = seconds }
    }

    suspend fun updateLastInboxSync(timestamp: Long) {
        context.afxDataStore.edit { it[Keys.LAST_INBOX_SYNC] = timestamp }
    }

    suspend fun updateBackupStatus(timestamp: Long, status: String) {
        context.afxDataStore.edit {
            it[Keys.LAST_BACKUP_TS] = timestamp
            it[Keys.LAST_BACKUP_STATUS] = status
        }
    }

    suspend fun saveTelegramCredentials(
        botToken: String,
        chatId: String,
        botUsername: String,
        connected: Boolean
    ) {
        val encryptedToken = KeyStoreCryptoManager.encryptWithKeystore(botToken)
        val encryptedChatId = KeyStoreCryptoManager.encryptWithKeystore(chatId)
        val maskedToken = KeyStoreCryptoManager.maskToken(botToken)
        val chatPreview = if (chatId.length > 4) "••••${chatId.takeLast(4)}" else chatId
        context.afxDataStore.edit {
            it[Keys.TELEGRAM_ENC_TOKEN] = encryptedToken
            it[Keys.TELEGRAM_ENC_CHAT_ID] = encryptedChatId
            it[Keys.TELEGRAM_MASKED_TOKEN] = maskedToken
            it[Keys.TELEGRAM_CHAT_PREVIEW] = chatPreview
            it[Keys.TELEGRAM_BOT_USERNAME] = botUsername
            it[Keys.TELEGRAM_CONNECTED] = connected
        }
    }

    suspend fun getDecryptedTelegramCredentials(): Pair<String, String>? {
        val prefs = context.afxDataStore.data.first()
        val encToken = prefs[Keys.TELEGRAM_ENC_TOKEN].orEmpty()
        val encChat = prefs[Keys.TELEGRAM_ENC_CHAT_ID].orEmpty()
        if (encToken.isBlank() || encChat.isBlank()) return null
        val token = KeyStoreCryptoManager.decryptWithKeystore(encToken) ?: return null
        val chat = KeyStoreCryptoManager.decryptWithKeystore(encChat) ?: return null
        return token to chat
    }

    suspend fun setTelegramExcludeSensitive(exclude: Boolean) {
        context.afxDataStore.edit { it[Keys.TELEGRAM_EXCLUDE_SENSITIVE] = exclude }
    }

    suspend fun disconnectTelegram(clearCredentials: Boolean) {
        context.afxDataStore.edit {
            it[Keys.TELEGRAM_CONNECTED] = false
            if (clearCredentials) {
                it.remove(Keys.TELEGRAM_ENC_TOKEN)
                it.remove(Keys.TELEGRAM_ENC_CHAT_ID)
                it.remove(Keys.TELEGRAM_MASKED_TOKEN)
                it.remove(Keys.TELEGRAM_CHAT_PREVIEW)
                it.remove(Keys.TELEGRAM_BOT_USERNAME)
            }
        }
    }

    suspend fun exportSettingsJson(): String {
        val current = settingsFlow.first()
        val payload = ExportedAppSettings(
            themeMode = current.themeMode.name,
            accentPreset = current.accentPreset.name,
            notificationsEnabled = current.notificationsEnabled,
            notifyNewSms = current.notifyNewSms,
            notifyBackupStatus = current.notifyBackupStatus,
            notifyBackupReminder = current.notifyBackupReminder,
            notificationSoundEnabled = current.notificationSoundEnabled,
            backupReminderDays = current.backupReminderDays,
            privacyModeHidePreviews = current.privacyModeHidePreviews,
            appLockTimeoutSeconds = current.appLockTimeoutSeconds,
            resolveContactsEnabled = current.resolveContactsEnabled,
            telegramExcludeSensitive = current.telegramExcludeSensitive
        )
        return Json { prettyPrint = true }.encodeToString(payload)
    }
}
