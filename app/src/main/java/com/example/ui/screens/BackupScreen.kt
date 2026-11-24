package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.backup.LocalBackupFileInfo
import com.example.data.local.BackupHistoryEntity
import com.example.data.preferences.UserSettings
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.util.Formatters

@Composable
fun BackupScreen(
    settings: UserSettings,
    totalMessagesCount: Int,
    selectedCount: Int,
    localBackups: List<LocalBackupFileInfo>,
    backupHistory: List<BackupHistoryEntity>,
    isBusy: Boolean,
    pendingSafExport: Pair<String, String>?,
    onCreateLocalBackup: (selectedOnly: Boolean, passphrase: String?) -> Unit,
    onConsumePendingSafExport: () -> Unit,
    onRestoreRawContent: (rawContent: String, passphrase: String?, onNeedPassphrase: () -> Unit) -> Unit,
    onRestoreLocalFile: (filePath: String, passphrase: String?, onNeedPassphrase: () -> Unit) -> Unit,
    onDeleteLocalBackup: (filePath: String) -> Unit,
    onOpenTelegramBackup: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    var encryptBackup by remember { mutableStateOf(false) }
    var showEncryptPrompt by remember { mutableStateOf(false) }
    var encryptSelectedOnly by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }

    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreFilePath by remember { mutableStateOf<String?>(null) }
    var showDecryptPrompt by remember { mutableStateOf(false) }
    var decryptPassphrase by remember { mutableStateOf("") }

    val safCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pending = pendingSafExport
        if (uri != null && pending != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pending.second.toByteArray(Charsets.UTF_8))
                }
                onNotify("Exported ${pending.first} to chosen folder.")
            }.onFailure {
                onNotify("Failed to export file to selected location.")
            }
        }
        onConsumePendingSafExport()
    }

    val safOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader(Charsets.UTF_8).readText()
                    pendingRestoreContent = content
                    pendingRestoreFilePath = null
                    onRestoreRawContent(content, null) {
                        showDecryptPrompt = true
                    }
                }
            }.onFailure {
                onNotify("Could not open selected backup file.")
            }
        }
    }

    LaunchedEffect(pendingSafExport) {
        if (pendingSafExport != null) {
            // Keep available for explicit "Save Copy to Downloads/Files" button or auto-prompt
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "User-initiated local JSON, encrypted vault, and personal Telegram bot backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isBusy) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Local Backup Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Local Backup",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalMessagesCount eligible messages • Last status: ${settings.lastBackupStatus}",
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
                                Text("AES-256 Encrypted Backup", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Protect backup JSON with a custom passphrase",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = encryptBackup,
                                onCheckedChange = { encryptBackup = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (encryptBackup) {
                                        encryptSelectedOnly = false
                                        showEncryptPrompt = true
                                    } else {
                                        onCreateLocalBackup(false, null)
                                    }
                                },
                                enabled = !isBusy && totalMessagesCount > 0,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("backup_all_local_btn")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back Up All")
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (encryptBackup) {
                                        encryptSelectedOnly = true
                                        showEncryptPrompt = true
                                    } else {
                                        onCreateLocalBackup(true, null)
                                    }
                                },
                                enabled = !isBusy && selectedCount > 0,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Selected ($selectedCount)")
                            }
                        }

                        if (pendingSafExport != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { safCreateLauncher.launch(pendingSafExport.first) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Copy to Device Files (${pendingSafExport.first})")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { safOpenLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restore_from_file_btn")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import & Restore Backup File")
                        }
                    }
                }
            }

            // Personal Telegram Bot Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    onClick = onOpenTelegramBackup
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Personal Telegram Backup",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (settings.telegramConnected) {
                                    StatusChip("Connected", Icons.Default.CloudDone, EmeraldSuccess)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (settings.telegramConnected) {
                                    "Connected to ${settings.telegramBotUsername}. Back up selected messages with preview & OTP filter."
                                } else {
                                    "Connect your personal Telegram bot with Keystore-encrypted credentials."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Open Telegram Backup",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Saved Local Backups
            item {
                Text(
                    text = "Local Backup Vault (${localBackups.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (localBackups.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No local backup files saved yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(localBackups, key = { it.filePath }) { file ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (file.isEncrypted) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Encrypted",
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = file.fileName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Text(
                                    text = "${Formatters.formatBytes(file.sizeBytes)} • ${Formatters.formatDateTime(file.lastModified)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    pendingRestoreFilePath = file.filePath
                                    pendingRestoreContent = null
                                    onRestoreLocalFile(file.filePath, null) {
                                        showDecryptPrompt = true
                                    }
                                }) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore backup")
                                }
                                IconButton(onClick = { onDeleteLocalBackup(file.filePath) }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete backup file",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent History
            if (backupHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Backup Activity Log",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(backupHistory.take(10), key = { it.id }) { entry ->
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                    text = "${entry.destination.replace('_', ' ')} • ${entry.messageCount} msgs",
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

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showEncryptPrompt) {
        AlertDialog(
            onDismissRequest = { showEncryptPrompt = false },
            title = { Text("Encrypt Local Backup") },
            text = {
                Column {
                    Text("Enter a strong passphrase to encrypt this backup with AES-256-GCM. You will need it to restore.")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Passphrase (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = passphraseInput
                        passphraseInput = ""
                        showEncryptPrompt = false
                        onCreateLocalBackup(encryptSelectedOnly, pass)
                    },
                    enabled = passphraseInput.length >= 6
                ) {
                    Text("Encrypt & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptPrompt = false }) { Text("Cancel") }
            }
        )
    }

    if (showDecryptPrompt) {
        AlertDialog(
            onDismissRequest = { showDecryptPrompt = false },
            title = { Text("Enter Backup Passphrase") },
            text = {
                Column {
                    Text("This backup file is encrypted. Enter its passphrase to validate and restore.")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = decryptPassphrase,
                        onValueChange = { decryptPassphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = decryptPassphrase
                        decryptPassphrase = ""
                        showDecryptPrompt = false
                        val raw = pendingRestoreContent
                        val path = pendingRestoreFilePath
                        if (raw != null) {
                            onRestoreRawContent(raw, pass) { showDecryptPrompt = true }
                        } else if (path != null) {
                            onRestoreLocalFile(path, pass) { showDecryptPrompt = true }
                        }
                    },
                    enabled = decryptPassphrase.isNotEmpty()
                ) {
                    Text("Decrypt & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDecryptPrompt = false }) { Text("Cancel") }
            }
        )
    }
}
