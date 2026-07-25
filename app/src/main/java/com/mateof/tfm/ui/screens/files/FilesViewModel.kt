package com.mateof.tfm.ui.screens.files

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfm.core.ApiException
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.apiCallNullable
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
import com.mateof.tfm.data.model.RefreshChannelRequest
import com.mateof.tfm.data.model.RenameFileRequest
import com.mateof.tfm.data.model.StartDownloadsRequest
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.data.repo.MediaUrls
import com.mateof.tfm.playback.PlayerConnection
import com.mateof.tfm.playback.QueueTrack
import com.mateof.tfm.util.DeviceDownloader
import com.mateof.tfm.util.ExternalOpener
import com.mateof.tfm.util.Uploads
import com.mateof.tfm.util.VideoPlayers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    val needsIndex: Boolean = false,
    // A background scan of the channel is running on the server (started from
    // here, from another client or from the web).
    val scanning: Boolean = false,
    // Media types picked the last time the user scanned a channel.
    val scanOptions: RefreshChannelRequest = RefreshChannelRequest()
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
    private val externalOpener: ExternalOpener,
    private val videoPlayers: VideoPlayers,
    private val prefs: ServerPreferences,
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
    private var scanJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            prefs.scanOptions.collect { options ->
                _state.value = _state.value.copy(scanOptions = options)
            }
        }
        // A scan may already be running (started from the web or another
        // screen); pick it up so the banner and the auto-reload work.
        watchScan(announceStart = false)
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

    /**
     * Indexes the channel: makes sure the local index exists and asks the
     * server to scan Telegram for the selected media types. The scan runs in
     * the background, so we poll its state and reload as it progresses.
     */
    fun scanChannel(options: RefreshChannelRequest = _state.value.scanOptions) {
        viewModelScope.launch {
            val creating = _state.value.needsIndex
            _state.value = _state.value.copy(busy = true)
            prefs.saveScanOptions(options)
            if (creating) {
                // Ignore "already exists" — we just want to guarantee the index.
                runCatching { apiCall { channelsApi.createDatabase(channelId) } }
            }
            runCatching { apiCallNullable { channelsApi.refresh(channelId, options) } }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        needsIndex = false,
                        scanning = true,
                        snackbar = if (creating) {
                            "Índice creado; escaneando el canal en segundo plano"
                        } else {
                            "Escaneando el canal en segundo plano"
                        }
                    )
                    watchScan(announceStart = true)
                    load()
                }
                .onFailure { e ->
                    // The server refuses a second scan while one is running;
                    // that is not an error for us, just follow the running one.
                    val running = (e as? ApiException)?.code == "already_running"
                    _state.value = _state.value.copy(
                        busy = false,
                        needsIndex = _state.value.needsIndex && !running,
                        scanning = running,
                        snackbar = if (running) "Ya hay un escaneo en curso" else e.userMessage()
                    )
                    if (running) watchScan(announceStart = true)
                }
        }
    }

    /**
     * Polls the channel's refresh state until the background scan finishes and
     * reloads the listing meanwhile, so newly indexed files show up.
     *
     * With [announceStart] false it stays quiet unless the server reports a
     * scan already in flight, which is what makes an ongoing web-side refresh
     * visible when the screen opens.
     */
    private fun watchScan(announceStart: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            var sawRunning = false
            var failures = 0
            var polls = 0
            var finished = false
            while (polls < MAX_SCAN_POLLS) {
                delay(SCAN_POLL_MS)
                polls++
                val running = runCatching { apiCall { channelsApi.isRefreshing(channelId) } }
                    .getOrNull()
                if (running == null) {
                    // Transient network hiccups are common on a long scan;
                    // only stop watching after several in a row.
                    if (++failures >= MAX_SCAN_FAILURES) break
                    continue
                }
                failures = 0
                if (running) {
                    sawRunning = true
                    if (!_state.value.scanning) {
                        _state.value = _state.value.copy(scanning = true)
                    }
                    // Show what has been indexed so far without user action.
                    if (polls % RELOAD_EVERY_POLLS == 0) load()
                } else {
                    // Nothing running and nothing was: the screen just opened
                    // on an idle channel, so say nothing at all.
                    if (!sawRunning && !announceStart) return@launch
                    finished = true
                    break
                }
            }
            _state.value = _state.value.copy(
                scanning = false,
                needsIndex = if (finished) false else _state.value.needsIndex,
                snackbar = if (finished) "Escaneo del canal terminado" else _state.value.snackbar
            )
            if (finished) load()
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

    fun openWithSystem(file: ApiFileDto) {
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val result = externalOpener.open(
                url = file.downloadUrl ?: file.streamUrl,
                fileName = file.name,
                extensionHint = file.type
            )
            _state.value = _state.value.copy(busy = false)
            when (result) {
                ExternalOpener.Result.Success -> Unit
                ExternalOpener.Result.NoUrl -> notify("Este elemento no tiene URL de descarga")
                ExternalOpener.Result.NoAppFound -> notify("No hay ninguna app que pueda abrir este tipo de fichero")
                is ExternalOpener.Result.Failed -> notify("No se pudo abrir: ${result.message}")
            }
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
                when {
                    url == null -> PlayAction.None
                    videoPlayers.launchExternal(url, file.name) -> PlayAction.HandedOff
                    else -> PlayAction.OpenVideo(url, file.name)
                }
            }
            "photo" -> {
                val url = mediaUrls.withKey(file.streamUrl ?: file.downloadUrl)
                if (url != null) PlayAction.OpenImage(url, file.name) else PlayAction.None
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

    private companion object {
        /** How often the background scan state is polled. */
        const val SCAN_POLL_MS = 4_000L

        /** Reload the listing every N polls while the scan runs (~30 s). */
        const val RELOAD_EVERY_POLLS = 8

        /** Give up watching after ~2 hours; a scan that long is stuck anyway. */
        const val MAX_SCAN_POLLS = 1_800

        /** Consecutive polling errors (server down, no network) before giving up. */
        const val MAX_SCAN_FAILURES = 3
    }
}

sealed interface PlayAction {
    data object None : PlayAction
    data object AudioStarted : PlayAction

    /** Playback already started elsewhere (external video app). */
    data object HandedOff : PlayAction
    data class OpenVideo(val url: String, val title: String) : PlayAction
    data class OpenImage(val url: String, val title: String) : PlayAction
}
