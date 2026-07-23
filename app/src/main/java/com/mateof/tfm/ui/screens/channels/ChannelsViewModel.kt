package com.mateof.tfm.ui.screens.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.apiCallPaged
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.ChannelsApi
import com.mateof.tfm.data.model.ChannelDto
import com.mateof.tfm.data.model.CreateChannelRequest
import com.mateof.tfm.data.model.LeaveChannelRequest
import com.mateof.tfm.data.model.RefreshChannelRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChannelsTab(val label: String) {
    SAVED("Guardados"), MINE("Míos"), ALL("Todos"), FAVORITES("Favoritos")
}

data class ChannelsUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    val tab: ChannelsTab = ChannelsTab.SAVED,
    val search: String = "",
    val page: Int = 1,
    val hasNext: Boolean = false,
    val busy: Boolean = false,
    val snackbar: String? = null,
    val details: ChannelDto? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val api: ChannelsApi
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
        // "Míos" has no server-side owner filter, so we pull a large page and
        // filter locally; paging is disabled for it.
        val mine = s.tab == ChannelsTab.MINE
        val page = if (more) s.page + 1 else 1
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = s.copy(
                loading = !more,
                loadingMore = more,
                error = if (more) s.error else null
            )
            runCatching {
                apiCallPaged {
                    api.list(
                        onlySaved = s.tab == ChannelsTab.SAVED,
                        favoritesOnly = s.tab == ChannelsTab.FAVORITES,
                        search = s.search.ifBlank { null },
                        page = page,
                        pageSize = if (mine) 200 else 50
                    )
                }
            }.onSuccess { paged ->
                val items = if (mine) {
                    paged.items.filter { it.isOwner }
                } else paged.items
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

    fun refreshChannel(channel: ChannelDto) {
        viewModelScope.launch {
            runCatching { apiCall { api.refresh(channel.id.toString(), RefreshChannelRequest()) } }
                .onSuccess {
                    _state.value = _state.value.copy(
                        snackbar = "Indexando «${channel.name}» en segundo plano"
                    )
                }
                .onFailure { e -> _state.value = _state.value.copy(snackbar = e.userMessage()) }
        }
    }

    fun createDatabase(channel: ChannelDto) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { apiCall { api.createDatabase(channel.id.toString()) } }
                .onSuccess {
                    _state.value = _state.value.copy(busy = false, snackbar = "Índice creado")
                    load()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, snackbar = e.userMessage())
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

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}
