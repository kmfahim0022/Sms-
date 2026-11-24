package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AfxApplication
import com.example.data.backup.BackupExportResult
import com.example.data.backup.BackupRestoreResult
import com.example.data.backup.LocalBackupFileInfo
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.CachedSmsEntity
import com.example.data.preferences.AccentPreset
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserSettings
import com.example.data.repository.SyncState
import com.example.data.telegram.TelegramResult
import com.example.notifications.AfxNotificationHelper
import com.example.ui.util.Formatters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SmsFilterOption(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    READ("Read"),
    FAVORITES("Favorites"),
    TODAY("Today"),
    THIS_WEEK("This Week")
}

enum class SmsBoxSection(val label: String) {
    ALL("All"),
    INBOX("Inbox"),
    SENT("Sent")
}

data class PermissionSnapshot(
    val readSmsGranted: Boolean = false,
    val receiveSmsGranted: Boolean = false,
    val readContactsGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val isDefaultSmsApp: Boolean = false
)

data class PendingTelegramPreview(
    val targetMessages: List<CachedSmsEntity>,
    val eligibleMessages: List<CachedSmsEntity>,
    val excludedSensitiveCount: Int,
    val sendAsFile: Boolean,
    val encryptFilePassphrase: String?
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as AfxApplication).repository

    val settings: StateFlow<UserSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val allMessages: StateFlow<List<CachedSmsEntity>> = repo.allMessagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteMessages: StateFlow<List<CachedSmsEntity>> = repo.favoriteMessagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = repo.backupHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telegramHistory: StateFlow<List<BackupHistoryEntity>> = repo.telegramHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = repo.totalCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inboxCount: StateFlow<Int> = repo.inboxCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val unreadCount: StateFlow<Int> = repo.unreadCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(SmsFilterOption.ALL)
    val activeFilter: StateFlow<SmsFilterOption> = _activeFilter.asStateFlow()

    private val _activeSection = MutableStateFlow(SmsBoxSection.ALL)
    val activeSection: StateFlow<SmsBoxSection> = _activeSection.asStateFlow()

    private val _selectedKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedKeys: StateFlow<Set<String>> = _selectedKeys.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _localBackups = MutableStateFlow<List<LocalBackupFileInfo>>(emptyList())
    val localBackups: StateFlow<List<LocalBackupFileInfo>> = _localBackups.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _pendingTelegramPreview = MutableStateFlow<PendingTelegramPreview?>(null)
    val pendingTelegramPreview: StateFlow<PendingTelegramPreview?> = _pendingTelegramPreview.asStateFlow()

    private val _lastExportedJsonForSaf = MutableStateFlow<Pair<String, String>?>(null)
    val lastExportedJsonForSaf: StateFlow<Pair<String, String>?> = _lastExportedJsonForSaf.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var backgroundedAtMs: Long = 0L

    val filteredMessages: StateFlow<List<CachedSmsEntity>> = combine(
        allMessages,
        _searchQuery,
        _activeFilter,
        _activeSection
    ) { messages, query, filter, section ->
        val q = query.trim().lowercase()
        messages.filter { msg ->
            val matchesSection = when (section) {
                SmsBoxSection.ALL -> true
                SmsBoxSection.INBOX -> msg.type == 1
                SmsBoxSection.SENT -> msg.type == 2
            }
            val matchesFilter = when (filter) {
                SmsFilterOption.ALL -> true
                SmsFilterOption.UNREAD -> !msg.isRead
                SmsFilterOption.READ -> msg.isRead
                SmsFilterOption.FAVORITES -> msg.isFavorite
                SmsFilterOption.TODAY -> Formatters.isToday(msg.timestamp)
                SmsFilterOption.THIS_WEEK -> Formatters.isThisWeek(msg.timestamp)
            }
            val matchesQuery = q.isEmpty() ||
                msg.displayName.lowercase().contains(q) ||
                msg.address.lowercase().contains(q) ||
                msg.body.lowercase().contains(q)
            matchesSection && matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshPermissions()
        refreshStorageAndBackups()
        viewModelScope.launch {
            val currentSettings = repo.settingsFlow.first()
            if (currentSettings.appLockEnabled && currentSettings.hasPinConfigured) {
                _isLocked.value = true
            }
            if (repo.smsDataSource.hasReadSmsPermission()) {
                syncInbox()
            }
        }
    }

    fun onAppResumed() {
        refreshPermissions()
        refreshStorageAndBackups()
        viewModelScope.launch {
            val s = repo.settingsFlow.first()
            if (s.appLockEnabled && s.hasPinConfigured && backgroundedAtMs > 0L) {
                val elapsedSec = (System.currentTimeMillis() - backgroundedAtMs) / 1000L
                if (elapsedSec >= s.appLockTimeoutSeconds) {
                    _isLocked.value = true
                }
            }
            backgroundedAtMs = 0L
            if (repo.smsDataSource.hasReadSmsPermission()) {
                syncInbox()
            }
        }
    }

    fun onAppPaused() {
        backgroundedAtMs = System.currentTimeMillis()
    }

    fun refreshPermissions() {
        _permissions.value = PermissionSnapshot(
            readSmsGranted = repo.smsDataSource.hasReadSmsPermission(),
            receiveSmsGranted = repo.smsDataSource.hasReceiveSmsPermission(),
            readContactsGranted = repo.smsDataSource.hasReadContactsPermission(),
            notificationsGranted = AfxNotificationHelper.hasNotificationPermission(getApplication()),
            isDefaultSmsApp = repo.smsDataSource.isDefaultSmsApp()
        )
    }

    fun refreshStorageAndBackups() {
        _localBackups.value = repo.listLocalBackups()
        _storageBytes.value = repo.getStorageBytes()
    }

    fun syncInbox() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            _syncState.value = repo.syncInboxFromDevice()
            refreshStorageAndBackups()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: SmsFilterOption) {
        _activeFilter.value = filter
    }

    fun setSection(section: SmsBoxSection) {
        _activeSection.value = section
    }

    fun toggleMessageSelection(uniqueKey: String) {
        val current = _selectedKeys.value.toMutableSet()
        if (!current.add(uniqueKey)) current.remove(uniqueKey)
        _selectedKeys.value = current
    }

    fun selectAllVisible() {
        _selectedKeys.value = filteredMessages.value.map { it.uniqueKey }.toSet()
    }

    fun clearSelection() {
        _selectedKeys.value = emptySet()
    }

    fun observeMessage(uniqueKey: String) = repo.observeMessage(uniqueKey)

    fun toggleFavorite(uniqueKey: String) {
        viewModelScope.launch { repo.toggleFavorite(uniqueKey) }
    }

    fun markReadLocally(uniqueKey: String) {
        viewModelScope.launch { repo.markReadLocally(uniqueKey) }
    }

    fun deleteMessage(message: CachedSmsEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val (deleted, msg) = repo.deleteMessage(message)
            _snackbarEvents.tryEmit(msg)
            refreshStorageAndBackups()
            if (deleted) onDeleted()
        }
    }

    // --- Local Backup & Restore ---

    fun createLocalBackup(selectedOnly: Boolean, encryptPassphrase: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val list = if (selectedOnly && _selectedKeys.value.isNotEmpty()) {
                allMessages.value.filter { _selectedKeys.value.contains(it.uniqueKey) }
            } else {
                allMessages.value
            }
            when (val res = repo.createLocalBackup(list, encryptPassphrase)) {
                is BackupExportResult.Success -> {
                    _lastExportedJsonForSaf.value = res.fileName to res.jsonContent
                    _snackbarEvents.tryEmit("Backed up ${res.exportedCount} messages to ${res.fileName}")
                    clearSelection()
                }
                is BackupExportResult.Failure -> {
                    _snackbarEvents.tryEmit(res.errorMessage)
                }
            }
            refreshStorageAndBackups()
            _isBusy.value = false
        }
    }

    fun backupSingleMessageLocal(message: CachedSmsEntity) {
        viewModelScope.launch {
            _isBusy.value = true
            when (val res = repo.createLocalBackup(listOf(message), null)) {
                is BackupExportResult.Success -> {
                    _snackbarEvents.tryEmit("Saved backup: ${res.fileName}")
                }
                is BackupExportResult.Failure -> {
                    _snackbarEvents.tryEmit(res.errorMessage)
                }
            }
            refreshStorageAndBackups()
            _isBusy.value = false
        }
    }

    fun clearPendingSafExport() {
        _lastExportedJsonForSaf.value = null
    }

    fun restoreFromRawText(
        rawContent: String,
        passphrase: String?,
        onNeedPassphrase: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            when (val res = repo.restoreBackupFromText(rawContent, passphrase)) {
                is BackupRestoreResult.Success -> {
                    _snackbarEvents.tryEmit(
                        "Restored ${res.restoredCount} messages (${res.duplicateSkippedCount} duplicates skipped)."
                    )
                    refreshStorageAndBackups()
                }
                is BackupRestoreResult.PassphraseRequired -> onNeedPassphrase()
                is BackupRestoreResult.WrongPassphrase -> {
                    _snackbarEvents.tryEmit("Incorrect backup passphrase.")
                }
                is BackupRestoreResult.InvalidFile -> {
                    _snackbarEvents.tryEmit(res.reason)
                }
            }
            _isBusy.value = false
        }
    }

    fun restoreFromLocalFile(filePath: String, passphrase: String?, onNeedPassphrase: () -> Unit) {
        viewModelScope.launch {
            val content = repo.localBackupManager.readBackupFileText(filePath)
            if (content == null) {
                _snackbarEvents.tryEmit("Unable to read backup file.")
                return@launch
            }
            restoreFromRawText(content, passphrase, onNeedPassphrase)
        }
    }

    fun deleteLocalBackupFile(filePath: String) {
        viewModelScope.launch {
            repo.localBackupManager.deleteBackupFile(filePath)
            refreshStorageAndBackups()
            _snackbarEvents.tryEmit("Backup file deleted.")
        }
    }

    // --- Telegram Backup (Explicit User Selection + Preview + Confirmation) ---

    fun testAndConnectTelegram(token: String, chatId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            when (val res = repo.testAndSaveTelegramConnection(token, chatId)) {
                is TelegramResult.Success -> {
                    _snackbarEvents.tryEmit("Connected to ${res.data} (credentials encrypted in Keystore)")
                }
                is TelegramResult.Error -> {
                    _snackbarEvents.tryEmit(res.message)
                }
            }
            _isBusy.value = false
        }
    }

    fun disconnectTelegram(clearCredentials: Boolean) {
        viewModelScope.launch {
            repo.preferences.disconnectTelegram(clearCredentials)
            _snackbarEvents.tryEmit(
                if (clearCredentials) "Telegram settings cleared." else "Telegram disconnected."
            )
        }
    }

    fun prepareTelegramBackupPreview(
        messagesToBackup: List<CachedSmsEntity>,
        sendAsFile: Boolean,
        encryptFilePassphrase: String? = null
    ) {
        val currentSettings = settings.value
        if (!currentSettings.telegramConnected) {
            _snackbarEvents.tryEmit("Connect and test your Telegram bot first.")
            return
        }
        if (messagesToBackup.isEmpty()) {
            _snackbarEvents.tryEmit("Select at least one message or conversation to back up.")
            return
        }
        val excluded = if (currentSettings.telegramExcludeSensitive) {
            messagesToBackup.count { it.isSensitive }
        } else 0
        val eligible = if (currentSettings.telegramExcludeSensitive) {
            messagesToBackup.filterNot { it.isSensitive }
        } else {
            messagesToBackup
        }
        _pendingTelegramPreview.value = PendingTelegramPreview(
            targetMessages = messagesToBackup,
            eligibleMessages = eligible,
            excludedSensitiveCount = excluded,
            sendAsFile = sendAsFile,
            encryptFilePassphrase = encryptFilePassphrase
        )
    }

    fun dismissTelegramPreview() {
        _pendingTelegramPreview.value = null
    }

    fun confirmAndSendTelegramBackup() {
        val preview = _pendingTelegramPreview.value ?: return
        _pendingTelegramPreview.value = null
        viewModelScope.launch {
            _isBusy.value = true
            when (
                val res = repo.sendConfirmedTelegramBackup(
                    selectedMessages = preview.targetMessages,
                    asFile = preview.sendAsFile,
                    encryptFilePassphrase = preview.encryptFilePassphrase
                )
            ) {
                is TelegramResult.Success -> {
                    _snackbarEvents.tryEmit(res.data)
                    clearSelection()
                }
                is TelegramResult.Error -> {
                    _snackbarEvents.tryEmit(res.message)
                }
            }
            refreshStorageAndBackups()
            _isBusy.value = false
        }
    }

    // --- App Lock & Settings ---

    fun verifyAndUnlock(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repo.preferences.verifyPin(pin)
            if (ok) _isLocked.value = false
            onResult(ok)
        }
    }

    fun unlockViaBiometric() {
        _isLocked.value = false
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            repo.preferences.setPin(pin)
            _snackbarEvents.tryEmit("App Lock PIN enabled.")
        }
    }

    fun disableAppLock() {
        viewModelScope.launch {
            repo.preferences.clearPin()
            _isLocked.value = false
            _snackbarEvents.tryEmit("App Lock disabled.")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.preferences.setBiometricLockEnabled(enabled) }
    }

    fun setAppLockTimeout(seconds: Int) {
        viewModelScope.launch { repo.preferences.setAppLockTimeout(seconds) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.preferences.updateThemeMode(mode) }
    }

    fun updateAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { repo.preferences.updateAccentPreset(preset) }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.preferences.updateNotificationsEnabled(enabled) }
    }

    fun updateNotifyNewSms(enabled: Boolean) {
        viewModelScope.launch { repo.preferences.updateNotifyNewSms(enabled) }
    }

    fun updateNotifyBackupStatus(enabled: Boolean) {
        viewModelScope.launch { repo.preferences.updateNotifyBackupStatus(enabled) }
    }

    fun updateNotifyBackupReminder(enabled: Boolean) {
        viewModelScope.launch { repo.preferences.updateNotifyBackupReminder(enabled) }
    }

    fun updateNotificationSound(enabled: Boolean) {
        viewModelScope.launch { repo.preferences.updateNotificationSound(enabled) }
    }

    fun updatePrivacyHidePreviews(hide: Boolean) {
        viewModelScope.launch { repo.preferences.updatePrivacyHidePreviews(hide) }
    }

    fun updateResolveContacts(enabled: Boolean) {
        viewModelScope.launch {
            repo.preferences.updateResolveContacts(enabled)
            if (repo.smsDataSource.hasReadSmsPermission()) {
                syncInbox()
            }
        }
    }

    fun setTelegramExcludeSensitive(exclude: Boolean) {
        viewModelScope.launch { repo.preferences.setTelegramExcludeSensitive(exclude) }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            repo.clearLocalCache()
            refreshStorageAndBackups()
            _snackbarEvents.tryEmit("Local SMS cache cleared. Tap Sync to reload from device.")
        }
    }

    suspend fun exportSettingsJson(): String = repo.preferences.exportSettingsJson()

    fun notifyMessage(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }
}
