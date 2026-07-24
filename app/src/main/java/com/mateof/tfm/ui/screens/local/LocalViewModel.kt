package com.mateof.tfm.ui.screens.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.apiCallPaged
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.ChannelsApi
import com.mateof.tfm.data.api.LocalApi
import com.mateof.tfm.data.api.TransfersApi
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.model.ChannelFoldersDto
import com.mateof.tfm.data.model.CreateFolderRequest
import com.mateof.tfm.data.model.FolderContentsDto
import com.mateof.tfm.data.model.LocalDeleteRequest
import com.mateof.tfm.data.model.LocalRenameRequest
import com.mateof.tfm.data.model.StartUploadsRequest
import com.mateof.tfm.data.repo.MediaUrls
import com.mateof.tfm.playback.PlayerConnection
import com.mateof.tfm.playback.QueueTrack
import com.mateof.tfm.ui.screens.files.PlayAction
import com.mateof.tfm.util.DeviceDownloader
import com.mateof.tfm.util.Uploads
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class LocalUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val contents: FolderContentsDto? = null,
    val items: List<ApiFileDto> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val filter: String = "all",
    val path: String = "",
    val selection: Set<String> = emptySet(),
    val busy: Boolean = false,
    val snackbar: String? = null,
    val savedChannels: List<ChannelDto>? = null,
    val channelPickerFolders: ChannelFoldersDto? = null,
    val channelPickerSearch: String = ""
)

