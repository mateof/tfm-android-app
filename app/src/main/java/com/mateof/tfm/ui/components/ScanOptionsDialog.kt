package com.mateof.tfm.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mateof.tfm.data.model.RefreshChannelRequest

/**
 * Lets the user pick which media types the server should index when it scans a
 * channel, mirroring the web's "Refresh Channel Data" modal.
 *
 * The scan only adds messages the index doesn't know about yet, so running it
 * again is cheap; [RefreshChannelRequest.force] rescans the whole history
 * instead of starting after the newest indexed message.
 */
@Composable
fun ScanOptionsDialog(
    onConfirm: (RefreshChannelRequest) -> Unit,
    onDismiss: () -> Unit,
    channelName: String? = null,
    title: String = "Actualizar índice",
    confirmLabel: String = "Escanear",
    initial: RefreshChannelRequest = RefreshChannelRequest(),
    showForce: Boolean = true
) {
    var includeVideo by rememberSaveable { mutableStateOf(initial.includeVideo) }
    var includeAudio by rememberSaveable { mutableStateOf(initial.includeAudio) }
    var includePhotos by rememberSaveable { mutableStateOf(initial.includePhotos) }
    var includeDocuments by rememberSaveable { mutableStateOf(initial.includeDocuments) }
    var force by rememberSaveable { mutableStateOf(false) }
    val anySelected = includeVideo || includeAudio || includePhotos || includeDocuments

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (channelName.isNullOrBlank()) {
                        "Elige qué tipos de contenido quieres indexar."
                    } else {
                        "Elige qué tipos de contenido quieres indexar de «$channelName»."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ScanCheckbox("Vídeo", "MP4, MKV…", includeVideo) { includeVideo = it }
                ScanCheckbox("Audio", "Música, notas de voz", includeAudio) { includeAudio = it }
                ScanCheckbox("Fotos", "JPG, PNG…", includePhotos) { includePhotos = it }
                ScanCheckbox("Documentos", "PDF, ZIP…", includeDocuments) { includeDocuments = it }
                if (showForce) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { force = !force }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reescanear todo el canal")
                            Text(
                                "Por defecto sólo se miran los mensajes posteriores al último " +
                                    "indexado. Actívalo si faltan ficheros antiguos (más lento).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = force, onCheckedChange = { force = it })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sólo se añaden ficheros nuevos: los ya indexados no se duplican.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = anySelected,
                onClick = {
                    onConfirm(
                        RefreshChannelRequest(
                            includeDocuments = includeDocuments,
                            includeAudio = includeAudio,
                            includeVideo = includeVideo,
                            includePhotos = includePhotos,
                            force = force
                        )
                    )
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ScanCheckbox(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
