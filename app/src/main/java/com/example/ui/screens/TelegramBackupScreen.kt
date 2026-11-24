package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.CachedSmsEntity
import com.example.data.preferences.UserSettings
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.util.Formatters
import com.example.ui.viewmodel.PendingTelegramPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBackupScreen(
    settings: UserSettings,
    allMessages: List<CachedSmsEntity>,
    selectedKeys: Set<String>,
    telegramHistory: List<BackupHistoryEntity>,
    isBusy: Boolean,
    pendingPreview: PendingTelegramPreview?,
    onBack: () -> Unit,
    onTestAndConnect: (token: String, chatId: String) -> Unit,
    onDisconnect: (clearCredentials: Boolean) -> Unit,
    onToggleExcludeSensitive: (Boolean) -> Unit,
    onPrepareBackup: (messages: List<CachedSmsEntity>, asFile: Boolean) -> Unit,
    onConfirmSend: () -> Unit,
    onDismissPreview: () -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    var chatIdInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var showConversationPicker by remember { mutableStateOf(false) }
    var sendAsFile by remember { mutableStateOf(false) }

    val selectedMessages = remember(allMessages, selectedKeys) {
        allMessages.filter { selectedKeys.contains(it.uniqueKey) }
    }

    val conversations = remember(allMessages) {
        allMessages.groupBy { it.address }.map { (addr, msgs) ->
            val title = msgs.firstOrNull()?.displayName ?: addr
            Triple(addr, title, msgs)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            TopAppBar(
                title = { Text("Telegram Bot Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("telegram_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Connection Status & Credentials Card
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bot Configuration", style = MaterialTheme.typography.titleLarge)
                                StatusChip(
                                    label = if (settings.telegramConnected) "Connected" else "Disconnected",
                                    icon = if (settings.telegramConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    tint = if (settings.telegramConnected) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (settings.telegramMaskedToken.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Saved Bot: ${settings.telegramBotUsername.ifBlank { "Configured" }}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Encrypted Token: ${settings.telegramMaskedToken}  |  Chat: ${settings.telegramChatIdPreview}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("telegram_token_input"),
                                label = { Text("Telegram Bot Token") },
                                placeholder = { Text("123456789:ABCdef...") },
                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showToken = !showToken }) {
                                        Icon(
                                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showToken) "Hide token" else "Show token"
                                        )
                                    }
                                },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = chatIdInput,
                                onValueChange = { chatIdInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("telegram_chat_id_input"),
                                label = { Text("Personal Chat ID") },
                                placeholder = { Text("e.g. 987654321") },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        onTestAndConnect(tokenInput, chatIdInput)
                                        tokenInput = ""
                                    },
                                    enabled = !isBusy && tokenInput.isNotBlank() && chatIdInput.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("telegram_connect_btn")
                                ) {
                                    Text("Connect & Test")
                                }

                                if (settings.telegramConnected) {
                                    OutlinedButton(
                                        onClick = { onDisconnect(false) },
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Disconnect")
                                    }
                                }
                            }

                            if (settings.telegramMaskedToken.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        tokenInput = ""
                                        chatIdInput = ""
                                        onDisconnect(true)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Clear Saved Telegram Credentials")
                                }
                            }
                        }
                    }
                }

                // Safeguards & User-Initiated Backup Card
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("User-Controlled Backup", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Messages are only sent when you explicitly select them, review the full preview, and confirm transmission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Shield, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Exclude OTPs & Banking Alerts", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text(
                                        "Automatically filters verification codes, PINs, and financial alerts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.telegramExcludeSensitive,
                                    onCheckedChange = onToggleExcludeSensitive
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Send as JSON Backup File", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Upload a .json document instead of formatted chat messages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = sendAsFile,
                                    onCheckedChange = { sendAsFile = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { onPrepareBackup(selectedMessages, sendAsFile) },
                                enabled = settings.telegramConnected && selectedMessages.isNotEmpty() && !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("telegram_backup_selected_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back Up Selected SMS (${selectedMessages.size})")
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            FilledTonalButton(
                                onClick = { showConversationPicker = true },
                                enabled = settings.telegramConnected && conversations.isNotEmpty() && !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("telegram_backup_conversation_btn")
                            ) {
                                Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Conversation to Back Up")
                            }
                        }
                    }
                }

                // Telegram History
                item {
                    Text(
                        text = "Telegram Backup History",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (telegramHistory.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No Telegram backups sent yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(telegramHistory, key = { it.id }) { entry ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = entry.status,
                                    tint = if (entry.status == "SUCCESS") EmeraldSuccess else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entry.messageCount} messages sent (${entry.excludedSensitiveCount} sensitive excluded)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = entry.errorMessage ?: Formatters.formatDateTime(entry.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConversationPicker) {
        AlertDialog(
            onDismissRequest = { showConversationPicker = false },
            title = { Text("Choose Conversation") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    conversations.take(25).forEach { (addr, title, msgs) ->
                        TextButton(
                            onClick = {
                                showConversationPicker = false
                                onPrepareBackup(msgs, sendAsFile)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(title, modifier = Modifier.weight(1f))
                                Text("${msgs.size} msgs", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConversationPicker = false }) { Text("Close") }
            }
        )
    }

    // Mandatory Full Preview & Explicit Confirmation Dialog
    if (pendingPreview != null) {
        AlertDialog(
            onDismissRequest = onDismissPreview,
            title = { Text("Confirm Telegram Backup") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Bot: ${settings.telegramBotUsername} (Chat ${settings.telegramChatIdPreview})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mode: ${if (pendingPreview.sendAsFile) "JSON Backup File" else "Formatted Text Messages"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (pendingPreview.excludedSensitiveCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${pendingPreview.excludedSensitiveCount} OTP/banking message(s) automatically excluded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Preview of ${pendingPreview.eligibleMessages.size} message(s) to send:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    pendingPreview.eligibleMessages.take(8).forEach { msg ->
                        Text(
                            text = "• ${msg.displayName}: ${msg.body.take(80)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (pendingPreview.eligibleMessages.size > 8) {
                        Text(
                            text = "...and ${pendingPreview.eligibleMessages.size - 8} more message(s).",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmSend,
                    enabled = pendingPreview.eligibleMessages.isNotEmpty(),
                    modifier = Modifier.testTag("telegram_confirm_send_btn")
                ) {
                    Text("Confirm & Send")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPreview) { Text("Cancel") }
            }
        )
    }
}
