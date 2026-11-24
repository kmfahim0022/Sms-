package com.example.data.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.CachedSmsEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface TelegramResult<out T> {
    data class Success<T>(val data: T) : TelegramResult<T>
    data class Error(val message: String, val code: Int? = null) : TelegramResult<Nothing>
}

class TelegramBackupManager(private val context: Context) {

    // NOTE: No HTTP body logging interceptor is attached so bot tokens and SMS bodies are never logged.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val api: TelegramApiService = Retrofit.Builder()
        .baseUrl("https://api.telegram.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TelegramApiService::class.java)

    fun validateBotTokenFormat(token: String): Boolean {
        val trimmed = token.trim()
        return Regex("^\\d{6,12}:[A-Za-z0-9_-]{25,}$").matches(trimmed)
    }

    fun validateChatIdFormat(chatId: String): Boolean {
        val trimmed = chatId.trim()
        return Regex("^-?\\d{5,16}$").matches(trimmed) || Regex("^@[A-Za-z0-9_]{5,}$").matches(trimmed)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun testConnection(token: String, chatId: String): TelegramResult<String> =
        withContext(Dispatchers.IO) {
            val cleanToken = token.trim()
            val cleanChatId = chatId.trim()

            if (!validateBotTokenFormat(cleanToken)) {
                return@withContext TelegramResult.Error("Invalid bot token format. Expected format: 123456789:ABCdef...")
            }
            if (!validateChatIdFormat(cleanChatId)) {
                return@withContext TelegramResult.Error("Invalid Chat ID. Use a numeric Chat ID (e.g. 123456789) or @channel_username.")
            }
            if (!isNetworkAvailable()) {
                return@withContext TelegramResult.Error("No internet connection available.")
            }

            return@withContext try {
                val meResp = api.getMe(cleanToken)
                if (!meResp.isSuccessful || meResp.body()?.ok != true) {
                    return@withContext mapHttpError(meResp.code(), meResp.body()?.description, "Invalid bot token or unauthorized.")
                }
                val botName = meResp.body()?.result?.username?.let { "@$it" }
                    ?: meResp.body()?.result?.first_name
                    ?: "Telegram Bot"

                val verifyMsg = api.sendMessage(
                    token = cleanToken,
                    chatId = cleanChatId,
                    text = "AFX SMS Manager connected to $botName. User-initiated backups are ready."
                )
                if (!verifyMsg.isSuccessful || verifyMsg.body()?.ok != true) {
                    return@withContext mapHttpError(
                        verifyMsg.code(),
                        verifyMsg.body()?.description,
                        "Bot token is valid, but Chat ID could not be reached. Ensure you started a chat with the bot first."
                    )
                }
                TelegramResult.Success(botName)
            } catch (io: IOException) {
                TelegramResult.Error("Network error while connecting to Telegram. Check internet access and try again.")
            } catch (e: Exception) {
                TelegramResult.Error(e.localizedMessage ?: "Telegram connection failed.")
            }
        }

    suspend fun sendSelectedMessagesAsText(
        token: String,
        chatId: String,
        messages: List<CachedSmsEntity>
    ): TelegramResult<Int> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext TelegramResult.Error("No internet connection available.")
        }
        if (messages.isEmpty()) {
            return@withContext TelegramResult.Error("No eligible messages selected.")
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val chunks = mutableListOf<String>()
        val current = StringBuilder("AFX SMS Manager — User Backup (${messages.size} messages)\n\n")

        for (msg in messages) {
            val direction = if (msg.type == 2) "SENT" else "INBOX"
            val entry = buildString {
                append("[$direction] ${msg.displayName} (${msg.address})\n")
                append("Time: ${dateFormat.format(Date(msg.timestamp))}\n")
                append("${msg.body}\n")
                append("──────────────\n")
            }
            if (current.length + entry.length > 3500) {
                chunks.add(current.toString())
                current.clear()
            }
            current.append(entry)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())

        return@withContext try {
            for (chunk in chunks) {
                val resp = api.sendMessage(token.trim(), chatId.trim(), chunk)
                if (!resp.isSuccessful || resp.body()?.ok != true) {
                    return@withContext mapHttpError(resp.code(), resp.body()?.description, "Failed to send backup message.")
                }
            }
            TelegramResult.Success(messages.size)
        } catch (io: IOException) {
            TelegramResult.Error("Network interruption while sending backup to Telegram.")
        } catch (e: Exception) {
            TelegramResult.Error(e.localizedMessage ?: "Telegram backup failed.")
        }
    }

    suspend fun sendBackupDocument(
        token: String,
        chatId: String,
        fileName: String,
        fileContent: String,
        messageCount: Int
    ): TelegramResult<String> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext TelegramResult.Error("No internet connection available.")
        }

        return@withContext try {
            val chatIdBody = chatId.trim().toRequestBody("text/plain".toMediaType())
            val captionBody = "AFX SMS Manager Backup ($messageCount messages)".toRequestBody("text/plain".toMediaType())
            val fileBody = fileContent.toRequestBody("application/json".toMediaType())
            val part = MultipartBody.Part.createFormData("document", fileName, fileBody)

            val resp = api.sendDocument(token.trim(), chatIdBody, captionBody, part)
            if (!resp.isSuccessful || resp.body()?.ok != true) {
                return@withContext mapHttpError(resp.code(), resp.body()?.description, "Failed to upload backup file to Telegram.")
            }
            TelegramResult.Success(fileName)
        } catch (io: IOException) {
            TelegramResult.Error("Network interruption while uploading backup file.")
        } catch (e: Exception) {
            TelegramResult.Error(e.localizedMessage ?: "Telegram file backup failed.")
        }
    }

    private fun mapHttpError(httpCode: Int, apiDesc: String?, fallback: String): TelegramResult.Error {
        val friendly = when (httpCode) {
            401, 404 -> "Invalid Telegram bot token. Check your token from @BotFather."
            400 -> apiDesc ?: "Invalid Chat ID or bad request. Make sure you messaged the bot first."
            403 -> "Bot does not have permission to message this Chat ID."
            429 -> "Telegram rate limit reached. Please wait a moment and retry."
            else -> apiDesc ?: fallback
        }
        return TelegramResult.Error(friendly, httpCode)
    }
}
