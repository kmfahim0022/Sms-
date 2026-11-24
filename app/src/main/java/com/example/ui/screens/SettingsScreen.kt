package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AccentPreset
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserSettings
import com.example.ui.components.GlassCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentChange: (AccentPreset) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onNotifyNewSmsChange: (Boolean) -> Unit,
    onNotifyBackupStatusChange: (Boolean) -> Unit,
    onNotifyBackupReminderChange: (Boolean) -> Unit,
    onNotificationSoundChange: (Boolean) -> Unit,
    onPrivacyHidePreviewsChange: (Boolean) -> Unit,
    onResolveContactsChange: (Boolean) -> Unit,
    onSetPin: (String) -> Unit,
    onDisableAppLock: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onClearCache: () -> Unit,
    onExportSettingsJson: suspend () -> String,
    onOpenPermissions: () -> Unit,
    onOpenTelegramSettings: () -> Unit,
    onOpenBackupSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }

    val biometricSupported = remember {
        val status = BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        status == BiometricManager.BIOMETRIC_SUCCESS
    }

    val isIgnoringBatteryOpt = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true
    }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val jsonText = onExportSettingsJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonText.toByteArray(Charsets.UTF_8))
                    }
                    onNotify("App settings exported.")
                }.onFailure {
                    onNotify("Failed to export app settings.")
                }
            }
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Appearance, notifications, privacy, app lock, and backups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Theme & Accent
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Appearance", Icons.Default.Palette)
                        Spacer(modifier = Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size)
                                ) {
                                    Text(mode.label, maxLines = 1)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Accent Color", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentPreset.entries.forEach { preset ->
                                val selected = settings.accentPreset == preset
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.hexColor))
                                        .then(
                                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { onAccentChange(preset) }
                                )
                            }
                        }
                    }
                }
            }

            // Notifications
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Notifications", Icons.Default.Notifications)
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSwitchRow(
                            title = "Enable Notifications",
                            subtitle = "Master switch for local app alerts",
                            checked = settings.notificationsEnabled,
                            onCheckedChange = onNotificationsChange
                        )
                        SettingSwitchRow(
                            title = "New SMS Received",
                            subtitle = "Show local notification when SMS arrives",
                            checked = settings.notifyNewSms,
                            enabled = settings.notificationsEnabled,
                            onCheckedChange = onNotifyNewSmsChange
                        )
                        SettingSwitchRow(
                            title = "Backup Status Alerts",
                            subtitle = "Notify on backup completed or failed",
                            checked = settings.notifyBackupStatus,
                            enabled = settings.notificationsEnabled,
                            onCheckedChange = onNotifyBackupStatusChange
                        )
                        SettingSwitchRow(
                            title = "Local Backup Reminder",
                            subtitle = "Periodic reminder every ${settings.backupReminderDays} days",
                            checked = settings.notifyBackupReminder,
                            enabled = settings.notificationsEnabled,
                            onCheckedChange = onNotifyBackupReminderChange
                        )
                        SettingSwitchRow(
                            title = "Notification Sound",
                            subtitle = "Play default alert sound",
                            checked = settings.notificationSoundEnabled,
                            enabled = settings.notificationsEnabled,
                            onCheckedChange = onNotificationSoundChange
                        )
                    }
                }
            }

            // Privacy & App Lock
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Privacy & App Lock", Icons.Default.Lock)
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSwitchRow(
                            title = "Hide Message Previews",
                            subtitle = "Mask message body in inbox & notifications",
                            checked = settings.privacyModeHidePreviews,
                            onCheckedChange = onPrivacyHidePreviewsChange
                        )
                        SettingSwitchRow(
                            title = "Resolve Contact Names",
                            subtitle = "Display contact names when Contacts permission is granted",
                            checked = settings.resolveContactsEnabled,
                            onCheckedChange = onResolveContactsChange
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingSwitchRow(
                            title = "PIN App Lock",
                            subtitle = if (settings.appLockEnabled) "Protected with PIN" else "Disabled",
                            checked = settings.appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) showPinDialog = true else onDisableAppLock()
                            }
                        )
                        if (settings.appLockEnabled) {
                            TextButton(onClick = { showPinDialog = true }) {
                                Text("Change Lock PIN")
                            }
                        }
                        SettingSwitchRow(
                            title = "Biometric Unlock",
                            subtitle = if (biometricSupported) "Fingerprint or face unlock"
                            else "Biometric hardware not enrolled or unavailable",
                            checked = settings.biometricLockEnabled,
                            enabled = settings.appLockEnabled && biometricSupported,
                            onCheckedChange = { enabled ->
                                if (!biometricSupported) {
                                    onNotify("Biometric hardware is unavailable on this device.")
                                } else {
                                    onBiometricChange(enabled)
                                }
                            }
                        )
                        if (settings.appLockEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Lock Timeout", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Immediate", 30 to "30s", 60 to "1m", 300 to "5m").forEach { (sec, label) ->
                                    FilterChip(
                                        selected = settings.appLockTimeoutSeconds == sec,
                                        onClick = { onTimeoutChange(sec) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Shortcuts: Backup, Telegram, Permissions, Battery, Cache, Export, About
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        SettingNavRow("Backup Settings", "Local JSON & encryption vault", Icons.Default.VisibilityOff, onOpenBackupSettings)
                        SettingNavRow("Telegram Bot Settings", "Credentials, test & history", Icons.AutoMirrored.Filled.Send, onOpenTelegramSettings)
                        SettingNavRow("Permission Manager", "Inspect & manage app permissions", Icons.Default.Security, onOpenPermissions)
                        SettingNavRow(
                            title = "Battery Optimization",
                            subtitle = if (isIgnoringBatteryOpt) "Unrestricted background sync" else "System battery optimization active",
                            icon = Icons.Default.BatteryChargingFull,
                            onClick = {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }.onFailure {
                                    val fallback = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(fallback)
                                }
                            }
                        )
                        SettingNavRow("Clear Local SMS Cache", "Remove cached index (keeps device SMS)", Icons.Default.CleaningServices, onClearCache)
                        SettingNavRow("Export App Settings", "Save non-sensitive preferences JSON", Icons.Default.FileDownload) {
                            exportSettingsLauncher.launch("afx_sms_settings.json")
                        }
                        SettingNavRow("About AFX SMS Manager", "AFX Tech • Version 1.0.0", Icons.Default.Info, onOpenAbout)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                pinInput = ""
                pinConfirmInput = ""
                showPinDialog = false
            },
            title = { Text("Set App Lock PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a 4 to 6 digit numeric PIN. Stored securely using PBKDF2-HMAC-SHA256.")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("New PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pinConfirmInput = it },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetPin(pinInput)
                        pinInput = ""
                        pinConfirmInput = ""
                        showPinDialog = false
                    },
                    enabled = pinInput.length >= 4 && pinInput == pinConfirmInput
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinInput = ""
                    pinConfirmInput = ""
                    showPinDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SettingNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
