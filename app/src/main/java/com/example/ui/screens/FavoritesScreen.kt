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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.CachedSmsEntity
import com.example.data.preferences.UserSettings
import com.example.ui.components.EmptyStateBannerCard
import com.example.ui.components.SmsMessageRow

@Composable
fun FavoritesScreen(
    favorites: List<CachedSmsEntity>,
    settings: UserSettings,
    onOpenMessageDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBackupFavoritesLocal: () -> Unit,
    onBrowseMessages: () -> Unit
) {
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
                        text = "Favorites",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${favorites.size} pinned messages saved locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (favorites.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onBackupFavoritesLocal,
                        modifier = Modifier.testTag("favorites_backup_btn")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back Up")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (favorites.isEmpty()) {
                    item {
                        EmptyStateBannerCard(
                            title = "No Favorites Yet",
                            description = "Tap the heart icon on any SMS message in your inbox or message details to pin important messages here.",
                            actionLabel = "Browse Messages",
                            onAction = onBrowseMessages
                        )
                    }
                } else {
                    items(favorites, key = { it.uniqueKey }) { msg ->
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
            }
        }
    }
}
