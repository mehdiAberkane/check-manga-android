package com.mangareminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mangareminder.BuildConfig
import com.mangareminder.R
import com.mangareminder.update.UpdateInfo
import com.mangareminder.update.UpdateState

/** Dialogue unique qui suit la recherche de mise à jour. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
    autoCheck: Boolean = true,
    onAutoCheck: (Boolean) -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (state) {
                        is UpdateState.Available -> R.string.update_available
                        is UpdateState.Failed -> R.string.update_failed_title
                        else -> R.string.update_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    UpdateState.Idle, UpdateState.Checking -> Column {
                        Text(stringResource(R.string.update_checking))
                        CircularProgressIndicator(Modifier.padding(top = 16.dp))
                    }
                    UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                    is UpdateState.Available -> ReleaseDetails(state.info)
                    is UpdateState.Failed -> Text(state.message.text())
                }
                if (state != UpdateState.Checking && state != UpdateState.Idle) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = autoCheck, role = Role.Switch, onValueChange = onAutoCheck),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.update_auto_check),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = autoCheck, onCheckedChange = null)
                    }
                }
            }
        },
        confirmButton = {
            if (state is UpdateState.Available) {
                TextButton(onClick = { onDownload(state.info) }) { Text(stringResource(R.string.action_download)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_later)) }
            }
        },
    )
}

@Composable
private fun ReleaseDetails(info: UpdateInfo) {
    Column(
        Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.update_versions, info.versionName, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.update_how_to))
        if (info.notes.isNotBlank()) {
            Text(
                info.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Pour vérifier le fichier téléchargé (sélectionnable, pour le copier).
        Column {
            Text(stringResource(R.string.update_sha256), style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(info.sha256, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
