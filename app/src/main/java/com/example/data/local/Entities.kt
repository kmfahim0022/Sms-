package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_sms",
    indices = [
        Index(value = ["sender"]),
        Index(value = ["timestamp"]),
        Index(value = ["bodyHash"]),
        Index(value = ["isFavorite"])
    ]
)
data class CachedSmsEntity(
    @PrimaryKey val uniqueKey: String,
    val systemSmsId: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    @ColumnInfo(name = "sender") val sender: String = displayName,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** 1 = Inbox (received), 2 = Sent */
    val type: Int,
    val isRead: Boolean,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean = false,
    val isRestoredOnly: Boolean = false,
    val isSensitive: Boolean = false,
    val bodyHash: String
)

@Entity(tableName = "backup_history")
data class BackupHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** LOCAL_JSON, LOCAL_ENCRYPTED, TELEGRAM_TEXT, TELEGRAM_FILE */
    val destination: String,
    val messageCount: Int,
    val excludedSensitiveCount: Int,
    /** SUCCESS, FAILED */
    val status: String,
    val fileName: String? = null,
    val errorMessage: String? = null
)
