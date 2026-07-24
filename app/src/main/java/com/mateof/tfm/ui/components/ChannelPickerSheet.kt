package com.mateof.tfm.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.ChannelsApi
import com.mateof.tfm.data.api.SharesApi
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.model.ChannelFoldersDto
import com.mateof.tfm.data.model.ChatFolderDto
import com.mateof.tfm.data.model.SharedCollectionDto
import com.mateof.tfm.data.repo.MediaUrls
import com.mateof.tfm.ui.screens.channels.ChannelsTab
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChannelPickerEntryPoint {
    fun channelsApi(): ChannelsApi
    fun sharesApi(): SharesApi
    fun mediaUrls(): MediaUrls
}

@Composable
private fun rememberChannelPickerDeps(): ChannelPickerEntryPoint {
    val context = LocalContext.current
    return remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ChannelPickerEntryPoint::class.java
        )
    }
}

/**
 * Bottom sheet that mirrors the main "Canales" screen — same 5 tabs (Míos,
 * Todos, Favoritos, Carpetas, Compartidos) and a search box — so the user
 * can pick any channel as an upload target instead of only the indexed ones.
 *
 * SHARED collections resolve to the underlying Telegram channel: uploading
 * to a shared collection is the same as uploading to the channel it points to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelPickerSheet(
    title: String = "Subir a canal",
    onPick: (ChannelDto) -> Unit,
    onDismiss: () -> Unit
) {
    val deps = rememberChannelPickerDeps()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tab by remember { mutableStateOf(ChannelsTab.MINE) }
    var query by remember { mutableStateOf("") }

    // Debounce the query so we don't spam the server on every keystroke.
    var effectiveQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(300)
        effectiveQuery = query.trim()
    }

    // Ids of channels that already have a local index. Only those are valid
    // upload targets, so every tab is filtered against this set. Loaded once,
    // refreshed only when the picker opens.
    var savedIds by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            apiCall { deps.channelsApi().list(onlySaved = true, pageSize = 500) }
        }.onSuccess { list -> savedIds = list.map { it.id }.toSet() }
            .onFailure { savedIds = emptySet() }
    }

    // One state slot per tab; content refreshes when (tab, effectiveQuery) changes.
    val listState = remember { mutableStateMapOf<ChannelsTab, TabDataState>() }
    var foldersState by remember { mutableStateOf(FoldersDataState()) }
    val expandedFolders = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(tab, effectiveQuery) {
        when (tab) {
            ChannelsTab.MINE -> loadChannels(
                deps.channelsApi(), tab, effectiveQuery, listState, onlySaved = true
            ) { it.filter { c -> c.isOwner } }
            ChannelsTab.ALL -> loadChannels(
                deps.channelsApi(), tab, effectiveQuery, listState, onlySaved = true
            )
            ChannelsTab.FAVORITES -> loadChannels(
                deps.channelsApi(), tab, effectiveQuery, listState,
                onlySaved = true, favoritesOnly = true
            )
            ChannelsTab.FOLDERS -> {
                foldersState = foldersState.copy(loading = true, error = null)
                runCatching { apiCall { deps.channelsApi().folders() } }
                    .onSuccess { foldersState = FoldersDataState(data = it) }
                    .onFailure { foldersState = FoldersDataState(error = it.userMessage()) }
            }
            ChannelsTab.SHARED -> loadShares(deps.sharesApi(), effectiveQuery, listState)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
            ChannelsTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label) }
                )
            }
        }
        HorizontalDivider()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 520.dp)
        ) {
            when (tab) {
                ChannelsTab.FOLDERS -> FoldersTabBody(
                    state = foldersState,
                    query = effectiveQuery,
                    mediaUrls = deps.mediaUrls(),
                    expanded = expandedFolders,
                    savedIds = savedIds,
                    onPick = onPick
                )
                ChannelsTab.SHARED -> {
                    val td = listState[ChannelsTab.SHARED]
                    when {
                        td == null || td.loading -> CenteredSpinner()
                        td.error != null -> CenteredMessage(td.error)
                        td.shares.isEmpty() -> CenteredMessage("Sin colecciones compartidas")
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(td.shares, key = { it.id }) { share ->
                                SharedRow(share = share, onPick = {
                                    val channelId = share.channelId?.toLongOrNull() ?: return@SharedRow
                                    onPick(
                                        ChannelDto(
                                            id = channelId,
                                            name = share.name,
                                            type = "channel"
                                        )
                                    )
                                })
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
                else -> {
                    val td = listState[tab]
                    when {
                        td == null || td.loading -> CenteredSpinner()
                        td.error != null -> CenteredMessage(td.error)
                        td.channels.isEmpty() -> CenteredMessage("Sin resultados")
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(td.channels, key = { it.id }) { c ->
                                ChannelPickerRow(
                                    channel = c,
                                    mediaUrls = deps.mediaUrls(),
                                    onPick = onPick
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun FoldersTabBody(
    state: FoldersDataState,
    query: String,
    mediaUrls: MediaUrls,
    expanded: MutableMap<Long, Boolean>,
    savedIds: Set<Long>?,
    onPick: (ChannelDto) -> Unit
) {
    when {
        state.loading || savedIds == null -> CenteredSpinner()
        state.error != null -> CenteredMessage(state.error)
        state.data == null -> CenteredMessage("Cargando…")
        else -> {
            val data = filterFolders(state.data, query, savedIds)
            if (data.folders.isEmpty() && data.ungrouped.isEmpty()) {
                CenteredMessage("Sin resultados")
                return
            }
            val searching = query.isNotBlank()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                data.folders.forEach { folder ->
                    val isOpen = searching || (expanded[folder.id] ?: false)
                    item(key = "h-${folder.id}") {
                        FolderPickerHeader(
                            title = folder.title ?: "(sin nombre)",
                            emoji = folder.iconEmoji,
                            count = folder.channels.size,
                            expanded = isOpen,
                            onClick = {
                                if (!searching) expanded[folder.id] = !isOpen
                            }
                        )
                    }
                    if (isOpen) {
                        items(folder.channels, key = { "${folder.id}-${it.id}" }) { c ->
                            ChannelPickerRow(channel = c, mediaUrls = mediaUrls, onPick = onPick)
                        }
                    }
                }
                if (data.ungrouped.isNotEmpty()) {
                    val ungroupedId = 0L
                    val isOpen = searching || (expanded[ungroupedId] ?: false)
                    item(key = "h-ungrouped") {
                        FolderPickerHeader(
                            title = "Sin carpeta",
                            emoji = null,
                            count = data.ungrouped.size,
                            expanded = isOpen,
                            onClick = {
                                if (!searching) expanded[ungroupedId] = !isOpen
                            }
                        )
                    }
                    if (isOpen) {
                        items(data.ungrouped, key = { "u-${it.id}" }) { c ->
                            ChannelPickerRow(channel = c, mediaUrls = mediaUrls, onPick = onPick)
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun FolderPickerHeader(
    title: String,
    emoji: String?,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
            contentDescription = if (expanded) "Colapsar" else "Expandir",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
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

@Composable
private fun ChannelPickerRow(
    channel: ChannelDto,
    mediaUrls: MediaUrls,
    onPick: (ChannelDto) -> Unit
) {
    ListItem(
        headlineContent = {
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
        },
        supportingContent = {
            Text(
                buildString {
                    append(channel.type ?: "canal")
                    if (channel.hasDatabase) append(" · indexado")
                    if (channel.isOwner) append(" · propio")
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (channel.name ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                AsyncImage(
                    model = mediaUrls.channelImage(channel.id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            }
        },
        modifier = Modifier.clickable { onPick(channel) }
    )
}

@Composable
private fun SharedRow(share: SharedCollectionDto, onPick: () -> Unit) {
    ListItem(
        headlineContent = { Text(share.name ?: "(sin nombre)") },
        supportingContent = {
            Text(
                buildString {
                    share.description?.takeIf { it.isNotBlank() }?.let { append(it).append("  ·  ") }
                    append("Canal ").append(share.channelId ?: "?")
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        },
        modifier = Modifier.clickable { onPick() }
    )
}

@Composable
private fun CenteredSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// State + loaders
// ---------------------------------------------------------------------------

private data class TabDataState(
    val loading: Boolean = true,
    val error: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    val shares: List<SharedCollectionDto> = emptyList()
)

private data class FoldersDataState(
    val loading: Boolean = false,
    val error: String? = null,
    val data: ChannelFoldersDto? = null
)

private suspend fun loadChannels(
    api: ChannelsApi,
    tab: ChannelsTab,
    query: String,
    into: MutableMap<ChannelsTab, TabDataState>,
    onlySaved: Boolean = false,
    favoritesOnly: Boolean = false,
    transform: (List<ChannelDto>) -> List<ChannelDto> = { it }
) {
    into[tab] = TabDataState(loading = true)
    runCatching {
        apiCall {
            api.list(
                onlySaved = onlySaved,
                favoritesOnly = favoritesOnly,
                search = query.ifBlank { null },
                pageSize = 200
            )
        }
    }.onSuccess { list ->
        into[tab] = TabDataState(loading = false, channels = transform(list))
    }.onFailure { e ->
        into[tab] = TabDataState(loading = false, error = e.userMessage())
    }
}

private suspend fun loadShares(
    api: SharesApi,
    query: String,
    into: MutableMap<ChannelsTab, TabDataState>
) {
    into[ChannelsTab.SHARED] = TabDataState(loading = true)
    runCatching {
        apiCall { api.list(filter = query.ifBlank { null }, pageSize = 200) }
    }.onSuccess { list ->
        into[ChannelsTab.SHARED] = TabDataState(loading = false, shares = list)
    }.onFailure { e ->
        into[ChannelsTab.SHARED] = TabDataState(loading = false, error = e.userMessage())
    }
}

/**
 * Keeps only channels that (a) match the search [query] and (b) already have
 * a local index — the picker only surfaces valid upload targets.
 */
private fun filterFolders(
    data: ChannelFoldersDto,
    query: String,
    savedIds: Set<Long>
): ChannelFoldersDto {
    fun matchQuery(c: ChannelDto) = query.isBlank() ||
        (c.name?.contains(query, ignoreCase = true) == true)
    fun keep(c: ChannelDto) = c.id in savedIds && matchQuery(c)
    val folders = data.folders.mapNotNull { f: ChatFolderDto ->
        val kept = f.channels.filter(::keep)
        if (kept.isEmpty()) null
        else f.copy(channels = kept, channelCount = kept.size)
    }
    val ungrouped = data.ungrouped.filter(::keep)
    return data.copy(
        folders = folders,
        ungrouped = ungrouped,
        totalChannels = folders.sumOf { it.channelCount } + ungrouped.size
    )
}
