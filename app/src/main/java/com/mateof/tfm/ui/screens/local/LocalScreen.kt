package com.mateof.tfm.ui.screens.local

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.mateof.tfm.core.Format
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.ui.components.ConfirmDialog
import com.mateof.tfm.ui.components.EmptyState
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.FileRow
import com.mateof.tfm.ui.components.FullScreenSpinnerOverlay
import com.mateof.tfm.ui.components.InputDialog
import com.mateof.tfm.ui.components.LoadingBox
import com.mateof.tfm.ui.nav.Routes
import com.mateof.tfm.ui.screens.files.PlayAction

private val FILTERS = listOf(
    "all" to "Todo",
    "audio" to "Audio",
    "video" to "Vídeo",
    "photo" to "Fotos",
    "document" to "Docs"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(navController: NavHostController, vm: LocalViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var actionsFor by remember { mutableStateOf<ApiFileDto?>(null) }
    var renameFor by remember { mutableStateOf<ApiFileDto?>(null) }
    var deleteFor by remember { mutableStateOf<List<String>?>(null) }
    var channelPickerFor by remember { mutableStateOf<List<String>?>(null) }
    // (paths, channel) once a target channel is chosen; drives the folder picker.
    var folderPickerFor by remember {
        mutableStateOf<Pair<List<String>, com.mateof.tfm.data.model.ChannelDto>?>(null)
    }
    var showCreateFolder by rememberSaveable { mutableStateOf(false) }
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> vm.uploadFromDevice(uris) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    BackHandler(enabled = state.selection.isNotEmpty()) { vm.clearSelection() }
    BackHandler(enabled = state.selection.isEmpty() && state.path.isNotBlank()) {
        vm.navigateUp()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selection.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${state.selection.size} seleccionados") },
                    navigationIcon = {
                        IconButton(onClick = vm::clearSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar selección")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            vm.loadSavedChannels()
                            channelPickerFor = state.selection.toList()
                        }) {
                            Icon(Icons.Outlined.CloudUpload, "Subir a canal")
                        }
                        IconButton(onClick = { deleteFor = state.selection.toList() }) {
                            Icon(Icons.Outlined.Delete, "Eliminar")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Servidor · Local", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "/" + state.path,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.path.isNotBlank()) {
                            IconButton(onClick = { vm.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showCreateFolder = true }) {
                            Icon(Icons.Outlined.CreateNewFolder, "Nueva carpeta")
                        }
                        IconButton(onClick = { confirmClearCache = true }) {
                            Icon(Icons.Outlined.CleaningServices, "Vaciar caché streaming")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickFiles.launch("*/*") }) {
                Icon(Icons.Outlined.UploadFile, "Subir fichero")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                FILTERS.forEach { (value, label) ->
                    FilterChip(
                        selected = state.filter == value,
                        onClick = { vm.setFilter(value) },
                        label = { Text(label) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            when {
                state.loading -> LoadingBox(label = "Cargando…")
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.load() })
                state.items.isEmpty() -> EmptyState("Carpeta vacía")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { file ->
                        FileRow(
                            file = file,
                            subtitle = if (file.isFile) {
                                (file.sizeText ?: Format.bytes(file.size)) +
                                    (file.type?.let { "  ·  $it" } ?: "")
                            } else "Carpeta",
                            selected = file.id in state.selection,
                            onClick = {
                                when {
                                    state.selection.isNotEmpty() -> vm.toggleSelection(file)
                                    !file.isFile -> vm.navigateTo(file.id)
                                    else -> when (val action = vm.play(file)) {
                                        is PlayAction.OpenVideo -> navController.navigate(
                                            Routes.video(action.url, action.title)
                                        )
                                        PlayAction.AudioStarted -> Unit
                                        PlayAction.None -> actionsFor = file
                                    }
                                }
                            },
                            onLongClick = { vm.toggleSelection(file) },
                            trailing = {
                                IconButton(onClick = { actionsFor = file }) {
                                    Icon(Icons.Filled.MoreVert, "Acciones")
                                }
                            }
                        )
                    }
                    if (state.hasNext) {
                        item {
                            TextButton(
                                onClick = { vm.load(more = true) },
                                enabled = !state.loadingMore,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Text(if (state.loadingMore) "Cargando…" else "Cargar más")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    FullScreenSpinnerOverlay(visible = state.busy)

    actionsFor?.let { file ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Subir a un canal de Telegram") },
                leadingContent = { Icon(Icons.Outlined.CloudUpload, null) },
                modifier = Modifier.clickable {
                    vm.loadSavedChannels()
                    channelPickerFor = listOf(file.id)
                    actionsFor = null
                }
            )
            if (file.isFile) {
                ListItem(
                    headlineContent = { Text("Descargar al dispositivo") },
                    leadingContent = { Icon(Icons.Outlined.Download, null) },
                    modifier = Modifier.clickable {
                        vm.downloadToDevice(file); actionsFor = null
                    }
                )
            }
            ListItem(
                headlineContent = { Text("Renombrar") },
                leadingContent = { Icon(Icons.Outlined.Edit, null) },
                modifier = Modifier.clickable { renameFor = file; actionsFor = null }
            )
            ListItem(
                headlineContent = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable {
                    deleteFor = listOf(file.id); actionsFor = null
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    renameFor?.let { file ->
        InputDialog(
            title = "Renombrar",
            label = "Nuevo nombre",
            initialValue = file.name,
            confirmLabel = "Renombrar",
            onConfirm = { vm.rename(file, it) },
            onDismiss = { renameFor = null }
        )
    }

    deleteFor?.let { paths ->
        ConfirmDialog(
            title = "Eliminar ${paths.size} elemento(s)",
            text = "Se eliminarán del disco del servidor. Esta acción es irreversible.",
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = { vm.delete(paths) },
            onDismiss = { deleteFor = null }
        )
    }

    if (showCreateFolder) {
        InputDialog(
            title = "Nueva carpeta",
            label = "Nombre",
            confirmLabel = "Crear",
            onConfirm = vm::createFolder,
            onDismiss = { showCreateFolder = false }
        )
    }

    if (confirmClearCache) {
        ConfirmDialog(
            title = "Vaciar caché de streaming",
            text = "Se libera el espacio de la caché temporal. La próxima reproducción volverá a descargar lo necesario.",
            confirmLabel = "Vaciar",
            onConfirm = vm::clearStreamCache,
            onDismiss = { confirmClearCache = false }
        )
    }

    channelPickerFor?.let { paths ->
        AlertDialog(
            onDismissRequest = { channelPickerFor = null },
            title = { Text("Subir a canal") },
            text = {
                val channels = state.savedChannels
                when {
                    channels == null -> Text("Cargando canales…")
                    channels.isEmpty() -> Text("No hay canales indexados.")
                    else -> LazyColumn {
                        items(channels, key = { it.id }) { channel ->
                            ListItem(
                                headlineContent = { Text(channel.name ?: "") },
                                modifier = Modifier.clickable {
                                    // Next step: choose the destination folder.
                                    folderPickerFor = paths to channel
                                    channelPickerFor = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { channelPickerFor = null }) { Text("Cerrar") }
            }
        )
    }

    folderPickerFor?.let { (paths, channel) ->
        com.mateof.tfm.ui.components.ChannelFolderPicker(
            channelId = channel.id.toString(),
            channelName = channel.name ?: "",
            onPick = { targetPath ->
                vm.uploadToChannel(paths, channel, targetPath)
                folderPickerFor = null
            },
            onDismiss = { folderPickerFor = null }
        )
    }
}
