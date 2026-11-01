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
                    label = { Text("Titre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.filter(Char::isDigit).take(3) },
                    label = { Text("Nombre de tomes parus") },
                    supportingText = { Text("Optionnel : laisse vide si tu ne sais pas") },
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
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun AddVolumesDialog(onDismiss: () -> Unit, onConfirm: (Set<Int>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = parseVolumes(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter des tomes") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Tomes possédés") },
                placeholder = { Text("ex : 1-12, 15, 17") },
                supportingText = {
                    when {
                        input.isBlank() -> Text("Un numéro, une plage (1-12) ou une liste")
                        parsed == null -> Text("Format non reconnu")
                        else -> Text("${parsed.size} tome(s) : ${formatVolumes(parsed)}", maxLines = 2)
                    }
                },
                isError = input.isNotBlank() && parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ImportDialog(importedCount: Int, currentCount: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer la sauvegarde ?") },
        text = {
            Text(
                if (currentCount == 0) "$importedCount série(s) vont être ajoutées."
                else "Ta collection actuelle ($currentCount série(s)) sera remplacée par les $importedCount série(s) de la sauvegarde."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Importer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun SaveErrorDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enregistrement impossible") },
        text = {
            Text(
                "Tes dernières modifications n'ont pas pu être enregistrées sur le téléphone " +
                    "(stockage plein ?). Libère de la place, puis exporte ta collection pour ne rien perdre."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun DeleteSeriesDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer la série ?") },
        text = { Text("« $title » et tous ses tomes seront retirés de ta collection.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
