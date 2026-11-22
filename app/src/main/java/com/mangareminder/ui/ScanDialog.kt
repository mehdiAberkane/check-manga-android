package com.mangareminder.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.mangareminder.R
import com.mangareminder.data.ScanMatch
import com.mangareminder.data.normalizeIsbn

/**
 * Lance le scanner de code-barres de Google (services Play : l'app n'a pas besoin de la
 * permission caméra). En cas d'échec (pas de services Play, module absent) ou si l'utilisateur
 * ferme le scanner, saisie manuelle de l'ISBN.
 */
fun startBarcodeScan(context: Context, onCode: (String) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onCode) ?: onUnavailable() }
        .addOnFailureListener { onUnavailable() }
        // Scanner fermé sans code (code abîmé, pas de lumière…) : on propose la saisie au clavier.
        .addOnCanceledListener { onUnavailable() }
}

@Composable
fun ScanDialog(
    state: ScanState,
    onIsbn: (String) -> Unit,
    onScanAgain: () -> Unit,
    onMarkOwned: (ScanMatch.InCollection) -> Unit,
    onCreateSeries: (ScanMatch.NewSeries) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_title)) },
        text = {
            when (state) {
                is ScanState.Looking -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.scan_looking, state.isbn))
                    CircularProgressIndicator()
                }
                is ScanState.Found -> when (val match = state.match) {
                    is ScanMatch.InCollection -> Text(
                        stringResource(
                            if (match.owned) R.string.answer_owned else R.string.answer_missing,
                            match.series.title,
                            match.volume,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    is ScanMatch.NewSeries -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.scan_book, match.bookTitle), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.scan_not_in_collection))
                    }
                }
                is ScanState.Manual -> ManualIsbn(state, onIsbn)
            }
        },
        confirmButton = {
            when (state) {
                is ScanState.Found -> when (val match = state.match) {
                    is ScanMatch.InCollection -> if (match.owned) {
                        TextButton(onClick = onScanAgain) { Text(stringResource(R.string.scan_again)) }
                    } else {
                        TextButton(onClick = { onMarkOwned(match) }) { Text(stringResource(R.string.action_bought)) }
                    }
                    is ScanMatch.NewSeries -> TextButton(onClick = { onCreateSeries(match) }) {
                        Text(stringResource(R.string.scan_create_series, match.suggestedTitle))
                    }
                }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Saisie de l'ISBN au clavier : secours si le scanner n'est pas disponible ou si la recherche a échoué. */
@Composable
private fun ManualIsbn(state: ScanState.Manual, onIsbn: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf(state.isbn.orEmpty()) }
    val valid = normalizeIsbn(input) != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(20) },
            label = { Text(stringResource(R.string.scan_isbn_label)) },
            supportingText = {
                if (input.isNotBlank() && !valid) Text(stringResource(R.string.scan_isbn_invalid))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { onIsbn(input) }, enabled = valid, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.scan_search))
        }
    }
}
