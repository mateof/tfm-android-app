package com.mateof.tfm.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mateof.tfm.core.apiCall
import com.mateof.tfm.core.userMessage
import com.mateof.tfm.data.api.LocalApi
import com.mateof.tfm.data.model.ApiFileDto
import com.mateof.tfm.data.model.BreadcrumbDto
import com.mateof.tfm.data.model.CreateFolderRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalApiEntryPoint {
    fun localApi(): LocalApi
}

@Composable
private fun rememberLocalApi(): LocalApi {
    val context = LocalContext.current
    return remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LocalApiEntryPoint::class.java
        ).localApi()
    }
}

/**
 * Lets the user navigate the server's local storage tree and pick a
 * destination folder. Returns the chosen path via [onPick] as a relative
 * path (no leading slash; empty string for the root), which is the format
 * every local-files endpoint under `api/v1/local` expects.
 */
@Composable
fun LocalFolderPicker(
    title: String = "Elegir carpeta en el servidor",
    subtitle: String? = null,
    confirmLabel: String = "Guardar aquí",
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val api = rememberLocalApi()
    val scope = rememberCoroutineScope()

    var path by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<ApiFileDto>>(emptyList()) }
    var breadcrumbs by remember { mutableStateOf(listOf(BreadcrumbDto("Local", "", null))) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun browse(target: String) {
        loading = true
        error = null
        scope.launch {
            runCatching {
                apiCall {
                    api.browse(
                        path = target.ifBlank { null },
                        filesOnly = false,
                        pageSize = 200
                    )
                }
            }.onSuccess { contents ->
                // The local API returns currentPath with a leading slash;
                // keep the state as a plain relative path so it round-trips
                // to browse() and to the STRM destination.
                path = (contents.currentPath ?: target).trimStart('/')
                folders = contents.items.filter { !it.isFile }
                breadcrumbs = contents.breadcrumbs.ifEmpty {
                    listOf(BreadcrumbDto("Local", "", null))
                }
                loading = false
            }.onFailure { e ->
                error = e.userMessage()
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { browse("") }

    fun createFolder(name: String) {
        scope.launch {
            runCatching {
                apiCall { api.createFolder(CreateFolderRequest(path, name)) }
            }.onSuccess { browse(path) }
                .onFailure { e -> error = e.userMessage() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle ?: "Navega hasta la carpeta donde quieres guardar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    breadcrumbs.forEachIndexed { i, crumb ->
                        Text(
                            text = crumb.name.ifBlank { "Local" },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (i == breadcrumbs.lastIndex)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (i == breadcrumbs.lastIndex) FontWeight.Bold else null,
                            modifier = Modifier.clickable { browse(crumb.path.trimStart('/')) }
                        )
                        if (i != breadcrumbs.lastIndex) {
                            Text(
                                "  ›  ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(4.dp))

                when {
                    loading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }

                    error != null -> Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    folders.isEmpty() -> Text(
                        "No hay subcarpetas aquí. Puedes guardar aquí o crear una nueva.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(folders, key = { it.id }) { folder ->
                            ListItem(
                                headlineContent = { Text(folder.name) },
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                },
                                // ApiFileDto.id for local folders is already
                                // the relative path (e.g. "movies/action").
                                modifier = Modifier.clickable { browse(folder.id) }
                            )
                        }
                    }
                }

                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.CreateNewFolder, null)
                    Text("  Nueva carpeta aquí")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPick(path) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    if (creating) {
        InputDialog(
            title = "Nueva carpeta",
            label = "Nombre",
            confirmLabel = "Crear",
            onConfirm = { createFolder(it); creating = false },
            onDismiss = { creating = false }
        )
    }
}
