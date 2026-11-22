package com.mangareminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.data.Loan
import com.mangareminder.data.MAX_BORROWER_LENGTH
import com.mangareminder.data.Series
import com.mangareminder.releases.formatReleaseDate

/** « Prêtés : tome 3 (Paul), tome 7 (Marie) ». */
@Composable
fun loansSummary(series: Series): String {
    val resources = LocalResources.current
    return stringResource(
        R.string.lent_volumes,
        series.loans.toSortedMap().entries.joinToString(", ") { (volume, loan) ->
            resources.getString(R.string.lent_volume_to, volume, loan.to)
        },
    )
}

/** Statut d'un tome possédé : lu ou non, prêté à qui. */
@Composable
fun VolumeStatusDialog(
    volume: Int,
    read: Boolean,
    loan: Loan?,
    onSetRead: (Boolean) -> Unit,
    onLend: (String) -> Unit,
    onReturn: () -> Unit,
    onDismiss: () -> Unit,
) {
    var borrower by rememberSaveable(volume) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.volume_n, volume)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = read, role = Role.Checkbox, onValueChange = onSetRead),
                ) {
                    Checkbox(checked = read, onCheckedChange = null)
                    Text(stringResource(R.string.volume_read_label))
                }
                if (loan != null) {
                    Text(
                        loan.date?.let { stringResource(R.string.lent_to_since, loan.to, formatReleaseDate(it).orEmpty()) }
                            ?: stringResource(R.string.volume_lent_to, loan.to),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = onReturn) { Text(stringResource(R.string.action_returned)) }
                } else {
                    OutlinedTextField(
                        value = borrower,
                        onValueChange = { borrower = it.take(MAX_BORROWER_LENGTH) },
                        label = { Text(stringResource(R.string.lend_to_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            onLend(borrower)
                            borrower = ""
                        },
                        enabled = borrower.isNotBlank(),
                    ) { Text(stringResource(R.string.action_lend)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
