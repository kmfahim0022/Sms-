package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CachedSmsEntity
import com.example.data.preferences.UserSettings
import com.example.data.repository.SyncState
import com.example.ui.components.EmptyStateBannerCard
import com.example.ui.components.GlassCard
import com.example.ui.components.MetricGlassTile
import com.example.ui.components.SmsMessageRow
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.util.Formatters
import com.example.ui.viewmodel.PermissionSnapshot

@Composable
fun DashboardScreen(
    settings: UserSettings,
    permissions: PermissionSnapshot,
    syncState: SyncState,
    totalCount: Int,
    inboxCount: Int,
    unreadCount: Int,
    storageBytes: Long,
    localBackupCount: Int,
    recentMessages: List<CachedSmsEntity>,
    onQuickSearchSubmit: (String) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLocalBackup: () -> Unit,
    onOpenTelegramBackup: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenMessageDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var quickQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AFX SMS Manager",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Personal Vault • User-Controlled Backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenPermissions,
                            modifier = Modifier.testTag("dashboard_permissions_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permissions Manager",
                                tint = if (permissions.readSmsGranted) EmeraldSuccess else AmberWarning
                            )
                        }
                        IconButton(
                            onClick = onSyncNow,
                            enabled = syncState !is SyncState.Syncing,
                            modifier = Modifier.testTag("dashboard_sync_btn")
                        ) {
                            if (syncState is SyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync SMS inbox",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Quick Search
            item {
                OutlinedTextField(
                    value = quickQuery,
                    onValueChange = { quickQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_quick_search"),
                    placeholder = { Text("Quick search sender, phone, or message...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (quickQuery.isNotBlank()) {
                            TextButton(onClick = { onQuickSearchSubmit(quickQuery) }) {
                                Text("Go")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                    )
                )
            }

            // Permission CTA if READ_SMS missing
            if (!permissions.readSmsGranted) {
                item {
                    EmptyStateBannerCard(
                        title = "Enable Local SMS Access",
                        description = "Grant Read SMS permission to load your personal inbox, search messages, organize favorites, and create encrypted local or Telegram backups.",
                        actionLabel = "Grant SMS Access",
                        onAction = onRequestSmsPermission
                    )
                }
            }

            // 2x2 Metrics Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricGlassTile(
                            title = "Total SMS",
                            value = totalCount.toString(),
                            subtitle = "Indexed locally",
                            icon = Icons.Default.Sms,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMessages
                        )
                        MetricGlassTile(
                            title = "Inbox",
                            value = inboxCount.toString(),
                            subtitle = "Received messages",
                            icon = Icons.Default.Inbox,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMessages
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricGlassTile(
                            title = "Unread",
                            value = unreadCount.toString(),
                            subtitle = if (unreadCount > 0) "Needs attention" else "All caught up",
                            icon = Icons.Default.MarkEmailUnread,
                            accentColor = if (unreadCount > 0) AmberWarning else EmeraldSuccess,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenMessages
                        )
                        MetricGlassTile(
                            title = "Local Storage",
                            value = Formatters.formatBytes(storageBytes),
                            subtitle = "$localBackupCount backup file(s)",
                            icon = Icons.Default.Storage,
                            accentColor = EmeraldSuccess,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLocalBackup
                        )
                    }
                }
            }

            // Backup Status Hero Glass Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_privacy_banner_1795527480049),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop,
                            alpha = 0.22f
                        )
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Backup Status",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                StatusChip(
                                    label = if (settings.telegramConnected) "Telegram Connected" else "Local Only",
                                    icon = if (settings.telegramConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    tint = if (settings.telegramConnected) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = settings.lastBackupStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Last backup: ${Formatters.formatDateTime(settings.lastBackupTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onOpenLocalBackup,
                                    modifier = Modifier.testTag("dashboard_local_backup_btn")
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Local Backup")
                                }
                                FilledTonalButton(
                                    onClick = onOpenTelegramBackup,
                                    modifier = Modifier.testTag("dashboard_telegram_backup_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Telegram Bot")
                                }
                            }
                        }
                    }
                }
            }

            // Quick-Access Cards
            item {
                Text(
                    text = "Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        QuickAccessCard("Inbox", "Live SMS feed", Icons.Default.Inbox, onOpenMessages)
                    }
                    item {
                        QuickAccessCard("Favorites", "Pinned messages", Icons.Default.Favorite, onOpenFavorites)
                    }
                    item {
                        QuickAccessCard("Local Vault", "JSON & Encrypted", Icons.Default.FolderZip, onOpenLocalBackup)
                    }
                    item {
                        QuickAccessCard("Telegram", "Personal Bot", Icons.AutoMirrored.Filled.Send, onOpenTelegramBackup)
                    }
                }
            }

            // Recent Messages
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Messages",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onOpenMessages) {
                        Text("See All")
                    }
                }
            }

            if (recentMessages.isEmpty()) {
                item {
                    AnimatedVisibility(visible = permissions.readSmsGranted) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No SMS messages found on device yet. Tap Sync above after receiving messages.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            } else {
                items(recentMessages.take(5), key = { it.uniqueKey }) { msg ->
                    SmsMessageRow(
                        message = msg,
                        privacyHidePreview = settings.privacyModeHidePreviews,
                        selectionMode = false,
                        isSelected = false,
                        onClick = { onOpenMessageDetail(msg.uniqueKey) },
                        onToggleFavorite = { onToggleFavorite(msg.uniqueKey) },
                        onToggleSelect = {}
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.width(142.dp),
        cornerRadius = 18.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
