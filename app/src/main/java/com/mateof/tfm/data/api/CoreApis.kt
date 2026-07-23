package com.mateof.tfm.data.api

import com.mateof.tfm.core.ApiEnvelope
import com.mateof.tfm.data.model.AppConfigDto
import com.mateof.tfm.data.model.AuthStatusDto
import com.mateof.tfm.data.model.LoginRequest
import com.mateof.tfm.data.model.QrPasswordRequest
import com.mateof.tfm.data.model.QrSessionDto
import com.mateof.tfm.data.model.SystemInfoDto
import com.mateof.tfm.data.model.SystemMetricsDto
import com.mateof.tfm.data.model.TelegramUserDto
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SystemApi {
    @GET("api/v1/system/ping")
    suspend fun ping(): ApiEnvelope<String>

    @GET("api/v1/system/info")
    suspend fun info(): ApiEnvelope<SystemInfoDto>

    @GET("api/v1/system/metrics")
    suspend fun metrics(): ApiEnvelope<SystemMetricsDto>
}

interface AuthApi {
    @GET("api/v1/auth/status")
    suspend fun status(): ApiEnvelope<AuthStatusDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<AuthStatusDto>

    @GET("api/v1/auth/me")
    suspend fun me(): ApiEnvelope<TelegramUserDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(): ApiEnvelope<AuthStatusDto>

    @POST("api/v1/auth/qr")
    suspend fun qrStart(@Query("logoutFirst") logoutFirst: Boolean = false): ApiEnvelope<QrSessionDto>

    @GET("api/v1/auth/qr/{sessionId}")
    suspend fun qrPoll(@Path("sessionId") sessionId: String): ApiEnvelope<QrSessionDto>

    @POST("api/v1/auth/qr/{sessionId}/password")
    suspend fun qrPassword(
        @Path("sessionId") sessionId: String,
        @Body body: QrPasswordRequest
    ): ApiEnvelope<QrSessionDto>

    @DELETE("api/v1/auth/qr/{sessionId}")
    suspend fun qrCancel(@Path("sessionId") sessionId: String): ApiEnvelope<QrSessionDto>
}

interface ConfigApi {
    @GET("api/v1/config")
    suspend fun get(): ApiEnvelope<AppConfigDto>

    @PATCH("api/v1/config")
    suspend fun patch(@Body body: JsonObject): ApiEnvelope<AppConfigDto>
}
