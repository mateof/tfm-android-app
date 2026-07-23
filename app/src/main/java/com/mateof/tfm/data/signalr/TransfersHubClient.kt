package com.mateof.tfm.data.signalr

import com.mateof.tfm.data.model.SpeedPointDto
import com.mateof.tfm.data.model.TransferSummaryDto
import com.mateof.tfm.data.model.TransfersSnapshotDto
import com.mateof.tfm.data.prefs.ServerPreferences
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

enum class HubState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Wraps the `/hubs/transfers` SignalR connection: exposes the live snapshot,
 * summary and speed samples as flows and keeps the connection alive with a
 * simple exponential-backoff reconnect loop.
 */
@Singleton
class TransfersHubClient @Inject constructor(
    private val prefs: ServerPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var hub: HubConnection? = null
    private var reconnectJob: Job? = null
    private var desired = false

    private val _state = MutableStateFlow(HubState.DISCONNECTED)
    val state: StateFlow<HubState> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow<TransfersSnapshotDto?>(null)
    val snapshot: StateFlow<TransfersSnapshotDto?> = _snapshot.asStateFlow()

    private val _summary = MutableStateFlow<TransferSummaryDto?>(null)
    val summary: StateFlow<TransferSummaryDto?> = _summary.asStateFlow()

    private val _speed = MutableSharedFlow<Pair<SpeedPointDto, SpeedPointDto>>(extraBufferCapacity = 64)
    val speed = _speed.asSharedFlow()

    /** Ask the client to be connected; safe to call repeatedly. */
    fun connect() {
        desired = true
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch { maintainConnection() }
    }

    fun disconnect() {
        desired = false
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch { mutex.withLock { teardown() } }
    }

    private suspend fun maintainConnection() {
        var backoffMs = 1000L
        while (scope.isActive && desired) {
            val connected = mutex.withLock {
                if (hub?.connectionState == HubConnectionState.CONNECTED) return@withLock true
                teardown()
                runCatching { startConnection() }.isSuccess
            }
            if (connected) {
                backoffMs = 1000L
                // Watch until the connection drops.
                while (desired && hub?.connectionState == HubConnectionState.CONNECTED) {
                    delay(2000)
                }
                _state.value = HubState.DISCONNECTED
            } else {
                _state.value = HubState.DISCONNECTED
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private suspend fun startConnection() {
        val cfg = prefs.current
        if (cfg.normalizedBaseUrl.isBlank()) throw IllegalStateException("Server not configured")
        _state.value = HubState.CONNECTING

        val connection = HubConnectionBuilder
            .create("${cfg.normalizedBaseUrl}/hubs/transfers")
            .withAccessTokenProvider(Single.defer { Single.just(cfg.apiKey) })
            .build()

        connection.on(
            "TransfersSnapshot",
            { s: TransfersSnapshotDto ->
                _snapshot.value = s
                s.summary?.let { _summary.value = it }
            },
            TransfersSnapshotDto::class.java
        )
        connection.on(
            "TransferSummary",
            { s: TransferSummaryDto -> _summary.value = s },
            TransferSummaryDto::class.java
        )
        connection.on(
            "SpeedHistoryPoint",
            { down: SpeedPointDto, up: SpeedPointDto -> _speed.tryEmit(down to up) },
            SpeedPointDto::class.java,
            SpeedPointDto::class.java
        )
        connection.onClosed { _state.value = HubState.DISCONNECTED }

        connection.start().await()
        hub = connection
        _state.value = HubState.CONNECTED
    }

    private fun teardown() {
        runCatching { hub?.stop() }
        hub = null
    }
}
