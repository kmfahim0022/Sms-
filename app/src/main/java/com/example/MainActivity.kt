package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.components.AfxBackgroundSurface
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.navigation.AboutRoute
import com.example.ui.navigation.BackupRoute
import com.example.ui.navigation.FavoritesRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.MessagesRoute
import com.example.ui.navigation.PermissionsManagerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SmsDetailRoute
import com.example.ui.navigation.TelegramBackupRoute
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AppLockOverlay
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.PermissionsManagerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmsDetailScreen
import com.example.ui.screens.TelegramBackupScreen
import com.example.ui.theme.AfxSmsManagerTheme
import com.example.ui.viewmodel.MainViewModel

private data class TopLevelNavItem(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private enum class PendingPermissionRequest(val permission: String) {
    READ_SMS(Manifest.permission.READ_SMS),
    RECEIVE_SMS(Manifest.permission.RECEIVE_SMS),
    READ_CONTACTS(Manifest.permission.READ_CONTACTS),
    POST_NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else ""
    )
}

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> viewModel.onAppResumed()
                        Lifecycle.Event.ON_PAUSE -> viewModel.onAppPaused()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AfxSmsManagerTheme(
                themeMode = settings.themeMode,
                accentPreset = settings.accentPreset
            ) {
                if (isLocked) {
                    AppLockOverlay(
                        biometricEnabled = settings.biometricLockEnabled,
                        onVerifyPin = viewModel::verifyAndUnlock,
                        onBiometricSuccess = viewModel::unlockViaBiometric
                    )
                } else {
                    AfxMainAppShell(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun AfxMainAppShell(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allMessages by viewModel.allMessages.collectAsStateWithLifecycle()
    val filteredMessages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val favoriteMessages by viewModel.favoriteMessages.collectAsStateWithLifecycle()
    val backupHistory by viewModel.backupHistory.collectAsStateWithLifecycle()
    val telegramHistory by viewModel.telegramHistory.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val inboxCount by viewModel.inboxCount.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val activeSection by viewModel.activeSection.collectAsStateWithLifecycle()
    val selectedKeys by viewModel.selectedKeys.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val pendingTelegramPreview by viewModel.pendingTelegramPreview.collectAsStateWithLifecycle()
    val pendingSafExport by viewModel.lastExportedJsonForSaf.collectAsStateWithLifecycle()

    var pendingRationale by remember { mutableStateOf<PendingPermissionRequest?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.syncInbox()
            viewModel.notifyMessage("Permission granted.")
        } else {
            viewModel.notifyMessage("Permission denied. You can enable it anytime in Permissions Manager.")
        }
    }

    fun requestWithRationale(req: PendingPermissionRequest) {
        if (req == PendingPermissionRequest.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.notifyMessage("Notification permission is already active on this Android version.")
            return
        }
        pendingRationale = req
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val topLevelItems = remember {
        listOf(
            TopLevelNavItem("Home", HomeRoute, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            TopLevelNavItem("Messages", MessagesRoute, Icons.Filled.Message, Icons.Outlined.Message, "nav_messages"),
            TopLevelNavItem("Favorites", FavoritesRoute, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "nav_favorites"),
            TopLevelNavItem("Backup", BackupRoute, Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload, "nav_backup"),
            TopLevelNavItem("Settings", SettingsRoute, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showTopLevelNav = topLevelItems.any { item ->
        currentDestination?.hasRoute(item.route::class) == true
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    AfxBackgroundSurface {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showTopLevelNav && !useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        ) {
                            topLevelItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label, maxLines = 1) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showTopLevelNav && useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            topLevelItems.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        modifier = Modifier.weight(1f),
                        enterTransition = { fadeIn(animationSpec = tween(200)) },
                        exitTransition = { fadeOut(animationSpec = tween(180)) }
                    ) {
                        composable<HomeRoute> {
                            DashboardScreen(
                                settings = settings,
                                permissions = permissions,
                                syncState = syncState,
                                totalCount = totalCount,
                                inboxCount = inboxCount,
                                unreadCount = unreadCount,
                                storageBytes = storageBytes,
                                localBackupCount = localBackups.size,
                                recentMessages = allMessages,
                                onQuickSearchSubmit = { q ->
                                    viewModel.updateSearchQuery(q)
                                    navigateTopLevel(MessagesRoute)
                                },
                                onRequestSmsPermission = {
                                    requestWithRationale(PendingPermissionRequest.READ_SMS)
                                },
                                onSyncNow = {
                                    if (!permissions.readSmsGranted) {
                                        requestWithRationale(PendingPermissionRequest.READ_SMS)
                                    } else {
                                        viewModel.syncInbox()
                                    }
                                },
                                onOpenMessages = { navigateTopLevel(MessagesRoute) },
                                onOpenFavorites = { navigateTopLevel(FavoritesRoute) },
                                onOpenLocalBackup = { navigateTopLevel(BackupRoute) },
                                onOpenTelegramBackup = { navController.navigate(TelegramBackupRoute) },
                                onOpenPermissions = { navController.navigate(PermissionsManagerRoute) },
                                onOpenMessageDetail = { key -> navController.navigate(SmsDetailRoute(key)) },
                                onToggleFavorite = viewModel::toggleFavorite
                            )
                        }

                        composable<MessagesRoute> {
                            MessagesScreen(
                                messages = filteredMessages,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                activeSection = activeSection,
                                selectedKeys = selectedKeys,
                                settings = settings,
                                permissions = permissions,
                                syncState = syncState,
                                onSearchChange = viewModel::updateSearchQuery,
                                onFilterChange = viewModel::setFilter,
                                onSectionChange = viewModel::setSection,
                                onToggleSelect = viewModel::toggleMessageSelection,
                                onSelectAll = viewModel::selectAllVisible,
                                onClearSelection = viewModel::clearSelection,
                                onSyncNow = {
                                    if (!permissions.readSmsGranted) {
                                        requestWithRationale(PendingPermissionRequest.READ_SMS)
                                    } else {
                                        viewModel.syncInbox()
                                    }
                                },
                                onRequestSmsPermission = {
                                    requestWithRationale(PendingPermissionRequest.READ_SMS)
                                },
                                onOpenMessageDetail = { key -> navController.navigate(SmsDetailRoute(key)) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onBackupSelectedLocal = {
                                    viewModel.createLocalBackup(selectedOnly = true, encryptPassphrase = null)
                                    navigateTopLevel(BackupRoute)
                                },
                                onBackupSelectedTelegram = {
                                    navController.navigate(TelegramBackupRoute)
                                }
                            )
                        }

                        composable<FavoritesRoute> {
                            FavoritesScreen(
                                favorites = favoriteMessages,
                                settings = settings,
                                onOpenMessageDetail = { key -> navController.navigate(SmsDetailRoute(key)) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onBackupFavoritesLocal = {
                                    val favKeys = favoriteMessages.map { it.uniqueKey }
                                    favKeys.forEach { key ->
                                        if (!selectedKeys.contains(key)) viewModel.toggleMessageSelection(key)
                                    }
                                    viewModel.createLocalBackup(selectedOnly = true, encryptPassphrase = null)
                                },
                                onBrowseMessages = { navigateTopLevel(MessagesRoute) }
                            )
                        }

                        composable<BackupRoute> {
                            BackupScreen(
                                settings = settings,
                                totalMessagesCount = allMessages.size,
                                selectedCount = selectedKeys.size,
                                localBackups = localBackups,
                                backupHistory = backupHistory,
                                isBusy = isBusy,
                                pendingSafExport = pendingSafExport,
                                onCreateLocalBackup = viewModel::createLocalBackup,
                                onConsumePendingSafExport = viewModel::clearPendingSafExport,
                                onRestoreRawContent = viewModel::restoreFromRawText,
                                onRestoreLocalFile = viewModel::restoreFromLocalFile,
                                onDeleteLocalBackup = viewModel::deleteLocalBackupFile,
                                onOpenTelegramBackup = { navController.navigate(TelegramBackupRoute) },
                                onNotify = viewModel::notifyMessage
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                onThemeModeChange = viewModel::updateThemeMode,
                                onAccentChange = viewModel::updateAccentPreset,
                                onNotificationsChange = { enabled ->
                                    if (enabled && !permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestWithRationale(PendingPermissionRequest.POST_NOTIFICATIONS)
                                    }
                                    viewModel.updateNotificationsEnabled(enabled)
                                },
                                onNotifyNewSmsChange = { enabled ->
                                    if (enabled && !permissions.receiveSmsGranted) {
                                        requestWithRationale(PendingPermissionRequest.RECEIVE_SMS)
                                    }
                                    viewModel.updateNotifyNewSms(enabled)
                                },
                                onNotifyBackupStatusChange = viewModel::updateNotifyBackupStatus,
                                onNotifyBackupReminderChange = viewModel::updateNotifyBackupReminder,
                                onNotificationSoundChange = viewModel::updateNotificationSound,
                                onPrivacyHidePreviewsChange = viewModel::updatePrivacyHidePreviews,
                                onResolveContactsChange = { enabled ->
                                    if (enabled && !permissions.readContactsGranted) {
                                        requestWithRationale(PendingPermissionRequest.READ_CONTACTS)
                                    }
                                    viewModel.updateResolveContacts(enabled)
                                },
                                onSetPin = viewModel::setPin,
                                onDisableAppLock = viewModel::disableAppLock,
                                onBiometricChange = viewModel::setBiometricEnabled,
                                onTimeoutChange = viewModel::setAppLockTimeout,
                                onClearCache = viewModel::clearLocalCache,
                                onExportSettingsJson = viewModel::exportSettingsJson,
                                onOpenPermissions = { navController.navigate(PermissionsManagerRoute) },
                                onOpenTelegramSettings = { navController.navigate(TelegramBackupRoute) },
                                onOpenBackupSettings = { navigateTopLevel(BackupRoute) },
                                onOpenAbout = { navController.navigate(AboutRoute) },
                                onNotify = viewModel::notifyMessage
                            )
                        }

                        composable<SmsDetailRoute> { backStackEntry ->
                            val args = backStackEntry.toRoute<SmsDetailRoute>()
                            val message by viewModel.observeMessage(args.uniqueKey)
                                .collectAsStateWithLifecycle(initialValue = null)
                            SmsDetailScreen(
                                message = message,
                                isDefaultSmsApp = permissions.isDefaultSmsApp,
                                onBack = { navController.popBackStack() },
                                onMarkReadLocally = viewModel::markReadLocally,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onCopySuccess = viewModel::notifyMessage,
                                onManualLocalBackup = viewModel::backupSingleMessageLocal,
                                onBackupToTelegram = { msg ->
                                    viewModel.prepareTelegramBackupPreview(listOf(msg), sendAsFile = false)
                                    navController.navigate(TelegramBackupRoute)
                                },
                                onDeleteMessage = { msg ->
                                    viewModel.deleteMessage(msg) {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }

                        composable<TelegramBackupRoute> {
                            TelegramBackupScreen(
                                settings = settings,
                                allMessages = allMessages,
                                selectedKeys = selectedKeys,
                                telegramHistory = telegramHistory,
                                isBusy = isBusy,
                                pendingPreview = pendingTelegramPreview,
                                onBack = { navController.popBackStack() },
                                onTestAndConnect = viewModel::testAndConnectTelegram,
                                onDisconnect = viewModel::disconnectTelegram,
                                onToggleExcludeSensitive = viewModel::setTelegramExcludeSensitive,
                                onPrepareBackup = { msgs, asFile ->
                                    viewModel.prepareTelegramBackupPreview(msgs, asFile)
                                },
                                onConfirmSend = viewModel::confirmAndSendTelegramBackup,
                                onDismissPreview = viewModel::dismissTelegramPreview
                            )
                        }

                        composable<PermissionsManagerRoute> {
                            PermissionsManagerScreen(
                                permissions = permissions,
                                onBack = { navController.popBackStack() },
                                onRequestReadSms = { requestWithRationale(PendingPermissionRequest.READ_SMS) },
                                onRequestReceiveSms = { requestWithRationale(PendingPermissionRequest.RECEIVE_SMS) },
                                onRequestReadContacts = { requestWithRationale(PendingPermissionRequest.READ_CONTACTS) },
                                onRequestNotifications = { requestWithRationale(PendingPermissionRequest.POST_NOTIFICATIONS) }
                            )
                        }

                        composable<AboutRoute> {
                            AboutScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    pendingRationale?.let { req ->
        val (title, rationale) = when (req) {
            PendingPermissionRequest.READ_SMS ->
                context.getString(R.string.perm_read_sms_title) to context.getString(R.string.perm_read_sms_rationale)
            PendingPermissionRequest.RECEIVE_SMS ->
                context.getString(R.string.perm_receive_sms_title) to context.getString(R.string.perm_receive_sms_rationale)
            PendingPermissionRequest.READ_CONTACTS ->
                context.getString(R.string.perm_read_contacts_title) to context.getString(R.string.perm_read_contacts_rationale)
            PendingPermissionRequest.POST_NOTIFICATIONS ->
                context.getString(R.string.perm_notifications_title) to context.getString(R.string.perm_notifications_rationale)
        }
        PermissionRationaleDialog(
            title = title,
            rationale = rationale,
            confirmText = "Request Permission",
            onConfirm = {
                val perm = req.permission
                pendingRationale = null
                if (perm.isNotEmpty()) {
                    permissionLauncher.launch(perm)
                }
            },
            onDismiss = { pendingRationale = null }
        )
    }
}
