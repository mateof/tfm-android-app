package com.mateof.tfm.ui.screens.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.mateof.tfm.core.Format
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.FilesApi
import com.mateof.tfm.data.api.PlaylistsApi
import com.mateof.tfm.data.model.PlaylistDto
import com.mateof.tfm.data.model.PlaylistTrackDto
import com.mateof.tfm.data.repo.MediaUrls
import com.mateof.tfm.playback.PlayerConnection
import com.mateof.tfm.playback.QueueTrack
import com.mateof.tfm.ui.components.EmptyState
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.FullScreenSpinnerOverlay
import com.mateof.tfm.ui.components.LoadingBox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class PlaylistDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val playlist: PlaylistDto? = null,
    val busy: Boolean = false,
    val snackbar: String? = null
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val api: PlaylistsApi,
    private val filesApi: FilesApi,
    private val mediaUrls: MediaUrls,
    private val player: PlayerConnection
) : ViewModel() {

    private val id: String = savedState.get<String>("id") ?: ""

    private val _state = MutableStateFlow(PlaylistDetailUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { apiCall { api.get(id) } }
                .onSuccess { _state.value = _state.value.copy(loading = false, playlist = it) }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                }
        }
    }

    /**
     * Builds the play queue. Local tracks already carry a `directUrl`; channel
     * tracks need their `streamUrl` resolved — those lookups run in parallel
     * (bounded) so long playlists start fast.
     */
    fun playAll(startIndex: Int = 0) {
        val playlist = _state.value.playlist ?: return
        if (playlist.tracks.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val semaphore = Semaphore(6)
            val resolved: List<QueueTrack?> = coroutineScope {
                playlist.tracks.sortedBy { it.order }.map { track ->
                    async {
                        semaphore.withPermit { resolveTrack(track) }
                    }
                }.awaitAll()
            }
            _state.value = _state.value.copy(busy = false)
            val queue = resolved.filterNotNull()
            if (queue.isEmpty()) {
                _state.value = _state.value.copy(snackbar = "No se pudo resolver ninguna pista")
                return@launch
            }
            // Map the requested index into the filtered queue as best we can.
            val target = resolved.take(startIndex + 1).count { it != null } - 1
            player.playQueue(queue, target.coerceAtLeast(0))
        }
    }

    private suspend fun resolveTrack(track: PlaylistTrackDto): QueueTrack? {
        val url: String? = if (track.isLocalFile || !track.directUrl.isNullOrBlank()) {
            mediaUrls.withKey(track.directUrl)
        } else {
            val channelId = track.channelId ?: return null
            val fileId = track.fileId ?: return null
            runCatching {
                apiCall { filesApi.get(channelId, fileId) }
            }.getOrNull()?.streamUrl?.let { mediaUrls.withKey(it) }
        }
        return url?.let {
            QueueTrack(
                url = it,
                title = track.fileName ?: "Pista",
                artist = track.channelName ?: "Playlist",
                mediaId = track.fileId ?: it
            )
        }
    }

    fun removeTrack(track: PlaylistTrackDto) {
        val fileId = track.fileId ?: return
        viewModelScope.launch {
            runCatching { apiCall { api.removeTrack(id, fileId) } }
                .onSuccess { _state.value = _state.value.copy(playlist = it) }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun moveTrack(from: Int, to: Int) {
        val playlist = _state.value.playlist ?: return
        val tracks = playlist.tracks.sortedBy { it.order }.toMutableList()
        if (from !in tracks.indices || to !in tracks.indices) return
        val item = tracks.removeAt(from)
        tracks.add(to, item)
        viewModelScope.launch {
            runCatching { apiCall { api.reorder(id, tracks.mapNotNull { it.fileId }) } }
                .onSuccess { _state.value = _state.value.copy(playlist = it) }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun downloadToServer() {
        viewModelScope.launch {
            runCatching { apiCall { api.download(id) } }
                .onSuccess {
                    _state.value = _state.value.copy(
                        snackbar = "Descarga de la playlist encolada (ver Transfers)"
                    )
                }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavHostController,
    vm: PlaylistDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.playlist?.name ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = vm::downloadToServer) {
                        Icon(Icons.Outlined.CloudDownload, "Descargar al servidor")
                    }
                }
            )
        },
        floatingActionButton = {
            if ((state.playlist?.tracks?.size ?: 0) > 0) {
                ExtendedFloatingActionButton(
                    onClick = { vm.playAll() },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("Reproducir") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox(label = "Cargando playlist…")
                state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
                state.playlist?.tracks.isNullOrEmpty() ->
                    EmptyState("Playlist vacía.\nAñade pistas desde el explorador de ficheros.")
                else -> {
                    val tracks = state.playlist!!.tracks.sortedBy { it.order }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(tracks, key = { _, t -> t.fileId ?: t.directUrl ?: t.hashCode() }) { index, track ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        track.fileName ?: "Pista",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(track.channelName ?: if (track.isLocalFile) "Local" else "")
                                            track.fileSize?.let {
                                                if (isNotEmpty()) append(" · ")
                                                append(Format.bytes(it))
                                            }
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Audiotrack,
                                        null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(
                                            onClick = { vm.moveTrack(index, index - 1) },
                                            enabled = index > 0
                                        ) {
                                            Icon(Icons.Outlined.KeyboardArrowUp, "Subir")
                                        }
                                        IconButton(
                                            onClick = { vm.moveTrack(index, index + 1) },
                                            enabled = index < tracks.lastIndex
                                        ) {
                                            Icon(Icons.Outlined.KeyboardArrowDown, "Bajar")
                                        }
                                        IconButton(onClick = { vm.removeTrack(track) }) {
                                            Icon(Icons.Outlined.Delete, "Quitar")
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { vm.playAll(startIndex = index) }
                            )
                        }
                        item { Spacer(Modifier.height(88.dp)) }
                    }
                }
            }
        }
    }

    FullScreenSpinnerOverlay(visible = state.busy, label = "Preparando la cola…")
}
