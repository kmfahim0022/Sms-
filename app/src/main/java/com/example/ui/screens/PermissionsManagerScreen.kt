package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.PermissionSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsManagerScreen(
    permissions: PermissionSnapshot,
    onBack: () -> Unit,
    onRequestReadSms: () -> Unit,
    onRequestReceiveSms: () -> Unit,
    onRequestReadContacts: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            TopAppBar(
                title = { Text("Permissions Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("permissions_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Least-Privilege Privacy Guarantee", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "AFX SMS Manager requests permissions only when you activate the corresponding feature. It never requests camera, microphone, location, accessibility, call-log interception, or broad storage permissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    PermissionItemCard(
                        title = stringResource(R.string.perm_read_sms_title),
                        description = stringResource(R.string.perm_read_sms_rationale),
                        granted = permissions.readSmsGranted,
                        onRequest = onRequestReadSms
                    )
                }

                item {
                    PermissionItemCard(
                        title = stringResource(R.string.perm_receive_sms_title),
                        description = stringResource(R.string.perm_receive_sms_rationale),
                        granted = permissions.receiveSmsGranted,
                        onRequest = onRequestReceiveSms
                    )
                }

                item {
                    PermissionItemCard(
                        title = stringResource(R.string.perm_read_contacts_title),
                        description = stringResource(R.string.perm_read_contacts_rationale),
                        granted = permissions.readContactsGranted,
                        onRequest = onRequestReadContacts
                    )
                }

                item {
                    PermissionItemCard(
                        title = stringResource(R.string.perm_notifications_title),
                        description = stringResource(R.string.perm_notifications_rationale),
                        granted = permissions.notificationsGranted,
                        onRequest = onRequestNotifications
                    )
                }

                item {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Android System App Permissions")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(
                    label = if (granted) "Granted" else "Not Granted",
                    icon = if (granted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    tint = if (granted) EmeraldSuccess else AmberWarning
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onRequest) {
                    Text("Allow Permission")
                }
            }
        }
    }
}
