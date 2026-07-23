package com.mateof.tfm.ui.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mateof.tfm.core.Format
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.repo.MediaUrls
import com.mateof.tfm.ui.components.ConfirmDialog
import com.mateof.tfm.ui.components.EmptyState
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.FullScreenSpinnerOverlay
import com.mateof.tfm.ui.components.InputDialog
import com.mateof.tfm.ui.components.LoadingBox
import com.mateof.tfm.ui.nav.Routes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaUrlsEntryPoint {
    fun mediaUrls(): MediaUrls
}

@Composable
fun rememberMediaUrls(): MediaUrls {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MediaUrlsEntryPoint::class.java
        ).mediaUrls()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(navController: NavHostController, vm: ChannelsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mediaUrls = rememberMediaUrls()
    val snackbarHostState = remember { SnackbarHostState() }

    var actionsFor by remember { mutableStateOf<ChannelDto?>(null) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var leaveFor by remember { mutableStateOf<ChannelDto?>(null) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Canal") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Canales",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showJoin = true }) {
                    Icon(Icons.Outlined.GroupAdd, contentDescription = "Unirse por invitación")
                }
                IconButton(onClick = { vm.load() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Recargar")
                }
            }

            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Buscar canal…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = state.tab.ordinal) {
                ChannelsTab.entries.forEach { t ->
                    Tab(
                        selected = state.tab == t,
                        onClick = { vm.setTab(t) },
                        text = { Text(t.label) }
                    )
                }
            }

            when {
                state.loading -> LoadingBox(label = "Cargando canales…")
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.load() })
                state.channels.isEmpty() -> EmptyState(
                    when (state.tab) {
                        ChannelsTab.SAVED ->
                            "No hay canales indexados todavía.\nIndexa uno desde la pestaña «Todos»."
                        ChannelsTab.MINE ->
                            "No eres propietario de ningún canal.\nCrea uno con el botón +."
                        else -> "Sin resultados"
                    }
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.channels, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            imageUrl = mediaUrls.channelImage(channel.id),
                            onClick = {
                                // Always open the browser; it guides indexing
                                // when the channel has no local database yet.
                                navController.navigate(
                                    Routes.files(channel.id.toString(), channel.name ?: "")
                                )
                            },
                            onMore = { actionsFor = channel }
                        )
                    }
                    if (state.hasNext) {
                        item {
                            TextButton(
                                onClick = { vm.load(more = true) },
                                enabled = !state.loadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Text(if (state.loadingMore) "Cargando…" else "Cargar más")
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    FullScreenSpinnerOverlay(visible = state.busy)

    actionsFor?.let { channel ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            Text(
                channel.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Explorar ficheros") },
                leadingContent = { Icon(Icons.Outlined.Dns, null) },
                modifier = Modifier.clickable {
                    actionsFor = null
                    navController.navigate(
                        Routes.files(channel.id.toString(), channel.name ?: "")
                    )
                }
            )
            if (channel.hasDatabase) {
                ListItem(
                    headlineContent = { Text("Actualizar índice (buscar nuevos ficheros)") },
                    leadingContent = { Icon(Icons.Outlined.Refresh, null) },
                    modifier = Modifier.clickable {
                        vm.refreshChannel(channel); actionsFor = null
                    }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Crear índice local (guardar canal)") },
                    leadingContent = { Icon(Icons.Outlined.Dns, null) },
                    modifier = Modifier.clickable {
                        vm.createDatabase(channel); actionsFor = null
                    }
                )
            }
            ListItem(
                headlineContent = {
                    Text(if (channel.isFavorite) "Quitar de favoritos" else "Añadir a favoritos")
                },
                leadingContent = {
                    Icon(
                        if (channel.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        null
                    )
                },
                modifier = Modifier.clickable { vm.toggleFavorite(channel); actionsFor = null }
            )
            ListItem(
                headlineContent = { Text("Mensajes recientes") },
                leadingContent = { Icon(Icons.Outlined.Message, null) },
                modifier = Modifier.clickable {
                    actionsFor = null
                    navController.navigate(
                        Routes.messages(channel.id.toString(), channel.name ?: "")
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Detalles") },
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                modifier = Modifier.clickable { vm.showDetails(channel); actionsFor = null }
            )
            ListItem(
                headlineContent = {
                    Text("Salir del canal", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable { leaveFor = channel; actionsFor = null }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    state.details?.let { d ->
        AlertDialog(
            onDismissRequest = vm::dismissDetails,
            title = { Text(d.name ?: "") },
            text = {
                Column {
                    DetailLine("Tipo", d.type ?: "-")
                    DetailLine("Ficheros", d.fileCount?.toString() ?: "-")
                    DetailLine("Carpetas", d.folderCount?.toString() ?: "-")
                    DetailLine("Tamaño", d.totalSizeText ?: Format.bytes(d.totalSize))
                    DetailLine("Audio", d.audioCount?.toString() ?: "-")
                    DetailLine("Vídeo", d.videoCount?.toString() ?: "-")
                    DetailLine("Fotos", d.photoCount?.toString() ?: "-")
                    DetailLine("Documentos", d.documentCount?.toString() ?: "-")
                    DetailLine("Indexando ahora", if (d.isRefreshing == true) "Sí" else "No")
                }
            },
            confirmButton = {
                TextButton(onClick = vm::dismissDetails) { Text("Cerrar") }
            }
        )
    }

    if (showCreate) {
        InputDialog(
            title = "Crear canal",
            label = "Nombre del canal",
            confirmLabel = "Crear",
            onConfirm = vm::createChannel,
            onDismiss = { showCreate = false }
        )
    }

    if (showJoin) {
        InputDialog(
            title = "Unirse a un canal",
            label = "Enlace o hash de invitación",
            confirmLabel = "Unirse",
            onConfirm = vm::joinByHash,
            onDismiss = { showJoin = false }
        )
    }

    leaveFor?.let { channel ->
        ConfirmDialog(
            title = "Salir de «${channel.name}»",
            text = "Se abandonará el canal en Telegram. El índice local también se eliminará.",
            confirmLabel = "Salir",
            destructive = true,
            onConfirm = { vm.leave(channel, deleteDb = true, deleteOnTelegram = false) },
            onDismiss = { leaveFor = null }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelDto,
    imageUrl: String?,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (channel.name ?: "?").take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.isFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorito",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    channel.name ?: "(sin nombre)",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                buildString {
                    append(channel.type ?: "canal")
                    if (channel.hasDatabase) append(" · indexado")
                    if (channel.isOwner) append(" · propio")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.Info, contentDescription = "Acciones")
        }
    }
}
