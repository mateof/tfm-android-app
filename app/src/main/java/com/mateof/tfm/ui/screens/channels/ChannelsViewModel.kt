package com.mateof.tfm.ui.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfm.core.ApiException
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.apiCallNullable
import com.mateof.tfm.core.apiCallPaged
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.ChannelsApi
import com.mateof.tfm.data.api.SharesApi
import com.mateof.tfm.data.api.TransfersApi
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.model.ChannelFoldersDto
import com.mateof.tfm.data.model.CreateChannelRequest
import com.mateof.tfm.data.model.LeaveChannelRequest
import com.mateof.tfm.data.model.RefreshChannelRequest
import com.mateof.tfm.data.model.SharedCollectionDto
import com.mateof.tfm.data.model.StartDownloadsRequest
import com.mateof.tfm.data.prefs.ServerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

enum class ChannelsTab(val label: String) {
    MINE("Míos"),
    ALL("Todos"),
    FAVORITES("Favoritos"),
    FOLDERS("Carpetas"),
    HIDDEN("Ocultos"),
    SHARED("Compartidos")
}

data class ChannelsUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    val folders: ChannelFoldersDto? = null,
    val shares: List<SharedCollectionDto> = emptyList(),
    val tab: ChannelsTab = ChannelsTab.MINE,
    val search: String = "",
    val page: Int = 1,
    val hasNext: Boolean = false,
    val busy: Boolean = false,
    val snackbar: String? = null,
    val details: ChannelDto? = null,
    // Ids of folders currently expanded in the "Carpetas" tab. Ungrouped
    // section uses the sentinel id 0L.
    val expandedFolders: Set<Long> = emptySet(),
    // Ids of channels that have a local index. Populated on every load and
    // used to mark rows with the "indexed" badge — the server's plain
    // channels list does not carry that flag.
    val savedIds: Set<Long> = emptySet(),
    // Media types picked the last time the user scanned a channel.
    val scanOptions: RefreshChannelRequest = RefreshChannelRequest(),
    // Server-wide setting: when off, hidden channels are filtered out of every
    // list (the server does the filtering). Null until the config is read.
    val showHidden: Boolean? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val api: ChannelsApi,
    private val sharesApi: SharesApi,
    private val transfersApi: TransfersApi,
    private val configApi: com.mateof.tfm.data.api.ConfigApi,
    private val prefs: ServerPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState())
    val state = _state.asStateFlow()

    private val searchInput = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            searchInput.drop(1).debounce(400).distinctUntilChanged().collect { load() }
        }
        viewModelScope.launch {
            prefs.scanOptions.collect { options ->
                _state.value = _state.value.copy(scanOptions = options)
            }
        }
    }

    private fun loadShowHidden() {
        viewModelScope.launch {
            runCatching { apiCall { configApi.get() } }
                .onSuccess { c ->
                    _state.value = _state.value.copy(showHidden = c.showHiddenChannels ?: false)
                }
        }
    }

    /**
     * Flips the server-wide "show hidden channels" setting. It is shared with
     * the web, so this changes what every client sees.
     */
    fun setShowHidden(value: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val body = buildJsonObject { put("showHiddenChannels", JsonPrimitive(value)) }
            runCatching { apiCall { configApi.patch(body) } }
                .onSuccess { c ->
                    _state.value = _state.value.copy(
                        busy = false,
                        showHidden = c.showHiddenChannels ?: value,
                        snackbar = if (value) {
                            "Mostrando también los canales ocultos"
                        } else {
                            "Los canales ocultos quedan fuera de las listas"
                        }
                    )
                    load()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
                }
        }
    }

    /** Hides the channel from the lists, or brings it back. */
    fun toggleHidden(channel: ChannelDto) {
        val hide = !channel.isHidden
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                apiCallNullable {
                    if (hide) api.addHidden(channel.id.toString())
                    else api.removeHidden(channel.id.toString())
                }
            }.onSuccess {
                _state.value = _state.value.copy(
                    busy = false,
                    snackbar = if (hide) {
                        "«${channel.name}» oculto" +
                            if (_state.value.showHidden == true) "" else " (ya no aparece en las listas)"
                    } else {
                        "«${channel.name}» visible de nuevo"
                    }
                )
                load()
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
            }
        }
    }

    fun setTab(tab: ChannelsTab) {
        if (_state.value.tab == tab) return
        _state.value = _state.value.copy(tab = tab)
        load()
    }

    fun setSearch(text: String) {
        _state.value = _state.value.copy(search = text)
        searchInput.value = text
    }

    fun load(more: Boolean = false) {
        val s = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = s.copy(
                loading = !more,
                loadingMore = more,
                error = if (more) s.error else null
            )
            if (!more) {
                refreshSavedIds()
                // The setting is server-wide and can change from Settings or
                // from the web, so re-read it whenever the list reloads.
                loadShowHidden()
            }
            when (s.tab) {
                ChannelsTab.FOLDERS -> loadFolders(s)
                ChannelsTab.SHARED -> loadShares(s)
                ChannelsTab.HIDDEN -> loadHidden(s)
                else -> loadChannelList(s, more)
            }
        }
    }

    /**
     * Fetches the set of channels that already have a local index. The plain
     * `/channels` endpoint doesn't populate the `hasDatabase` flag, so we
     * cross-reference against this set to badge rows correctly.
     */
    private suspend fun refreshSavedIds() {
        runCatching {
            // includeHidden so a hidden channel still gets its "indexed" badge
            // in the Ocultos tab.
            apiCall { api.list(onlySaved = true, includeHidden = true, pageSize = 500) }
        }.onSuccess { list ->
            _state.value = _state.value.copy(savedIds = list.map { it.id }.toSet())
        }
    }

    private fun markIndexed(list: List<ChannelDto>): List<ChannelDto> {
        val ids = _state.value.savedIds
        if (ids.isEmpty()) return list
        return list.map { if (it.hasDatabase || it.id !in ids) it else it.copy(hasDatabase = true) }
    }

    private suspend fun loadChannelList(s: ChannelsUiState, more: Boolean) {
        // "Míos" has no server-side owner filter, so we pull a large page and
        // filter locally; paging is disabled for it.
        val mine = s.tab == ChannelsTab.MINE
        val page = if (more) s.page + 1 else 1
        runCatching {
            apiCallPaged {
                api.list(
                    favoritesOnly = s.tab == ChannelsTab.FAVORITES,
                    search = s.search.ifBlank { null },
                    page = page,
                    pageSize = if (mine) 200 else 50
                )
            }
        }.onSuccess { paged ->
            val filtered = if (mine) paged.items.filter { it.isOwner } else paged.items
            val items = markIndexed(filtered)
            _state.value = _state.value.copy(
                loading = false,
                loadingMore = false,
                channels = if (more) _state.value.channels + items else items,
                page = page,
                hasNext = !mine && paged.page?.hasNext == true
            )
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) return@onFailure
            _state.value = _state.value.copy(
                loading = false, loadingMore = false, error = e.userMessage()
            )
        }
    }

    private suspend fun loadFolders(s: ChannelsUiState) {
        runCatching { apiCall { api.folders() } }
            .onSuccess { data ->
                val enriched = data.copy(
                    folders = data.folders.map { f -> f.copy(channels = markIndexed(f.channels)) },
                    ungrouped = markIndexed(data.ungrouped)
                )
                val query = s.search.trim()
                val filtered = if (query.isBlank()) enriched else filterFolders(enriched, query)
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    folders = filtered,
                    hasNext = false
                )
            }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                _state.value = _state.value.copy(
                    loading = false, loadingMore = false, error = e.userMessage()
                )
            }
    }

    private fun filterFolders(data: ChannelFoldersDto, query: String): ChannelFoldersDto {
        fun match(c: ChannelDto) = c.name?.contains(query, ignoreCase = true) == true
        val folders = data.folders.map { f ->
            f.copy(
                channels = f.channels.filter(::match),
                channelCount = f.channels.count(::match)
            )
        }.filter { it.channels.isNotEmpty() }
        val ungrouped = data.ungrouped.filter(::match)
        return data.copy(
            folders = folders,
            ungrouped = ungrouped,
            totalChannels = folders.sumOf { it.channelCount } + ungrouped.size
        )
    }

    /**
     * The hidden channels, from the dedicated endpoint so they can be unhidden
     * even while the "show hidden channels" setting keeps them out of every
     * other list. Filtering is local: the endpoint takes no search parameter.
     */
    private suspend fun loadHidden(s: ChannelsUiState) {
        runCatching { apiCall { api.hidden() } }
            .onSuccess { list ->
                val query = s.search.trim()
                val items = markIndexed(
                    if (query.isBlank()) list
                    else list.filter { it.name?.contains(query, ignoreCase = true) == true }
                )
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    channels = items,
                    page = 1,
                    hasNext = false
                )
            }
            .onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                _state.value = _state.value.copy(
                    loading = false, loadingMore = false, error = e.userMessage()
                )
            }
    }

    private suspend fun loadShares(s: ChannelsUiState) {
        runCatching {
            apiCallPaged {
                sharesApi.list(filter = s.search.ifBlank { null }, pageSize = 100)
            }
        }.onSuccess { paged ->
            _state.value = _state.value.copy(
                loading = false,
                loadingMore = false,
                shares = paged.items,
                hasNext = false
            )
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) return@onFailure
            _state.value = _state.value.copy(
                loading = false, loadingMore = false, error = e.userMessage()
            )
        }
    }

    fun toggleFavorite(channel: ChannelDto) {
        viewModelScope.launch {
            runCatching {
                if (channel.isFavorite) apiCall { api.removeFavorite(channel.id.toString()) }
                else apiCall { api.addFavorite(channel.id.toString()) }
            }.onSuccess {
                _state.value = _state.value.copy(
                    channels = _state.value.channels.map {
                        if (it.id == channel.id) it.copy(isFavorite = !channel.isFavorite) else it
                    }
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(snackbar = e.userMessage())
            }
        }
    }

    /**
     * Asks the server to scan the channel on Telegram and index the selected
     * media types. Creates the local index first when the channel has none, so
     * a single action covers both "save this channel" and "look for new files".
     */
    fun scanChannel(channel: ChannelDto, options: RefreshChannelRequest) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            prefs.saveScanOptions(options)
            if (!channel.hasDatabase) {
                // Ignore "already exists": we only want to guarantee the index.
                runCatching { apiCall { api.createDatabase(channel.id.toString()) } }
            }
            runCatching { apiCallNullable { api.refresh(channel.id.toString(), options) } }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        snackbar = "Indexando «${channel.name}» en segundo plano"
                    )
                    load()
                }
                .onFailure { e ->
                    val running = (e as? ApiException)?.code == "already_running"
                    _state.value = _state.value.copy(
                        busy = false,
                        snackbar = if (running) {
                            "«${channel.name}» ya se está indexando"
                        } else {
                            e.userMessage()
                        }
                    )
                    if (running) load()
                }
        }
    }

    fun createChannel(title: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { api.create(CreateChannelRequest(title = title)) } }
                .onSuccess {
                    _state.value = _state.value.copy(busy = false, snackbar = "Canal creado")
                    load()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
                }
        }
    }

    fun joinByHash(hash: String) {
        val clean = hash.trim()
            .removePrefix("https://t.me/+")
            .removePrefix("https://t.me/joinchat/")
            .removePrefix("t.me/+")
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { api.join(clean) } }
                .onSuccess {
                    _state.value = _state.value.copy(busy = false, snackbar = "Unido al canal")
                    load()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
                }
        }
    }

    fun leave(channel: ChannelDto, deleteDb: Boolean, deleteOnTelegram: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                apiCall {
                    api.leave(
                        channel.id.toString(),
                        LeaveChannelRequest(deleteDb, deleteOnTelegram)
                    )
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, snackbar = "Canal abandonado")
                load()
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
            }
        }
    }

    fun showDetails(channel: ChannelDto) {
        viewModelScope.launch {
            runCatching { apiCall { api.details(channel.id.toString()) } }
                .onSuccess { d -> _state.value = _state.value.copy(details = d) }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun dismissDetails() {
        _state.value = _state.value.copy(details = null)
    }

    fun toggleFolder(id: Long) {
        val cur = _state.value.expandedFolders
        _state.value = _state.value.copy(
            expandedFolders = if (id in cur) cur - id else cur + id
        )
    }

    // ------------------------------------------------------------------ shares

    fun downloadSharedToServer(share: SharedCollectionDto) {
        val channelId = share.channelId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                apiCall {
                    transfersApi.startDownloads(
                        StartDownloadsRequest(
                            channelId = channelId,
                            fileIds = emptyList(),
                            sharedCollectionId = share.collectionId ?: share.id
                        )
                    )
                }
            }.onSuccess { r ->
                _state.value = _state.value.copy(
                    busy = false,
                    snackbar = "Descarga iniciada (${r.accepted ?: 0} elementos). Mira Transfers"
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
            }
        }
    }

    fun deleteShare(share: SharedCollectionDto) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { sharesApi.delete(share.id) } }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        snackbar = "Colección eliminada",
                        shares = _state.value.shares.filterNot { it.id == share.id }
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
                }
        }
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}
