package com.mateof.tfm.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mateof.tfm.data.api.AuthApi
import com.mateof.tfm.data.api.ChannelsApi
import com.mateof.tfm.data.api.ConfigApi
import com.mateof.tfm.data.api.FilesApi
import com.mateof.tfm.data.api.LocalApi
import com.mateof.tfm.data.api.PlaylistsApi
import com.mateof.tfm.data.api.SystemApi
import com.mateof.tfm.data.api.TransfersApi
import com.mateof.tfm.data.net.ApiKeyInterceptor
import com.mateof.tfm.data.net.HostSelectionInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        hostSelection: HostSelectionInterceptor,
        apiKey: ApiKeyInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(hostSelection)
        .addInterceptor(apiKey)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Multipart uploads of big files can take a long while.
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Placeholder; HostSelectionInterceptor rewrites every request
        // to the configured server.
        .baseUrl("http://placeholder.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun provideSystemApi(r: Retrofit): SystemApi = r.create(SystemApi::class.java)
    @Provides @Singleton fun provideAuthApi(r: Retrofit): AuthApi = r.create(AuthApi::class.java)
    @Provides @Singleton fun provideConfigApi(r: Retrofit): ConfigApi = r.create(ConfigApi::class.java)
    @Provides @Singleton fun provideChannelsApi(r: Retrofit): ChannelsApi = r.create(ChannelsApi::class.java)
    @Provides @Singleton fun provideFilesApi(r: Retrofit): FilesApi = r.create(FilesApi::class.java)
    @Provides @Singleton fun provideTransfersApi(r: Retrofit): TransfersApi = r.create(TransfersApi::class.java)
    @Provides @Singleton fun provideLocalApi(r: Retrofit): LocalApi = r.create(LocalApi::class.java)
    @Provides @Singleton fun providePlaylistsApi(r: Retrofit): PlaylistsApi = r.create(PlaylistsApi::class.java)
}
