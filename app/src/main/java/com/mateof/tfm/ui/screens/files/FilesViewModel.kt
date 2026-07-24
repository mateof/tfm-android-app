package com.mateof.tfm.ui.screens.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.apiCallPaged
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.FilesApi
import com.mateof.tfm.data.api.PlaylistsApi
import com.mateof.tfm.data.api.SharesApi
import com.mateof.tfm.data.api.TransfersApi
import com.mateof.tfm.data.model.AddTrackRequest
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.data.model.CopyMoveRequest
import com.mateof.tfm.data.model.CreateFolderRequest
import com.mateof.tfm.data.model.CreateStrmRequest
import com.mateof.tfm.data.model.FolderContentsDto
import com.mateof.tfm.data.model.IdsRequest
import com.mateof.tfm.data.model.PlaylistDto
import com.mateof.tfm.data.model.RenameFileRequest
import com.mateof.tfm.data.model.StartDownloadsRequest
import com.mateof.tfm.data.repo.MediaUrls
import com.mateof.tfm.playback.PlayerConnection
import com.mateof.tfm.playback.QueueTrack
import com.mateof.tfm.util.DeviceDownloader
import com.mateof.tfm.util.Uploads
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class FilesUiState(
    val channelId: String = "",
    val channelName: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val contents: FolderContentsDto? = null,
    val items: List<ApiFileDto> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val filter: String = "all",
    val sortBy: String = "name",
    val sortDescending: Boolean = false,
    val search: String = "",
    val searchMode: Boolean = false,
    val selection: Set<String> = emptySet(),
    val busy: Boolean = false,
    val snackbar: String? = null,
    val playlists: List<PlaylistDto>? = null,
    // True when the channel has no local index yet, so browsing has nothing
    // to show; the screen offers to create + scan it.
    val needsIndex: Boolean = false
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val filesApi: FilesApi,
    private val channelsApi: com.mateof.tfm.data.api.ChannelsApi,
    private val transfersApi: TransfersApi,
    private val playlistsApi: PlaylistsApi,
    private val sharesApi: SharesApi,
    private val mediaUrls: MediaUrls,
    private val player: PlayerConnection,
    private val downloader: DeviceDownloader,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val channelId: String = savedState.get<String>("channelId") ?: ""
    private val channelName: String = savedState.get<String>("name") ?: ""
    private var currentPath: String = savedState.get<String>("path") ?: "/"

    private val _state = MutableStateFlow(
        FilesUiState(channelId = channelId, channelName = channelName)
    )
    val state = _state.asStateFlow()

    // Uploads should survive the screen being closed.
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var loadJob: Job? = null

    init {
        load()
    }

    val path: String get() = currentPath

    fun navigateTo(path: String) {
        currentPath = if (path.endsWith("/")) path else "$path/"
        _state.value = _state.value.copy(selection = emptySet(), searchMode = false, search = "")
        load()
    }

    fun navigateUp(): Boolean {
        val parent = _state.value.contents?.parentPath
        if (_state.value.searchMode) {
            _state.value = _state.value.copy(searchMode = false, search = "")
            load()
            return true
        }
        if (currentPath == "/" || parent == null) return false
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
                if (s.searchMode && s.search.isNotBlank()) {
                    val paged = apiCallPaged {
                        filesApi.search(
                            channelId = channelId,
                            q = s.search,
                            path = currentPath,
                            filter = s.filter.takeIf { it != "all" },
                            page = page
                        )
                    }
                    Triple(null as FolderContentsDto?, paged.items, paged.page?.hasNext == true)
                } else {
                    val paged = apiCallPaged {
                        filesApi.browse(
                            channelId = channelId,
                            path = currentPath,
                            filter = s.filter.takeIf { it != "all" },
                            sortBy = s.sortBy,
                            sortDescending = s.sortDescending,
                            page = page
                        )
                    }
                    Triple(paged.items, paged.items.items, paged.page?.hasNext == true)
                }
            }.onSuccess { (contents, items, hasNext) ->
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    needsIndex = false,
                    contents = contents ?: _state.value.contents,
                    items = if (more) _state.value.items + items else items,
                    page = page,
                    hasNext = hasNext
                )
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                // A channel with no local index answers channel_not_found /
                // not_found; surface a dedicated "create the index" state.
                val code = (e as? com.mateof.tfm.core.ApiException)?.code
                val needsIndex = currentPath == "/" && !s.searchMode &&
                    (code == "channel_not_found" || code == "not_found")
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    needsIndex = needsIndex,
                    error = if (needsIndex) null else e.userMessage()
                )
            }
        }
    }

    /** Create the channel's local index and kick off a scan, then reload. */
    fun createIndexAndScan(
        options: com.mateof.tfm.data.model.RefreshChannelRequest =
            com.mateof.tfm.data.model.RefreshChannelRequest()
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            // Ignore "already exists" — we just want to guarantee the index.
            runCatching { apiCall { channelsApi.createDatabase(channelId) } }
            runCatching { apiCall { channelsApi.refresh(channelId, options) } }
            _state.value = _state.value.copy(
                busy = false,
                needsIndex = false,
                snackbar = "Índice creado; escaneando el canal en segundo plano (ver Transfers)"
            )
            load()
        }
    }

    fun setFilter(filter: String) {
        _state.value = _state.value.copy(filter = filter)
        load()
    }

    fun setSort(sortBy: String, descending: Boolean) {
        _state.value = _state.value.copy(sortBy = sortBy, sortDescending = descending)
        load()
    }

    fun setSearch(text: String) {
        _state.value = _state.value.copy(search = text)
    }

    fun submitSearch() {
        if (_state.value.search.isBlank()) return
        _state.value = _state.value.copy(searchMode = true)
        load()
    }

    // ---------------------------------------------------------------- select

    fun toggleSelection(file: ApiFileDto) {
        val sel = _state.value.selection.toMutableSet()
        if (!sel.remove(file.id)) sel.add(file.id)
        _state.value = _state.value.copy(selection = sel)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = emptySet())
    }

    /** Selects every currently loaded item (pagination-limited). */
    fun selectAll() {
        _state.value = _state.value.copy(
            selection = _state.value.items.map { it.id }.toSet()
        )
    }

    private fun selectedIds(): List<String> = _state.value.selection.toList()

    // --------------------------------------------------------------- actions

    fun downloadToServer(ids: List<String>) {
        viewModelScope.launch {
            runCatching {
                apiCall {
                    transfersApi.startDownloads(
                        StartDownloadsRequest(channelId = channelId, fileIds = ids)
                    )
                }
            }.onSuccess { r ->
                notify("Descarga iniciada (${r.accepted ?: ids.size} elementos). Mira Transfers")
                clearSelection()
            }.onFailure { e -> notify(e.userMessage()) }
        }
    }

    fun downloadSelectionToServer() = downloadToServer(selectedIds())

    fun downloadToDevice(file: ApiFileDto) {
        if (downloader.download(file.downloadUrl, file.name)) {
            notify("Descargando «${file.name}» en el dispositivo")
        } else {
            notify("Este elemento no tiene URL de descarga")
        }
    }

    fun downloadSelectionToDevice() {
        val files = _state.value.items.filter { it.id in _state.value.selection && it.isFile }
        files.forEach { downloader.download(it.downloadUrl, it.name) }
        notify("Descargando ${files.size} ficheros en el dispositivo")
        clearSelection()
    }

    fun delete(ids: List<String>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { filesApi.delete(channelId, IdsRequest(ids)) } }
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

    fun deleteSelection() = delete(selectedIds())

    fun rename(file: ApiFileDto, newName: String) {
        viewModelScope.launch {
            runCatching { apiCall { filesApi.rename(channelId, file.id, RenameFileRequest(newName)) } }
                .onSuccess { load() }
                .onFailure { e -> notify(e.userMessage()) }
        }
    }

    fun copyOrMove(ids: List<String>, targetPath: String, move: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val body = CopyMoveRequest(ids = ids, targetPath = normalizeFolder(targetPath))
            runCatching {
                apiCall {
                    if (move) filesApi.move(channelId, body) else filesApi.copy(channelId, body)
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false)
                notify(if (move) "Movido" else "Copiado")
                clearSelection()
                load()
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false)
                notify(e.userMessage())
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            runCatching {
                apiCall { filesApi.createFolder(channelId, CreateFolderRequest(currentPath, name)) }
            }.onSuccess { load() }
                .onFailure { e -> notify(e.userMessage()) }
        }
    }

    fun uploadFromDevice(uris: List<Uri>) {
        if (uris.isEmpty()) return
        notify("Subiendo ${uris.size} fichero(s) al canal…")
        uris.forEach { uri ->
            uploadScope.launch {
                runCatching {
                    val picked = Uploads.describe(appContext, uri)
                    val part = Uploads.filePart(appContext, picked)
                    val pathBody = currentPath.toRequestBody("text/plain".toMediaType())
                    apiCall { filesApi.upload(channelId, part, pathBody) }
                }.onSuccess {
                    notify("Subida al servidor completada; enviando a Telegram (ver Transfers)")
                }.onFailure { e ->
                    notify("Error subiendo: ${e.userMessage()}")
                }
            }
        }
    }

    // ------------------------------------------------------------------ strm

    /**
     * Generates .strm files for the current folder.
     *
     * When [destinationFolder] is null, the server prepares a ZIP and returns
     * its relative URL; we hand it to the system download manager. When it is
     * set, .strm files are written under that path in the server local root.
     */
    fun exportStrm(destinationFolder: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                apiCall {
                    sharesApi.createStrm(
                        channelId = channelId,
                        body = CreateStrmRequest(
                            path = currentPath,
                            destinationFolder = destinationFolder?.ifBlank { null }
                        )
                    )
                }
            }.onSuccess { result ->
                _state.value = _state.value.copy(busy = false)
                if (destinationFolder.isNullOrBlank()) {
                    val ok = downloader.download(result, strmZipName())
                    notify(
                        if (ok) "Descargando ZIP con .strm en el dispositivo"
                        else "STRM listo, pero no se pudo iniciar la descarga (URL: $result)"
                    )
                } else {
                    notify("Ficheros .strm escritos en «$result»")
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false)
                notify(e.userMessage())
            }
        }
    }

    private fun strmZipName(): String {
        val slug = currentPath.trim('/').replace('/', '_').ifBlank { "root" }
        return "strm-${channelName.ifBlank { channelId }}-$slug.zip"
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
                            artist = channelName,
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

    // -------------------------------------------------------------- playlist

    fun loadPlaylists() {
        viewModelScope.launch {
            runCatching { apiCall { playlistsApi.list() } }
                .onSuccess { _state.value = _state.value.copy(playlists = it) }
                .onFailure { e -> notify(e.userMessage()) }
        }
    }

    fun addToPlaylist(file: ApiFileDto, playlist: PlaylistDto) {
        viewModelScope.launch {
            runCatching {
                apiCall {
                    playlistsApi.addTrack(
                        playlist.id,
                        AddTrackRequest(
                            fileId = file.id,
                            channelId = channelId,
                            channelName = channelName,
                            fileName = file.name,
                            filePath = file.path,
                            fileType = file.type,
                            fileSize = file.size
                        )
                    )
                }
            }.onSuccess { notify("Añadido a «${playlist.name}»") }
                .onFailure { e -> notify(e.userMessage()) }
        }
    }

    // ------------------------------------------------------------------ misc

    private fun normalizeFolder(path: String): String {
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        return p
    }

    private fun notify(message: String) {
        _state.value = _state.value.copy(snackbar = message)
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}

sealed interface PlayAction {
    data object None : PlayAction
    data object AudioStarted : PlayAction
    data class OpenVideo(val url: String, val title: String) : PlayAction
}
