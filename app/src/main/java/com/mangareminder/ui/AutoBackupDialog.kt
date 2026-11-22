package com.mangareminder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.backup.BackupStatus
import com.mangareminder.backup.KEPT_BACKUPS
import java.text.DateFormat
import java.util.Date

/** « 22/11/2026 21:05 », au format du téléphone. */
fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
fun AutoBackupDialog(
    status: BackupStatus,
    onFolderChosen: (Uri) -> Unit,
    onBackupNow: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Choix d'un dossier par Android (stockage du téléphone, Google Drive…).
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderChosen(uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.backup_intro, KEPT_BACKUPS, KEPT_BACKUPS))
                if (status.folder != null) {
                    Text(stringResource(R.string.backup_folder, status.folderName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (status.lastSuccess == 0L) stringResource(R.string.backup_never)
                        else stringResource(R.string.backup_last, formatDateTime(status.lastSuccess)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    status.lastError?.let {
                        Text(it.text(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onBackupNow) { Text(stringResource(R.string.backup_now)) }
                    TextButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.backup_change_folder)) }
                    TextButton(onClick = onDisable) {
                        Text(stringResource(R.string.backup_disable), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (status.folder == null) {
                TextButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.backup_choose_folder)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (status.folder == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
