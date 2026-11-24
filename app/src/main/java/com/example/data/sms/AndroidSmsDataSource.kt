package com.example.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.local.CachedSmsEntity
import com.example.security.KeyStoreCryptoManager
import com.example.security.SensitiveMessageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SmsReadResult {
    data class Success(val messages: List<CachedSmsEntity>) : SmsReadResult
    data object PermissionDenied : SmsReadResult
    data class Error(val message: String) : SmsReadResult
}

class AndroidSmsDataSource(private val context: Context) {

    fun hasReadSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReceiveSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultSmsApp(): Boolean {
        return runCatching {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }.getOrDefault(false)
    }

    suspend fun readDeviceSms(
        existingFavorites: Set<String>,
        resolveContacts: Boolean
    ): SmsReadResult = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission()) {
            return@withContext SmsReadResult.PermissionDenied
        }

        val contactCache = mutableMapOf<String, String>()
        val results = mutableListOf<CachedSmsEntity>()
        val canReadContacts = resolveContacts && hasReadContactsPermission()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        return@withContext runCatching {
            val selection = "${Telephony.Sms.TYPE} IN (?, ?)"
            val selectionArgs = arrayOf(
                Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                Telephony.Sms.MESSAGE_TYPE_SENT.toString()
            )
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (cursor.moveToNext()) {
                    val sysId = cursor.getLong(idIdx)
                    val threadId = cursor.getLong(threadIdx)
                    val address = cursor.getString(addressIdx).orEmpty().ifBlank { "Unknown" }
                    val body = cursor.getString(bodyIdx).orEmpty()
                    val timestamp = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    val isRead = cursor.getInt(readIdx) == 1

                    val displayName = if (canReadContacts && address != "Unknown") {
                        contactCache.getOrPut(address) {
                            lookupContactName(address) ?: address
                        }
                    } else {
                        address
                    }

                    val bodyHash = computeBodyHash(address, timestamp, type, body)
                    val uniqueKey = "sys_${sysId}_${bodyHash.take(12)}"
                    val isSensitive = SensitiveMessageFilter.isSensitive(displayName, body)

                    results.add(
                        CachedSmsEntity(
                            uniqueKey = uniqueKey,
                            systemSmsId = sysId,
                            threadId = threadId,
                            address = address,
                            displayName = displayName,
                            body = body,
                            timestamp = timestamp,
                            type = type,
                            isRead = isRead,
                            isFavorite = existingFavorites.contains(uniqueKey),
                            isRestoredOnly = false,
                            isSensitive = isSensitive,
                            bodyHash = bodyHash
                        )
                    )
                }
            }
            SmsReadResult.Success(results)
        }.getOrElse { error ->
            SmsReadResult.Error(error.localizedMessage ?: "Failed to query device SMS inbox.")
        }
    }

    /**
     * Deletes an SMS from the Android system SMS provider only when this app is the active
     * Default SMS App (required by Android OS). Returns false if OS restricts deletion.
     */
    suspend fun deleteFromSystemProviderIfPermitted(systemSmsId: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!isDefaultSmsApp() || systemSmsId <= 0L) return@withContext false
            runCatching {
                val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, systemSmsId.toString())
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
        }

    private fun lookupContactName(phoneNumber: String): String? {
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        }.getOrNull()
    }

    companion object {
        fun computeBodyHash(address: String, timestamp: Long, type: Int, body: String): String {
            val normalizedAddress = address.trim().lowercase()
            return KeyStoreCryptoManager.sha256Hex("$normalizedAddress|$timestamp|$type|$body")
        }
    }
}