@HiltViewModel
class LocalViewModel @Inject constructor(
    private val localApi: LocalApi,
    private val transfersApi: TransfersApi,
    private val channelsApi: ChannelsApi,
    private val mediaUrls: MediaUrls,
    private val player: PlayerConnection,
    private val downloader: DeviceDownloader,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(LocalUiState())
    val state = _state.asStateFlow()

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null

    init {
        load()
    }

    fun navigateTo(path: String) {
        _state.value = _state.value.copy(path = path.trim('/'), selection = emptySet())
        load()
    }

    fun navigateUp(): Boolean {
        val p = _state.value.path
        if (p.isBlank()) return false
        val parent = p.substringBeforeLast('/', "")
        navigateTo(parent)
        return true
    }

    fun load(more: Boolean = false) {
        val s = _state.value
        val page = if (more) s.page + 1 else 1
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = s.copy(loading = !more, loadingMore = more, error = null)
            runCatching {
                apiCallPaged {
                    localApi.browse(
                        path = s.path.ifBlank { null },
                        filter = s.filter.takeIf { it != "all" },
                        page = page
                    )
                }
            }.onSuccess { paged ->
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    contents = paged.items,
                    items = if (more) _state.value.items + paged.items.items else paged.items.items,
                    page = page,
                    hasNext = paged.page?.hasNext == true
                )
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                _state.value = _state.value.copy(
                    loading = false, loadingMore = false, error = e.userMessage()
                )
            }
        }
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter)
        load()
    }

    fun toggleSelection(file: ApiFileDto) {
        val sel = _state.value.selection.toMutableSet()
        if (!sel.remove(file.id)) sel.add(file.id)
        _state.value = _state.value.copy(selection = sel)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = emptySet())
    }

    // --------------------------------------------------------------- actions

    fun delete(paths: List<String>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { localApi.delete(LocalDeleteRequest(paths)) } }
                .onSuccess { r ->
                    _state.value = _state.value.copy(busy = false)
                    notify("${r.accepted ?: 0} elementos eliminados")
                    clearSelection()
                    load()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false)
                    notify(e.userMessage())
                }
        }
    }

    fun rename(file: ApiFileDto, newName: String) {
        viewModelScope.launch {
            runCatching { apiCall { localApi.rename(LocalRenameRequest(file.id, newName)) } }
                .onSuccess { load() }
                .onFailure { e -> notify(e.userMessage()) }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            runCatching {
                apiCall { localApi.createFolder(CreateFolderRequest(_state.value.path, name)) }
            }.onSuccess { load() }
                .onFailure { e -> notify(e.userMessage()) }
        }
    }

    fun downloadToDevice(file: ApiFileDto) {
        val url = file.downloadUrl ?: return notify("Sin URL de descarga")
        if (downloader.download(url, file.name)) {
            notify("Descargando «${file.name}» en el dispositivo")
        }
    }

    fun uploadFromDevice(uris: List<Uri>) {
        if (uris.isEmpty()) return
        notify("Subiendo ${uris.size} fichero(s) al almacenamiento del servidor…")
        val path = _state.value.path
        uris.forEach { uri ->
            uploadScope.launch {
                runCatching {
                    val picked = Uploads.describe(appContext, uri)
                    val part = Uploads.filePart(appContext, picked)
                    val pathBody = path.ifBlank { null }?.toRequestBody("text/plain".toMediaType())
                    apiCall { localApi.upload(part, pathBody) }
                }.onSuccess { notify("Subida completada") }
                    .onFailure { e -> notify("Error subiendo: ${e.userMessage()}") }
            }
        }
    }

    // ------------------------------------------------------ upload to channel

    /**
     * Loads the indexed channels + their Telegram folder mapping in parallel so
     * the upload picker can render them grouped, matching the main screen.
     */
    fun loadSavedChannels() {
        _state.value = _state.value.copy(channelPickerSearch = "")
        viewModelScope.launch {
            val savedDeferred = async {
                runCatching {
                    apiCall { channelsApi.list(onlySaved = true, pageSize = 200) }
                }
            }
            val foldersDeferred = async {
                runCatching { apiCall { channelsApi.folders() } }
            }
            val (saved, folders) = awaitAll(savedDeferred, foldersDeferred)
            @Suppress("UNCHECKED_CAST")
            val savedResult = saved as Result<List<ChannelDto>>
            @Suppress("UNCHECKED_CAST")
            val foldersResult = folders as Result<ChannelFoldersDto>

            savedResult.onSuccess { list ->
                _state.value = _state.value.copy(savedChannels = list)
            }.onFailure { e -> notify(e.userMessage()) }

            // Missing folders isn't fatal: the picker degrades to a flat list.
            foldersResult.onSuccess { data ->
                _state.value = _state.value.copy(channelPickerFolders = data)
            }
        }
    }

    fun setChannelPickerSearch(text: String) {
        _state.value = _state.value.copy(channelPickerSearch = text)
    }

    fun uploadToChannel(paths: List<String>, channel: ChannelDto, targetPath: String) {
        viewModelScope.launch {
            runCatching {
                apiCall {
                    transfersApi.startUploads(
                        StartUploadsRequest(
                            channelId = channel.id.toString(),
                            localPaths = paths,
                            targetPath = targetPath
                        )
                    )
                }
            }.onSuccess {
                val where = if (targetPath == "/") "raíz" else targetPath
                notify("Subida a «${channel.name}» ($where) encolada (ver Transfers)")
                clearSelection()
            }.onFailure { e -> notify(e.userMessage()) }
        }
    }

    // -------------------------------------------------------------- playback

    fun play(file: ApiFileDto): PlayAction {
        return when (file.category?.lowercase()) {
            "audio" -> {
                val audios = _state.value.items.filter {
                    it.isFile && it.category.equals("Audio", true) && it.streamUrl != null
                }
                val index = audios.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                player.playQueue(
                    audios.map {
                        QueueTrack(
                            url = mediaUrls.withKey(it.streamUrl)!!,
                            title = it.name,
                            artist = "Local",
                            mediaId = it.id
                        )
                    },
                    index
                )
                PlayAction.AudioStarted
            }
            "video" -> {
                val url = mediaUrls.withKey(file.streamUrl)
                if (url != null) PlayAction.OpenVideo(url, file.name) else PlayAction.None
            }
            else -> PlayAction.None
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { localApi.clearCache() } }
                .onSuccess {
                    _state.value = _state.value.copy(busy = false)
                    notify("Caché de streaming vaciada")
                    load()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false)
                    notify(e.userMessage())
                }
        }
    }

    private fun notify(message: String) {
        _state.value = _state.value.copy(snackbar = message)
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}
