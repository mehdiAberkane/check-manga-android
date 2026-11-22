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
import androidx.compose.ui.res.stringResource
import com.mangareminder.R

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
                stringResource(
                    when (state) {
                        is UpdateState.Available, is UpdateState.NeedsPermission -> R.string.update_available
                        is UpdateState.Failed -> R.string.update_failed_title
                        else -> R.string.update_title
                    },
                ),
            )
        },
        text = {
            when (state) {
                UpdateState.Idle, UpdateState.Checking -> Busy(stringResource(R.string.update_checking))
                UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                is UpdateState.Available -> ReleaseNotes(state.info)
                is UpdateState.NeedsPermission ->
                    Text(stringResource(R.string.update_permission, state.info.versionName))
                is UpdateState.Downloading -> Column {
                    Text(stringResource(R.string.update_downloading, state.info.versionName))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                UpdateState.Installing -> Busy(stringResource(R.string.update_installing))
                is UpdateState.Failed -> Text(state.message.text())
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { onInstall(state.info) }) { Text(stringResource(R.string.action_update)) }
                is UpdateState.NeedsPermission ->
                    TextButton(onClick = { onOpenPermission(state.info) }) { Text(stringResource(R.string.action_open_settings)) }
                is UpdateState.Downloading -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                UpdateState.Installing -> {}
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available || state is UpdateState.NeedsPermission) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) }
            }
        },
    )
}

@Composable
private fun ReleaseNotes(info: UpdateInfo) {
    Column {
        Text(stringResource(R.string.update_versions, info.versionName, BuildConfig.VERSION_NAME))
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
