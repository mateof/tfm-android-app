package com.mateof.tfm.ui.screens.files

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

private val FILTERS = listOf(
    "all" to "Todo",
    "audio" to "Audio",
    "video" to "Vídeo",
    "photo" to "Fotos",
    "document" to "Docs",
    "archive" to "Archivos"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavHostController, vm: FilesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var actionsFor by remember { mutableStateOf<ApiFileDto?>(null) }
    var renameFor by remember { mutableStateOf<ApiFileDto?>(null) }
    var deleteFor by remember { mutableStateOf<List<String>?>(null) }
    var copyMoveFor by remember { mutableStateOf<Pair<List<String>, Boolean>?>(null) }
    var playlistFor by remember { mutableStateOf<ApiFileDto?>(null) }
    var showCreateFolder by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSelectMenu by remember { mutableStateOf(false) }
    var showStrm by rememberSaveable { mutableStateOf(false) }
    var showScanOptions by rememberSaveable { mutableStateOf(false) }

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
    BackHandler(enabled = state.selection.isEmpty()) {
        if (!vm.navigateUp()) navController.popBackStack()
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
                        IconButton(onClick = { vm.downloadSelectionToServer() }) {
                            Icon(Icons.Outlined.CloudDownload, "Descargar al servidor")
                        }
                        IconButton(onClick = { vm.downloadSelectionToDevice() }) {
                            Icon(Icons.Outlined.Download, "Descargar al dispositivo")
                        }
                        IconButton(onClick = {
                            copyMoveFor = state.selection.toList() to false
                        }) {
                            Icon(Icons.Outlined.ContentCopy, "Copiar")
                        }
                        IconButton(onClick = {
                            copyMoveFor = state.selection.toList() to true
                        }) {
                            Icon(Icons.Outlined.DriveFileMove, "Mover")
                        }
                        IconButton(onClick = { deleteFor = state.selection.toList() }) {
                            Icon(Icons.Outlined.Delete, "Eliminar")
                        }
                        IconButton(onClick = { showSelectMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "Más")
                        }
                        DropdownMenu(
                            expanded = showSelectMenu,
                            onDismissRequest = { showSelectMenu = false }
                        ) {
                            val allSelected = state.items.isNotEmpty() &&
                                state.selection.size == state.items.size
                            DropdownMenuItem(
                                text = { Text("Seleccionar todos") },
                                enabled = !allSelected,
                                onClick = { vm.selectAll(); showSelectMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Deseleccionar todos") },
                                onClick = { vm.clearSelection(); showSelectMenu = false }
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.channelName.ifBlank { "Ficheros" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                state.contents?.currentPath ?: vm.path,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!vm.navigateUp()) navController.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.load() }) {
                            Icon(Icons.Outlined.Refresh, "Recargar")
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Outlined.Search, "Buscar")
                        }
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.Outlined.Sort, "Ordenar")
                        }
                        SortMenu(
                            expanded = showSort,
                            onDismiss = { showSort = false },
                            current = state.sortBy,
                            descending = state.sortDescending,
                            onPick = { by, desc -> vm.setSort(by, desc) }
                        )
                        IconButton(onClick = { showCreateFolder = true }) {
                            Icon(Icons.Outlined.CreateNewFolder, "Nueva carpeta")
                        }
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, "Más opciones")
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Exportar .strm de esta carpeta") },
                                leadingIcon = { Icon(Icons.Outlined.MovieCreation, null) },
                                onClick = {
                                    showOverflow = false
                                    showStrm = true
                                }
                            )
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

            if (showSearch) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = vm::setSearch,
                    placeholder = { Text("Buscar en esta carpeta y subcarpetas…") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = vm::submitSearch) { Text("Buscar") }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

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

            // Breadcrumbs
            val crumbs = state.contents?.breadcrumbs.orEmpty()
            if (crumbs.isNotEmpty() && !state.searchMode) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    crumbs.forEachIndexed { i, crumb ->
                        Text(
                            text = crumb.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (i == crumbs.lastIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (i == crumbs.lastIndex) FontWeight.Bold else null,
                            modifier = Modifier.clickable { vm.navigateTo(crumb.path) }
                        )
                        if (i != crumbs.lastIndex) {
                            Text(
                                "  ›  ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Pull-to-refresh over the list. During a background scan the
            // channel db grows silently; swiping reloads without leaving.
            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = { vm.load() },
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                state.loading && state.items.isEmpty() -> LoadingBox(label = "Cargando ficheros…")
                state.needsIndex -> NeedsIndexState(onCreate = { showScanOptions = true })
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.load() })
                state.items.isEmpty() -> EmptyState("Carpeta vacía")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.items, key = { _, f -> f.id }) { _, file ->
                        FileRow(
                            file = file,
                            subtitle = buildString {
                                if (file.isFile) {
                                    append(file.sizeText ?: Format.bytes(file.size))
                                    file.type?.let { append("  ·  ").append(it) }
                                } else {
                                    append("Carpeta")
                                }
                            },
                            selected = file.id in state.selection,
                            onClick = {
                                when {
                                    state.selection.isNotEmpty() -> vm.toggleSelection(file)
                                    !file.isFile -> vm.navigateTo(
                                        file.path?.plus(file.name)?.plus("/")
                                            ?: (vm.path + file.name + "/")
                                    )
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
    }

    FullScreenSpinnerOverlay(visible = state.busy)

    // ---------------------------------------------------------- action sheet
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
            if (file.isFile && file.category.equals("audio", true)) {
                ListItem(
                    headlineContent = { Text("Reproducir") },
                    leadingContent = { Icon(Icons.Outlined.PlayArrow, null) },
                    modifier = Modifier.clickable { vm.play(file); actionsFor = null }
                )
                ListItem(
                    headlineContent = { Text("Añadir a playlist") },
                    leadingContent = { Icon(Icons.Outlined.PlaylistAdd, null) },
                    modifier = Modifier.clickable {
                        vm.loadPlaylists(); playlistFor = file; actionsFor = null
                    }
                )
            }
            ListItem(
                headlineContent = { Text("Descargar al servidor") },
                leadingContent = { Icon(Icons.Outlined.CloudDownload, null) },
                modifier = Modifier.clickable {
                    vm.downloadToServer(listOf(file.id)); actionsFor = null
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
                headlineContent = { Text("Copiar a…") },
                leadingContent = { Icon(Icons.Outlined.ContentCopy, null) },
                modifier = Modifier.clickable {
                    copyMoveFor = listOf(file.id) to false; actionsFor = null
                }
            )
            ListItem(
                headlineContent = { Text("Mover a…") },
                leadingContent = { Icon(Icons.Outlined.DriveFileMove, null) },
                modifier = Modifier.clickable {
                    copyMoveFor = listOf(file.id) to true; actionsFor = null
                }
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

    // --------------------------------------------------------------- dialogs
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

    deleteFor?.let { ids ->
        ConfirmDialog(
            title = "Eliminar ${ids.size} elemento(s)",
            text = "Se eliminarán también los mensajes de Telegram que los respaldan. Esta acción es irreversible.",
            confirmLabel = "Eliminar",
            destructive = true,
            onConfirm = { vm.delete(ids) },
            onDismiss = { deleteFor = null }
        )
    }

    copyMoveFor?.let { (ids, move) ->
        InputDialog(
            title = if (move) "Mover a carpeta" else "Copiar a carpeta",
            label = "Ruta destino (p. ej. /backup/)",
            initialValue = "/",
            confirmLabel = if (move) "Mover" else "Copiar",
            onConfirm = { vm.copyOrMove(ids, it, move) },
            onDismiss = { copyMoveFor = null }
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

    if (showStrm) {
        ExportStrmDialog(
            folderPath = state.contents?.currentPath ?: vm.path,
            onExport = { destination ->
                vm.exportStrm(destination)
                showStrm = false
            },
            onDismiss = { showStrm = false }
        )
    }

    if (showScanOptions) {
        ScanOptionsDialog(
            onConfirm = { options ->
                vm.createIndexAndScan(options)
                showScanOptions = false
            },
            onDismiss = { showScanOptions = false }
        )
    }

    playlistFor?.let { file ->
        AlertDialog(
            onDismissRequest = { playlistFor = null },
            title = { Text("Añadir a playlist") },
            text = {
                val playlists = state.playlists
                when {
                    playlists == null -> Text("Cargando playlists…")
                    playlists.isEmpty() -> Text("No hay playlists. Crea una en la pestaña Listas.")
                    else -> Column {
                        playlists.forEach { pl ->
                            ListItem(
                                headlineContent = { Text(pl.name) },
                                supportingContent = { Text("${pl.trackCount} pistas") },
                                modifier = Modifier.clickable {
                                    vm.addToPlaylist(file, pl)
                                    playlistFor = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistFor = null }) { Text("Cerrar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportStrmDialog(
    folderPath: String,
    onExport: (destinationFolder: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var toLocal by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    var showFolderPicker by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar .strm") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    "Carpeta: $folderPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                ListItem(
                    headlineContent = { Text("Descargar como ZIP") },
                    supportingContent = { Text("Se descarga en el dispositivo") },
                    leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
                    trailingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = !toLocal,
                            onClick = { toLocal = false }
                        )
                    },
                    modifier = Modifier.clickable { toLocal = false }
                )
                ListItem(
                    headlineContent = { Text("Guardar en el servidor") },
                    supportingContent = { Text("Los .strm quedan en el almacenamiento local del servidor") },
                    leadingContent = { Icon(Icons.Outlined.FolderOpen, null) },
                    trailingContent = {
                        androidx.compose.material3.RadioButton(
                            selected = toLocal,
                            onClick = { toLocal = true }
                        )
                    },
                    modifier = Modifier.clickable { toLocal = true }
                )
                if (toLocal) {
                    Spacer(Modifier.height(8.dp))
                    if (destination == null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showFolderPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("Elegir carpeta…")
                        }
                    } else {
                        ListItem(
                            headlineContent = {
                                Text(destination!!.ifBlank { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text("Carpeta destino en el servidor") },
                            leadingContent = { Icon(Icons.Outlined.FolderOpen, null) },
                            trailingContent = {
                                TextButton(onClick = { showFolderPicker = true }) { Text("Cambiar") }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !toLocal || destination != null,
                onClick = { onExport(if (toLocal) destination else null) }
            ) { Text(if (toLocal) "Exportar" else "Descargar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    if (showFolderPicker) {
        com.mateof.tfm.ui.components.LocalFolderPicker(
            title = "Guardar .strm en…",
            confirmLabel = "Elegir esta carpeta",
            onPick = { picked ->
                destination = picked
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }
}

@Composable
private fun ScanOptionsDialog(
    onConfirm: (com.mateof.tfm.data.model.RefreshChannelRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var includeVideo by rememberSaveable { mutableStateOf(true) }
    var includeAudio by rememberSaveable { mutableStateOf(true) }
    var includePhotos by rememberSaveable { mutableStateOf(true) }
    var includeDocuments by rememberSaveable { mutableStateOf(true) }
    val anySelected = includeVideo || includeAudio || includePhotos || includeDocuments

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escanear canal") },
        text = {
            Column {
                Text(
                    "Elige qué tipos de contenido quieres indexar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ScanCheckbox("Vídeo", includeVideo) { includeVideo = it }
                ScanCheckbox("Audio", includeAudio) { includeAudio = it }
                ScanCheckbox("Fotos", includePhotos) { includePhotos = it }
                ScanCheckbox("Documentos", includeDocuments) { includeDocuments = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = anySelected,
                onClick = {
                    onConfirm(
                        com.mateof.tfm.data.model.RefreshChannelRequest(
                            includeDocuments = includeDocuments,
                            includeAudio = includeAudio,
                            includeVideo = includeVideo,
                            includePhotos = includePhotos
                        )
                    )
                }
            ) { Text("Escanear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ScanCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: String,
    descending: Boolean,
    onPick: (String, Boolean) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            "name" to "Nombre",
            "date" to "Fecha",
            "size" to "Tamaño",
            "type" to "Tipo"
        ).forEach { (value, label) ->
            DropdownMenuItem(
                text = {
                    Text(
                        if (current == value) {
                            "$label ${if (descending) "↓" else "↑"}"
                        } else label
                    )
                },
                onClick = {
                    val desc = if (current == value) !descending else false
                    onPick(value, desc)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun NeedsIndexState(onCreate: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.CreateNewFolder,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Este canal aún no tiene índice local",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "Crea el índice y escanea el canal para poder navegar sus ficheros.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        androidx.compose.material3.Button(
            onClick = onCreate,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Icon(Icons.Outlined.CreateNewFolder, null)
            Text("  Crear índice y escanear")
        }
    }
}
