package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object MessagesRoute

@Serializable
data object FavoritesRoute

@Serializable
data object BackupRoute

@Serializable
data object SettingsRoute

@Serializable
data class SmsDetailRoute(val uniqueKey: String)

@Serializable
data object TelegramBackupRoute

@Serializable
data object PermissionsManagerRoute

@Serializable
data object AboutRoute
