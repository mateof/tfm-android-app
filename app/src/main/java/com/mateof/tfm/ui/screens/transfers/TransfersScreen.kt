package com.mateof.tfm.ui.screens.transfers

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.mateof.tfm.core.Format
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.TransfersApi
import com.mateof.tfm.data.model.TransferDto
import com.mateof.tfm.data.model.TransfersSnapshotDto
import com.mateof.tfm.data.signalr.HubState
import com.mateof.tfm.data.signalr.TransfersHubClient
import com.mateof.tfm.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val hub: TransfersHubClient,
    private val api: TransfersApi
) : ViewModel() {

    val snapshot = hub.snapshot
    val summary = hub.summary
    val hubState = hub.state

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    init {
        hub.connect()
        // Fetch an initial snapshot over REST in parallel with the hub handshake,
        // so the screen paints immediately even if the socket takes a moment.
        viewModelScope.launch {
            runCatching { apiCall { api.snapshot() } }
        }
    }

    private fun <T> exec(action: suspend () -> T) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { e -> _snackbar.value = e.userMessage() }
        }
    }

    fun pauseAll() = exec { apiCall { api.pauseDownloads() } }
    fun resumeAll() = exec { apiCall { api.resumeDownloads() } }
    fun stopAll() = exec { apiCall { api.stopDownloads() } }
    fun clearFinished() = exec { apiCall { api.clear("all") } }
    fun pause(t: TransferDto) { t.id?.let { id -> exec { apiCall { api.pause(id) } } } }
    fun cancel(t: TransferDto) { t.id?.let { id -> exec { apiCall { api.cancel(id) } } } }
    fun retry(t: TransferDto) { t.id?.let { id -> exec { apiCall { api.retry(id) } } } }

    fun snackbarShown() {
        _snackbar.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(navController: NavHostController, vm: TransfersViewModel = hiltViewModel()) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val hubState by vm.hubState.collectAsStateWithLifecycle()
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbar) {
        snackbar?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Transferencias") },
                actions = {
                    if (summary?.downloadsPaused == true) {
                        IconButton(onClick = vm::resumeAll) {
                            Icon(Icons.Outlined.PlayArrow, "Reanudar descargas")
                        }
                    } else {
                        IconButton(onClick = vm::pauseAll) {
                            Icon(Icons.Outlined.Pause, "Pausar descargas")
                        }
                    }
                    IconButton(onClick = vm::stopAll) {
                        Icon(Icons.Outlined.Stop, "Detener todo")
                    }
                    IconButton(onClick = vm::clearFinished) {
                        Icon(Icons.Outlined.ClearAll, "Limpiar terminadas")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Speed header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedStat(
                        icon = { Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary) },
                        label = summary?.downloadSpeed
                            ?: Format.speed(summary?.downloadBytesPerSecond),
                        sub = "${summary?.activeDownloads ?: 0} activas · ${summary?.queuedDownloads ?: 0} en cola"
                    )
                    SpeedStat(
                        icon = { Icon(Icons.Filled.Upload, null, tint = MaterialTheme.colorScheme.secondary) },
                        label = summary?.uploadSpeed
                            ?: Format.speed(summary?.uploadBytesPerSecond),
                        sub = "${summary?.activeUploads ?: 0} activas · ${summary?.queuedUploads ?: 0} en cola"
                    )
                }
            }

            if (hubState != HubState.CONNECTED) {
                Text(
                    text = when (hubState) {
                        HubState.CONNECTING -> "Conectando con el hub en tiempo real…"
                        else -> "Sin conexión en tiempo real; reintentando…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            val snap = snapshot
            val sections = buildList {
                if (snap != null) {
                    if (snap.downloads.isNotEmpty()) add("Descargas" to snap.downloads)
                    if (snap.queuedDownloads.isNotEmpty()) add("Descargas en cola" to snap.queuedDownloads)
                    if (snap.uploads.isNotEmpty()) add("Subidas" to snap.uploads)
                    if (snap.queuedUploads.isNotEmpty()) add("Subidas en cola" to snap.queuedUploads)
                    if (snap.tasks.isNotEmpty()) add("Tareas" to snap.tasks)
                }
            }

            if (sections.isEmpty()) {
                EmptyState("No hay transferencias.\nLas descargas y subidas aparecerán aquí en tiempo real.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sections.forEach { (title, list) ->
                        item(key = "header_$title") {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(list, key = { "${title}_${it.id}" }) { t ->
                            TransferCard(
                                transfer = t,
                                onPause = { vm.pause(t) },
                                onCancel = { vm.cancel(t) },
                                onRetry = { vm.retry(t) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SpeedStat(icon: @Composable () -> Unit, label: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text(
                label.ifBlank { "0 KB/s" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransferCard(
    transfer: TransferDto,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transfer.name ?: "(sin nombre)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            transfer.channelName?.let { append(it) }
                            transfer.state?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                            if (transfer.kind == "task" && transfer.totalItems != null) {
                                append(" · ${transfer.executedItems ?: 0}/${transfer.totalItems}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val state = transfer.state?.lowercase()
                if (state == "working" || state == "pending") {
                    if (transfer.kind == "download") {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Outlined.Pause, "Pausar")
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Cancel, "Cancelar")
                    }
                } else if (state == "paused" || state == "canceled" || state == "error") {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, "Reintentar")
                    }
                }
            }
            val progress = (transfer.progress ?: 0).coerceIn(0, 100)
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
            Text(
                "${transfer.transmittedText ?: Format.bytes(transfer.transmitted)} / " +
                    "${transfer.sizeText ?: Format.bytes(transfer.size)}  ·  $progress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
