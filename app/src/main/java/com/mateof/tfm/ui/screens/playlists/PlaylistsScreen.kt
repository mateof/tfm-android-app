package com.mateof.tfm.ui.screens.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryMusic
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.mateof.tfm.core.Format
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.PlaylistsApi
import com.mateof.tfm.data.model.CreatePlaylistRequest
import com.mateof.tfm.data.model.PlaylistDto
import com.mateof.tfm.ui.components.ConfirmDialog
import com.mateof.tfm.ui.components.EmptyState
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.InputDialog
import com.mateof.tfm.ui.components.LoadingBox
import com.mateof.tfm.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val playlists: List<PlaylistDto> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val api: PlaylistsApi
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistsUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { apiCall { api.list(sortBy = "name") } }
                .onSuccess { _state.value = _state.value.copy(loading = false, playlists = it) }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                }
        }
    }

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { apiCall { api.create(CreatePlaylistRequest(name)) } }
                .onSuccess { load() }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun delete(playlist: PlaylistDto) {
        viewModelScope.launch {
            runCatching { apiCall { api.delete(playlist.id) } }
                .onSuccess { load() }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(navController: NavHostController, vm: PlaylistsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteFor by remember { mutableStateOf<PlaylistDto?>(null) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    // Reload when returning from the detail screen.
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Playlists") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Playlist") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox(label = "Cargando playlists…")
                state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
                state.playlists.isEmpty() -> EmptyState("No hay playlists todavía")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.playlists, key = { it.id }) { pl ->
                        ListItem(
                            headlineContent = { Text(pl.name) },
                            supportingContent = {
                                Text(
                                    "${pl.trackCount} pistas · ${Format.date(pl.dateModified)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.LibraryMusic,
                                    null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { deleteFor = pl }) {
                                    Icon(Icons.Outlined.Delete, "Eliminar")
                                }
                            },
                            modifier = Modifier.clickable {
                                navController.navigate(Routes.playlist(pl.id))
                            }
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showCreate) {
        InputDialog(
            title = "Nueva playlist",
            label = "Nombre",
            confirmLabel = "Crear",
            onConfirm = vm::create,
            onDismiss = { showCreate = false }
        )
    }

    deleteFor?.let { pl ->
        ConfirmDialog(
            title = "Eliminar «${pl.name}»",
            text = "La playlist se eliminará del servidor.",
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = { vm.delete(pl) },
            onDismiss = { deleteFor = null }
        )
    }
}
