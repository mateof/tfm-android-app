package com.mateof.tfm.ui.screens.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.mateof.tfm.data.api.ChannelsApi
import com.mateof.tfm.data.api.TransfersApi
import com.mateof.tfm.data.model.ChannelMessageDto
import com.mateof.tfm.data.model.DownloadMessagesRequest
import com.mateof.tfm.ui.components.EmptyState
import com.mateof.tfm.ui.components.ErrorState
import com.mateof.tfm.ui.components.LoadingBox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val messages: List<ChannelMessageDto> = emptyList(),
    val onlyMedia: Boolean = true,
    val snackbar: String? = null,
    val channelName: String = ""
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channelsApi: ChannelsApi,
    private val transfersApi: TransfersApi
) : ViewModel() {

    private val channelId: String = savedState.get<String>("channelId") ?: ""

    private val _state = MutableStateFlow(
        MessagesUiState(channelName = savedState.get<String>("name") ?: "")
    )
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load(more: Boolean = false) {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(loading = !more, loadingMore = more, error = null)
            runCatching {
                apiCall {
                    channelsApi.messages(
                        channelId,
                        limit = 50,
                        offset = if (more) s.messages.size else 0,
                        onlyMedia = s.onlyMedia
                    )
                }
            }.onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    messages = if (more) _state.value.messages + list else list
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false, loadingMore = false, error = e.userMessage()
                )
            }
        }
    }

    fun setOnlyMedia(value: Boolean) {
        _state.value = _state.value.copy(onlyMedia = value)
        load()
    }

    fun downloadMessage(message: ChannelMessageDto) {
        val chatId = channelId.toLongOrNull() ?: return
        viewModelScope.launch {
            runCatching {
                apiCall {
                    transfersApi.downloadMessages(
                        DownloadMessagesRequest(
                            chatId = chatId,
                            messageIds = listOf(message.id)
                        )
                    )
                }
            }.onSuccess {
                _state.value = _state.value.copy(
                    snackbar = "Descarga encolada (ver Transfers)"
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(snackbar = e.userMessage())
            }
        }
    }

    fun snackbarShown() {
        _state.value = _state.value.copy(snackbar = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(navController: NavHostController, vm: MessagesViewModel = hiltViewModel()) {
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
                    Column {
                        Text(
                            state.channelName.ifBlank { "Mensajes" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Historial reciente de Telegram",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = state.onlyMedia,
                    onClick = { vm.setOnlyMedia(true) },
                    label = { Text("Con adjuntos") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = !state.onlyMedia,
                    onClick = { vm.setOnlyMedia(false) },
                    label = { Text("Todos") }
                )
            }

            when {
                state.loading -> LoadingBox(label = "Cargando mensajes…")
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.load() })
                state.messages.isEmpty() -> EmptyState("Sin mensajes")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.messages, key = { it.id }) { message ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            message.fileName
                                                ?: message.text?.take(120)?.ifBlank { "(mensaje)" }
                                                ?: "(mensaje)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            buildString {
                                                append(Format.date(message.date))
                                                message.mediaType?.let { append(" · $it") }
                                                message.fileSize?.let {
                                                    append(" · ${Format.bytes(it)}")
                                                }
                                                message.from?.let { append(" · $it") }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (message.hasMedia) {
                                        IconButton(onClick = { vm.downloadMessage(message) }) {
                                            Icon(
                                                Icons.Outlined.CloudDownload,
                                                "Descargar al servidor"
                                            )
                                        }
                                    }
                                }
                                if (message.fileName != null && !message.text.isNullOrBlank()) {
                                    Text(
                                        message.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        TextButton(
                            onClick = { vm.load(more = true) },
                            enabled = !state.loadingMore,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text(if (state.loadingMore) "Cargando…" else "Cargar más")
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
