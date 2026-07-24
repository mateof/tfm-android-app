package com.mateof.tfm.ui.screens.local

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.mateof.tfm.core.Format
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.model.ChannelFoldersDto
import com.mateof.tfm.data.model.ChatFolderDto
import com.mateof.tfm.ui.components.ConfirmDialog
import com.mateof.tfm.ui.screens.channels.rememberMediaUrls
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
        ChannelPickerSheet(
            channels = state.savedChannels,
            foldersData = state.channelPickerFolders,
            query = state.channelPickerSearch,
            onQueryChange = vm::setChannelPickerSearch,
            onPick = { channel ->
                folderPickerFor = paths to channel
                channelPickerFor = null
            },
            onDismiss = { channelPickerFor = null }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelPickerSheet(
    channels: List<ChannelDto>?,
    foldersData: ChannelFoldersDto?,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (ChannelDto) -> Unit,
    onDismiss: () -> Unit
) {
    val mediaUrls = rememberMediaUrls()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "Subir a canal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Buscar canal…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        when {
            channels == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { Text("Cargando canales…") }

            channels.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { Text("No hay canales indexados.") }

            else -> {
                val sections = remember(channels, foldersData, query) {
                    groupChannelsByFolder(channels, foldersData, query)
                }
                if (sections.all { it.channels.isEmpty() }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Sin resultados") }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                        sections.forEach { section ->
                            if (section.channels.isEmpty()) return@forEach
                            item(key = "h-${section.key}") {
                                PickerFolderHeader(
                                    title = section.title,
                                    emoji = section.emoji,
                                    count = section.channels.size
                                )
                            }
                            items(section.channels, key = { "${section.key}-${it.id}" }) { c ->
                                ListItem(
                                    headlineContent = { Text(c.name ?: "(sin nombre)") },
                                    supportingContent = {
                                        Text(
                                            c.type ?: "canal",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                (c.name ?: "?").take(1).uppercase(),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            AsyncImage(
                                                model = mediaUrls.channelImage(c.id),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                    },
                                    modifier = Modifier.clickable { onPick(c) }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun PickerFolderHeader(title: String, emoji: String?, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        if (!emoji.isNullOrBlank()) {
            Text(emoji, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class PickerSection(
    val key: String,
    val title: String,
    val emoji: String?,
    val channels: List<ChannelDto>
)

/**
 * Groups the indexed [channels] into the Telegram folders reported by
 * [foldersData], keeping only channels that pass the [query] filter. Channels
 * that don't belong to any folder go into a trailing "Sin carpeta" section.
 * If [foldersData] is null, everything falls into a single "Todos" section.
 */
private fun groupChannelsByFolder(
    channels: List<ChannelDto>,
    foldersData: ChannelFoldersDto?,
    query: String
): List<PickerSection> {
    val q = query.trim()
    fun match(c: ChannelDto) = q.isBlank() ||
        (c.name?.contains(q, ignoreCase = true) == true)

    val filtered = channels.filter(::match)
    if (foldersData == null || foldersData.folders.isEmpty()) {
        return listOf(PickerSection("all", "Todos", null, filtered))
    }
    val byId = filtered.associateBy { it.id }
    val used = mutableSetOf<Long>()
    val sections = foldersData.folders.mapNotNull { folder: ChatFolderDto ->
        val members = folder.channels.mapNotNull { byId[it.id] }
        members.forEach { used += it.id }
        if (members.isEmpty()) null
        else PickerSection("f-${folder.id}", folder.title ?: "(sin nombre)", folder.iconEmoji, members)
    }
    val ungrouped = filtered.filter { it.id !in used }
    return if (ungrouped.isEmpty()) sections
    else sections + PickerSection("ungrouped", "Sin carpeta", null, ungrouped)
}
