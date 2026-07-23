package com.mateof.tfm.ui.nav

import androidx.lifecycle.ViewModel
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.data.signalr.TransfersHubClient
import com.mateof.tfm.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val hub: TransfersHubClient,
    private val prefs: ServerPreferences,
    private val player: PlayerConnection
) : ViewModel() {

    val summary = hub.summary
    val nowPlaying = player.nowPlaying

    fun onAppVisible() {
        if (prefs.current.configured) hub.connect()
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun stopPlayback() = player.stopAndClear()
}
