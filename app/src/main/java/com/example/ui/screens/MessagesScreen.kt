package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.local.CachedSmsEntity
import com.example.data.preferences.UserSettings
import com.example.data.repository.SyncState
import com.example.ui.components.EmptyStateBannerCard
import com.example.ui.components.GlassCard
import com.example.ui.components.SmsMessageRow
import com.example.ui.theme.AfxSmsManagerTheme
import com.example.ui.viewmodel.PermissionSnapshot
import com.example.ui.viewmodel.SmsBoxSection
import com.example.ui.viewmodel.SmsFilterOption
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    messages: List<CachedSmsEntity>,
    searchQuery: String,
    activeFilter: SmsFilterOption,
    activeSection: SmsBoxSection,
    selectedKeys: Set<String>,
    settings: UserSettings,
    permissions: PermissionSnapshot,
    syncState: SyncState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (SmsFilterOption) -> Unit,
    onSectionChange: (SmsBoxSection) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSyncNow: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenMessageDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBackupSelectedLocal: () -> Unit,
    onBackupSelectedTelegram: () -> Unit
) {
    var selectionMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 4 }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${messages.size} visible • Sorted newest first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) onClearSelection()
                        },
                        modifier = Modifier.testTag("messages_selection_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (selectionMode) Icons.Default.Close else Icons.Default.CheckBox,
                            contentDescription = "Toggle multi-select",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onSyncNow,
                        enabled = syncState !is SyncState.Syncing,
                        modifier = Modifier.testTag("messages_sync_btn")
                    ) {
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh SMS")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("messages_search_input"),
                placeholder = { Text("Search sender, number, or text...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
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

            Spacer(modifier = Modifier.height(10.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SmsBoxSection.entries.forEachIndexed { idx, section ->
                    SegmentedButton(
                        selected = activeSection == section,
                        onClick = { onSectionChange(section) },
                        shape = SegmentedButtonDefaults.itemShape(idx, SmsBoxSection.entries.size)
                    ) {
                        Text(section.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SmsFilterOption.entries) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            AnimatedVisibility(visible = selectionMode) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedKeys.size} selected",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Row {
                                TextButton(onClick = onSelectAll) {
                                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Select All")
                                }
                                TextButton(onClick = onClearSelection) {
                                    Text("Clear")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onBackupSelectedLocal,
                                enabled = selectedKeys.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local JSON")
                            }
                            Button(
                                onClick = onBackupSelectedTelegram,
                                enabled = selectedKeys.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Telegram")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            PullToRefreshBox(
                isRefreshing = syncState is SyncState.Syncing,
                onRefresh = onSyncNow,
                modifier = Modifier.weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("messages_lazy_column"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!permissions.readSmsGranted && messages.isEmpty()) {
                        item {
                            EmptyStateBannerCard(
                                title = "SMS Permission Needed",
                                description = "AFX SMS Manager reads your device SMS locally only after you grant READ_SMS permission. No messages leave your device without your explicit action.",
                                actionLabel = "Grant Read SMS",
                                onAction = onRequestSmsPermission
                            )
                        }
                    } else if (messages.isEmpty()) {
                        item {
                            EmptyStateBannerCard(
                                title = "No Messages Here",
                                description = if (searchQuery.isNotBlank() || activeFilter != SmsFilterOption.ALL) {
                                    "Try clearing your search or choosing another filter."
                                } else {
                                    "Your device SMS inbox is empty or hasn't been synced yet."
                                },
                                actionLabel = "Sync Inbox",
                                onAction = onSyncNow
                            )
                        }
                    } else {
                        items(messages, key = { it.uniqueKey }) { msg ->
                            SmsMessageRow(
                                message = msg,
                                privacyHidePreview = settings.privacyModeHidePreviews,
                                selectionMode = selectionMode,
                                isSelected = selectedKeys.contains(msg.uniqueKey),
                                onClick = { onOpenMessageDetail(msg.uniqueKey) },
                                onToggleFavorite = { onToggleFavorite(msg.uniqueKey) },
                                onToggleSelect = { onToggleSelect(msg.uniqueKey) }
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessagesScreenPreview() {
    AfxSmsManagerTheme {
        MessagesScreen(
            messages = listOf(
                CachedSmsEntity(
                    uniqueKey = "preview_1",
                    systemSmsId = 1L,
                    threadId = 1L,
                    address = "+1 555 0142",
                    displayName = "Alex Rivera",
                    body = "Are we still on for the project sync at 3:30 PM?",
                    timestamp = System.currentTimeMillis() - 120_000L,
                    type = 1,
                    isRead = false,
                    isFavorite = true,
                    bodyHash = "preview_hash_1"
                ),
                CachedSmsEntity(
                    uniqueKey = "preview_2",
                    systemSmsId = 2L,
                    threadId = 2L,
                    address = "+1 555 0198",
                    displayName = "+1 555 0198",
                    body = "Package out for delivery today before 6 PM.",
                    timestamp = System.currentTimeMillis() - 3_600_000L,
                    type = 1,
                    isRead = true,
                    isFavorite = false,
                    bodyHash = "preview_hash_2"
                )
            ),
            searchQuery = "",
            activeFilter = SmsFilterOption.ALL,
            activeSection = SmsBoxSection.ALL,
            selectedKeys = emptySet(),
            settings = UserSettings(),
            permissions = PermissionSnapshot(readSmsGranted = true),
            syncState = SyncState.Ready(2),
            onSearchChange = {},
            onFilterChange = {},
            onSectionChange = {},
            onToggleSelect = {},
            onSelectAll = {},
            onClearSelection = {},
            onSyncNow = {},
            onRequestSmsPermission = {},
            onOpenMessageDetail = {},
            onToggleFavorite = {},
            onBackupSelectedLocal = {},
            onBackupSelectedTelegram = {}
        )
    }
}
