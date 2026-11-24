package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM cached_sms ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<CachedSmsEntity>>

    @Query("SELECT * FROM cached_sms WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun observeFavoriteMessages(): Flow<List<CachedSmsEntity>>

    @Query("SELECT * FROM cached_sms WHERE uniqueKey = :uniqueKey LIMIT 1")
    fun observeMessageByKey(uniqueKey: String): Flow<CachedSmsEntity?>

    @Query("SELECT * FROM cached_sms WHERE uniqueKey = :uniqueKey LIMIT 1")
    suspend fun getMessageByKey(uniqueKey: String): CachedSmsEntity?

    @Query("SELECT uniqueKey FROM cached_sms WHERE isFavorite = 1")
    suspend fun getFavoriteKeys(): List<String>

    @Query("SELECT bodyHash FROM cached_sms")
    suspend fun getAllBodyHashes(): List<String>

    @Query("SELECT * FROM cached_sms ORDER BY timestamp DESC")
    suspend fun getAllMessagesSnapshot(): List<CachedSmsEntity>

    @Query("SELECT COUNT(*) FROM cached_sms")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_sms WHERE type = 1")
    fun observeInboxCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_sms WHERE isRead = 0 AND type = 1")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<CachedSmsEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(messages: List<CachedSmsEntity>)

    @Query("UPDATE cached_sms SET isFavorite = :isFavorite WHERE uniqueKey = :uniqueKey")
    suspend fun setFavorite(uniqueKey: String, isFavorite: Boolean)

    @Query("UPDATE cached_sms SET isRead = 1 WHERE uniqueKey = :uniqueKey")
    suspend fun markAsReadLocal(uniqueKey: String)

    @Query("DELETE FROM cached_sms WHERE uniqueKey = :uniqueKey")
    suspend fun deleteByKey(uniqueKey: String)

    @Query("DELETE FROM cached_sms WHERE isRestoredOnly = 0")
    suspend fun clearNonRestoredCache()

    @Query("DELETE FROM cached_sms")
    suspend fun clearAll()
}

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC")
    fun observeAllHistory(): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history WHERE destination LIKE 'TELEGRAM_%' ORDER BY timestamp DESC")
    fun observeTelegramHistory(): Flow<List<BackupHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BackupHistoryEntity): Long

    @Query("DELETE FROM backup_history")
    suspend fun clearHistory()
}
