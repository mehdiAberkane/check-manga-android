package com.mangareminder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mangareminder.BuildConfig
import com.mangareminder.update.UpdateInfo
import com.mangareminder.update.UpdateState

/** Dialogue unique qui suit toutes les étapes de la mise à jour. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: (UpdateInfo) -> Unit,
    onOpenPermission: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = state is UpdateState.Downloading || state == UpdateState.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !busy),
        title = {
            Text(
                when (state) {
                    is UpdateState.Available, is UpdateState.NeedsPermission -> "Mise à jour disponible"
                    is UpdateState.Failed -> "Mise à jour impossible"
                    else -> "Mise à jour"
                },
            )
        },
        text = {
            when (state) {
                UpdateState.Idle, UpdateState.Checking -> Busy("Recherche d'une nouvelle version…")
                UpdateState.UpToDate -> Text("Tu as déjà la dernière version (${BuildConfig.VERSION_NAME}).")
                is UpdateState.Available -> ReleaseNotes(state.info)
                is UpdateState.NeedsPermission -> Text(
                    "Pour installer la version ${state.info.versionName}, autorise Manga Reminder à installer " +
                        "des applications (réglage Android « Installer des applis inconnues »), puis reviens " +
                        "et appuie sur « Mettre à jour ». Ta collection est conservée.",
                )
                is UpdateState.Downloading -> Column {
                    Text("Téléchargement de la version ${state.info.versionName}…")
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                UpdateState.Installing -> Busy("Installation… Android va demander de confirmer la mise à jour.")
                is UpdateState.Failed -> Text(state.message)
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { onInstall(state.info) }) { Text("Mettre à jour") }
                is UpdateState.NeedsPermission ->
                    TextButton(onClick = { onOpenPermission(state.info) }) { Text("Ouvrir les réglages") }
                is UpdateState.Downloading -> TextButton(onClick = onDismiss) { Text("Annuler") }
                UpdateState.Installing -> {}
                else -> TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available || state is UpdateState.NeedsPermission) {
                TextButton(onClick = onDismiss) { Text("Plus tard") }
            }
        },
    )
}

@Composable
private fun ReleaseNotes(info: UpdateInfo) {
    Column {
        Text("Version ${info.versionName} (installée : ${BuildConfig.VERSION_NAME}). Ta collection est conservée.")
        if (info.notes.isNotBlank()) {
            Text(
                info.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun Busy(message: String) {
    Column {
        Text(message)
        CircularProgressIndicator(Modifier.padding(top = 16.dp))
    }
}
