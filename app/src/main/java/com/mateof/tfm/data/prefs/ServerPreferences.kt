package com.mateof.tfm.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mateof.tfm.data.model.RefreshChannelRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "server_prefs")

data class ServerConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val configured: Boolean = false
) {
    /** Base url guaranteed to end without trailing slash, e.g. `http://host:5257`. */
    val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')
}

/**
 * Which app plays video files. Any other value is the package name of an
 * installed player (VLC, MX Player, …).
 */
object VideoPlayerChoice {
    const val INTERNAL = "internal"
    const val ASK = "ask"
    const val SYSTEM = "system"
}

@Singleton
class ServerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val CONFIGURED = booleanPreferencesKey("configured")
        val VIDEO_PLAYER = stringPreferencesKey("video_player")
        val SCAN_DOCUMENTS = booleanPreferencesKey("scan_documents")
        val SCAN_AUDIO = booleanPreferencesKey("scan_audio")
        val SCAN_VIDEO = booleanPreferencesKey("scan_video")
        val SCAN_PHOTOS = booleanPreferencesKey("scan_photos")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val configFlow: Flow<ServerConfig> = context.dataStore.data.map { p ->
        ServerConfig(
            baseUrl = p[Keys.BASE_URL] ?: "",
            apiKey = p[Keys.API_KEY] ?: "",
            configured = p[Keys.CONFIGURED] ?: false
        )
    }

    /**
     * Hot cached config so interceptors and URL builders can read it synchronously
     * on the request path without blocking.
     */
    val config: StateFlow<ServerConfig> = configFlow.stateIn(
        scope,
        SharingStarted.Eagerly,
        runBlocking { ServerConfig() }
    )

    val current: ServerConfig get() = config.value

    val videoPlayer: StateFlow<String> = context.dataStore.data
        .map { it[Keys.VIDEO_PLAYER] ?: VideoPlayerChoice.INTERNAL }
        .stateIn(scope, SharingStarted.Eagerly, VideoPlayerChoice.INTERNAL)

    suspend fun save(baseUrl: String, apiKey: String) {
        context.dataStore.edit { p ->
            p[Keys.BASE_URL] = baseUrl.trimEnd('/')
            p[Keys.API_KEY] = apiKey
            p[Keys.CONFIGURED] = true
        }
    }

    suspend fun saveVideoPlayer(value: String) {
        context.dataStore.edit { p -> p[Keys.VIDEO_PLAYER] = value }
    }

    /**
     * Media types picked the last time a channel was scanned, so the dialog
     * opens with the user's usual choice. `force` is never remembered: a full
     * rescan has to be asked for explicitly every time.
     */
    val scanOptions: StateFlow<RefreshChannelRequest> = context.dataStore.data
        .map { p ->
            RefreshChannelRequest(
                includeDocuments = p[Keys.SCAN_DOCUMENTS] ?: true,
                includeAudio = p[Keys.SCAN_AUDIO] ?: true,
                includeVideo = p[Keys.SCAN_VIDEO] ?: true,
                includePhotos = p[Keys.SCAN_PHOTOS] ?: true
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, RefreshChannelRequest())

    suspend fun saveScanOptions(options: RefreshChannelRequest) {
        context.dataStore.edit { p ->
            p[Keys.SCAN_DOCUMENTS] = options.includeDocuments
            p[Keys.SCAN_AUDIO] = options.includeAudio
            p[Keys.SCAN_VIDEO] = options.includeVideo
            p[Keys.SCAN_PHOTOS] = options.includePhotos
        }
    }

    suspend fun awaitLoaded(): ServerConfig = configFlow.first()
}
