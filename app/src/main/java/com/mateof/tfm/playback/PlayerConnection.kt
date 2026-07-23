package com.mateof.tfm.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class QueueTrack(
    val url: String,
    val title: String,
    val artist: String? = null,
    val mediaId: String = url
)

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false
)

/**
 * App-wide connection to [PlaybackService]'s media session. Keeps a tiny
 * [NowPlaying] state for the mini player; screens that need more detail talk
 * to [controller] directly.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _nowPlaying.value = NowPlaying(
                title = player.mediaMetadata.title?.toString(),
                artist = player.mediaMetadata.artist?.toString(),
                isPlaying = player.isPlaying,
                hasMedia = player.mediaItemCount > 0
            )
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                val c = future.get()
                c.addListener(listener)
                _controller.value = c
            }
        }, MoreExecutors.directExecutor())
    }

    fun playQueue(tracks: List<QueueTrack>, startIndex: Int = 0) {
        val c = _controller.value ?: return
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setUri(t.url)
                .setMediaId(t.mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = _controller.value ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun stopAndClear() {
        val c = _controller.value ?: return
        c.stop()
        c.clearMediaItems()
    }
}
