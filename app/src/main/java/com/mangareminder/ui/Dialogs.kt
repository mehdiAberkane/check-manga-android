package com.mangareminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mangareminder.data.MAX_VOLUME
import com.mangareminder.data.SeriesRepository
import com.mangareminder.data.formatVolumes
import com.mangareminder.data.parseVolumes
import androidx.compose.ui.res.stringResource
import com.mangareminder.R
import androidx.compose.ui.res.pluralStringResource

@Composable
fun SeriesDialog(
    heading: String,
    initialTitle: String,
    initialTotal: Int?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, totalVolumes: Int?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var total by rememberSaveable { mutableStateOf(initialTotal?.toString() ?: "") }
    val totalValue = total.toIntOrNull()
    val totalValid = total.isEmpty() || (totalValue != null && totalValue in 1..MAX_VOLUME)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(SeriesRepository.MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.field_total)) },
                    supportingText = { Text(stringResource(R.string.field_total_hint)) },
                    isError = !totalValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim(), totalValue) },
                enabled = title.isNotBlank() && totalValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun AddVolumesDialog(onDismiss: () -> Unit, onConfirm: (Set<Int>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = parseVolumes(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_volumes_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.field_owned)) },
                placeholder = { Text("ex : 1-12, 15, 17") },
                supportingText = {
                    when {
                        input.isBlank() -> Text(stringResource(R.string.volumes_hint))
                        parsed == null -> Text(stringResource(R.string.volumes_invalid))
                        else -> Text(
                            pluralStringResource(R.plurals.volumes_preview, parsed.size, parsed.size, formatVolumes(parsed)),
                            maxLines = 2,
                        )
                    }
                },
                isError = input.isNotBlank() && parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ImportDialog(importedCount: Int, currentCount: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Text(
                if (currentCount == 0) pluralStringResource(R.plurals.import_add, importedCount, importedCount)
                else stringResource(
                    R.string.import_replace,
                    pluralStringResource(R.plurals.series_count, currentCount, currentCount),
                    pluralStringResource(R.plurals.series_count, importedCount, importedCount),
                )
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SaveErrorDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_error_title)) },
        text = {
            Text(stringResource(R.string.save_error_text))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
fun DeleteSeriesDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_text, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
