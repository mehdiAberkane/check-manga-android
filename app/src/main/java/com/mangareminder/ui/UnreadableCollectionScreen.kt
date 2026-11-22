package com.mangareminder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.data.Series
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * series.json n'a pas pu être lu. Rien n'est écrit tant que l'utilisateur n'a pas choisi :
 * restaurer la dernière sauvegarde automatique, importer un fichier, exporter le fichier abîmé
 * (pour le garder ou le faire réparer), ou repartir d'une collection vide.
 */
@Composable
fun UnreadableCollectionScreen(
    hasAutoBackup: Boolean,
    pendingImport: List<Series>?,
    onRestoreLatest: suspend () -> Boolean,
    onReadBackup: suspend (Uri) -> Boolean,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onExportDamaged: suspend (Uri) -> Boolean,
    onStartEmpty: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            if (!onReadBackup(uri)) snackbar.showSnackbar(resources.getString(R.string.import_invalid))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(
                resources.getString(if (onExportDamaged(uri)) R.string.export_done else R.string.export_failed),
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.unreadable_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.unreadable_text), style = MaterialTheme.typography.bodyLarge)
            if (hasAutoBackup) {
                Button(
                    onClick = {
                        scope.launch {
                            if (!onRestoreLatest()) snackbar.showSnackbar(resources.getString(R.string.unreadable_no_backup))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.unreadable_restore)) }
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.menu_import))
            }
            OutlinedButton(
                onClick = { exportLauncher.launch("manga-reminder-abime-${LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.unreadable_export)) }
            TextButton(onClick = { confirmEmpty = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.unreadable_start_empty), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingImport?.let { imported ->
        ImportDialog(
            importedCount = imported.size,
            currentCount = 0,
            onDismiss = onCancelImport,
            onConfirm = onConfirmImport,
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.unreadable_start_empty)) },
            text = { Text(stringResource(R.string.unreadable_start_empty_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    onStartEmpty()
                }) { Text(stringResource(R.string.unreadable_start_empty_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
