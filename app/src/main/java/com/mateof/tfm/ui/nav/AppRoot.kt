package com.mateof.tfm.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mateof.tfm.ui.components.MiniPlayer
import com.mateof.tfm.ui.screens.channels.ChannelsScreen
import com.mateof.tfm.ui.screens.files.FilesScreen
import com.mateof.tfm.ui.screens.gate.GateScreen
import com.mateof.tfm.ui.screens.local.LocalScreen
import com.mateof.tfm.ui.screens.login.LoginScreen
import com.mateof.tfm.ui.screens.messages.MessagesScreen
import com.mateof.tfm.ui.screens.player.PlayerScreen
import com.mateof.tfm.ui.screens.playlists.PlaylistDetailScreen
import com.mateof.tfm.ui.screens.playlists.PlaylistsScreen
import com.mateof.tfm.ui.screens.settings.SettingsScreen
import com.mateof.tfm.ui.screens.setup.SetupScreen
import com.mateof.tfm.ui.screens.transfers.TransfersScreen
import com.mateof.tfm.ui.screens.video.VideoPlayerScreen

object Routes {
    const val GATE = "gate"
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val CHANNELS = "channels"
    const val LOCAL = "local"
    const val TRANSFERS = "transfers"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"
    const val PLAYER = "player"

    fun files(channelId: String, name: String, path: String = "/") =
        "files/$channelId?name=${Uri.encode(name)}&path=${Uri.encode(path)}"

    fun messages(channelId: String, name: String) =
        "messages/$channelId?name=${Uri.encode(name)}"

    fun playlist(id: String) = "playlist/$id"

    fun video(url: String, title: String) =
        "video?url=${Uri.encode(url)}&title=${Uri.encode(title)}"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val TABS = listOf(
    TabItem(Routes.CHANNELS, "Canales", Icons.Filled.Folder),
    TabItem(Routes.LOCAL, "Local", Icons.Filled.Storage),
    TabItem(Routes.TRANSFERS, "Transfers", Icons.Filled.SwapVert),
    TabItem(Routes.PLAYLISTS, "Listas", Icons.Filled.LibraryMusic),
    TabItem(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings)
)

@Composable
fun AppRoot(vm: AppRootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTopLevel = TABS.any { it.route == currentRoute }
    // The mini player follows the user everywhere except the full-screen
    // audio/video players themselves.
    val showMiniPlayer = nowPlaying.hasMedia &&
        currentRoute != Routes.PLAYER &&
        currentRoute?.startsWith("video") != true

    LaunchedEffect(Unit) { vm.onAppVisible() }

    Scaffold(
        bottomBar = {
            Column {
                if (showMiniPlayer) {
                    MiniPlayer(
                        nowPlaying = nowPlaying,
                        onToggle = vm::togglePlayPause,
                        onOpen = { navController.navigate(Routes.PLAYER) },
                        onClose = vm::stopPlayback
                    )
                }
                AnimatedVisibility(visible = isTopLevel) {
                    NavigationBar {
                        TABS.forEach { tab ->
                            val active = summary?.isWorking == true && tab.route == Routes.TRANSFERS
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    BadgedBox(badge = {
                                        if (active) {
                                            val count = (summary?.activeDownloads ?: 0) +
                                                (summary?.activeUploads ?: 0)
                                            Badge { Text(count.toString()) }
                                        }
                                    }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.GATE,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.GATE) { GateScreen(navController) }
            composable(Routes.SETUP) { SetupScreen(navController) }
            composable(Routes.LOGIN) { LoginScreen(navController) }
            composable(Routes.CHANNELS) { ChannelsScreen(navController) }
            composable(Routes.LOCAL) { LocalScreen(navController) }
            composable(Routes.TRANSFERS) { TransfersScreen(navController) }
            composable(Routes.PLAYLISTS) { PlaylistsScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.PLAYER) { PlayerScreen(navController) }
            composable("files/{channelId}?name={name}&path={path}") { FilesScreen(navController) }
            composable("messages/{channelId}?name={name}") { MessagesScreen(navController) }
            composable("playlist/{id}") { PlaylistDetailScreen(navController) }
            composable("video?url={url}&title={title}") { VideoPlayerScreen(navController) }
        }
    }
}

fun NavHostController.navigateToMain() {
    navigate(Routes.CHANNELS) {
        popUpTo(0) { inclusive = true }
    }
}
