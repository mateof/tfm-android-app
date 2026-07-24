package com.mateof.tfm.ui.screens.video

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.navigation.NavHostController
import com.mateof.tfm.data.prefs.ServerPreferences
import com.mateof.tfm.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext context: android.content.Context,
    prefs: ServerPreferences,
    private val audioConnection: PlayerConnection
) : ViewModel() {

    val url: String = savedState.get<String>("url") ?: ""
    val title: String = savedState.get<String>("title") ?: ""

    val player: ExoPlayer

    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks = _tracks.asStateFlow()

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode = _resizeMode.asStateFlow()

    init {
        // Stop background audio so the two players don't fight for focus.
        audioConnection.controller.value?.pause()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply {
                val key = prefs.current.apiKey
                if (key.isNotBlank()) setDefaultRequestProperties(mapOf("X-Api-Key" to key))
            }
        // NextRenderersFactory adds FFmpeg software decoders. EXTENSION_RENDERER_MODE_ON
        // keeps hardware decoders first (H.264/HEVC/VP9) and falls back to FFmpeg for
        // codecs the device can't handle (Xvid/DivX video, AC3/EAC3/DTS audio, …),
        // so MKV/AVI/etc. play regardless of the codecs inside.
        val renderersFactory = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        // Broaden container sniffing (constant-bitrate seek for AVI/MP3, etc.).
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory), extractorsFactory)
            )
            .build()
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                _tracks.value = tracks
            }
        })
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        )
        player.prepare()
        player.playWhenReady = true
    }

    fun setResizeMode(mode: Int) {
        _resizeMode.value = mode
    }

    /** Applies a track override for a specific audio group. */
    fun selectAudioTrack(group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    /**
     * Enables the given text track, or disables subtitles entirely when [group]
     * is null.
     */
    fun selectSubtitleTrack(group: Tracks.Group?, trackIndex: Int = 0) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (group == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        }
        player.trackSelectionParameters = builder.build()
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    navController: NavHostController,
    vm: VideoPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val resizeMode by vm.resizeMode.collectAsStateWithLifecycle()

    // Immersive fullscreen while the video screen is visible.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler { navController.popBackStack() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = vm.player
                    keepScreenOn = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // Enable the built-in subtitle rendering surface.
                    subtitleView?.setApplyEmbeddedStyles(true)
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Atrás",
                tint = Color.White
            )
        }
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.End
        ) {
            SubtitleMenu(tracks = tracks, onPick = vm::selectSubtitleTrack)
            AudioMenu(tracks = tracks, onPick = vm::selectAudioTrack)
            AspectRatioMenu(current = resizeMode, onPick = vm::setResizeMode)
        }
    }
}

// ---------------------------------------------------------------------------
// Menus
// ---------------------------------------------------------------------------

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AspectRatioMenu(current: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Outlined.AspectRatio,
            contentDescription = "Relación de aspecto",
            tint = Color.White
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        AspectRatioOption("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT, current) {
            onPick(it); open = false
        }
        AspectRatioOption("Rellenar", AspectRatioFrameLayout.RESIZE_MODE_FILL, current) {
            onPick(it); open = false
        }
        AspectRatioOption("Recortar (zoom)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, current) {
            onPick(it); open = false
        }
        AspectRatioOption("Ancho fijo", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, current) {
            onPick(it); open = false
        }
        AspectRatioOption("Alto fijo", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, current) {
            onPick(it); open = false
        }
    }
}

@Composable
private fun AspectRatioOption(label: String, mode: Int, current: Int, onClick: (Int) -> Unit) {
    DropdownMenuItem(
        text = { Text(if (mode == current) "✓  $label" else label) },
        onClick = { onClick(mode) }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AudioMenu(tracks: Tracks, onPick: (Tracks.Group, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Outlined.Audiotrack,
            contentDescription = "Audio",
            tint = Color.White
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (audioGroups.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No hay pistas de audio") },
                enabled = false,
                onClick = { open = false }
            )
            return@DropdownMenu
        }
        var idx = 0
        audioGroups.forEach { group ->
            repeat(group.length) { trackIndex ->
                val selected = group.isTrackSelected(trackIndex)
                val label = formatTrackLabel(
                    group.getTrackFormat(trackIndex),
                    fallback = "Pista ${++idx}"
                )
                DropdownMenuItem(
                    text = { Text(if (selected) "✓  $label" else label) },
                    enabled = group.isTrackSupported(trackIndex),
                    onClick = { onPick(group, trackIndex); open = false }
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun SubtitleMenu(
    tracks: Tracks,
    onPick: (Tracks.Group?, Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = textGroups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    IconButton(onClick = { open = true }) {
        Icon(
            Icons.Outlined.ClosedCaption,
            contentDescription = "Subtítulos",
            tint = Color.White
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(if (!anySelected) "✓  Sin subtítulos" else "Sin subtítulos") },
            onClick = { onPick(null, 0); open = false }
        )
        if (textGroups.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No hay subtítulos disponibles") },
                enabled = false,
                onClick = { open = false }
            )
            return@DropdownMenu
        }
        var idx = 0
        textGroups.forEach { group ->
            repeat(group.length) { trackIndex ->
                val selected = group.isTrackSelected(trackIndex)
                val label = formatTrackLabel(
                    group.getTrackFormat(trackIndex),
                    fallback = "Subtítulo ${++idx}"
                )
                DropdownMenuItem(
                    text = { Text(if (selected) "✓  $label" else label) },
                    enabled = group.isTrackSupported(trackIndex),
                    onClick = { onPick(group, trackIndex); open = false }
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun formatTrackLabel(
    format: androidx.media3.common.Format,
    fallback: String
): String {
    format.label?.takeIf { it.isNotBlank() }?.let { return it }
    val languageLabel = format.language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let {
            runCatching { Locale.forLanguageTag(it).displayLanguage }
                .getOrNull()
                ?.takeIf { d -> d.isNotBlank() }
                ?: it
        }
    val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase()
    return listOfNotNull(languageLabel, codec).joinToString(" · ").ifBlank { fallback }
}
