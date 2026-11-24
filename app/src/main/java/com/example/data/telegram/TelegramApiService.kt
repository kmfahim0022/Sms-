package com.example.data.telegram

import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class TelegramBotUser(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String,
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramGetMeResponse(
    val ok: Boolean,
    val result: TelegramBotUser? = null,
    val description: String? = null,
    val error_code: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramBasicResponse(
    val ok: Boolean,
    val description: String? = null,
    val error_code: Int? = null
)

interface TelegramApiService {

    @GET("bot{token}/getMe")
    suspend fun getMe(
        @Path(value = "token", encoded = true) token: String
    ): Response<TelegramGetMeResponse>

    @FormUrlEncoded
    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path(value = "token", encoded = true) token: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String
    ): Response<TelegramBasicResponse>

    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("caption") caption: RequestBody,
        @Part document: MultipartBody.Part
    ): Response<TelegramBasicResponse>
}
