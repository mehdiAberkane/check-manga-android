package com.mangareminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import androidx.compose.ui.res.stringResource

/** Choix de la couverture parmi celles trouvées par Google Books, une par tome. */
@Composable
fun CoverChooserDialog(
    search: CoverSearch,
    hasChosenCover: Boolean,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cover_dialog_title)) },
        text = {
            when (search) {
                CoverSearch.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.cover_searching))
                    CircularProgressIndicator(Modifier.padding(top = 16.dp))
                }
                CoverSearch.Failed -> Text(stringResource(R.string.cover_search_failed))
                is CoverSearch.Results -> if (search.choices.isEmpty()) {
                    Text(stringResource(R.string.cover_none_found))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (search.partial) {
                            Text(
                                stringResource(R.string.cover_search_partial),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 72.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            items(search.choices, key = { it.cover!! }) { release ->
                                val label = stringResource(R.string.volume_n, release.volume)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = label) {
                                        onChoose(release.cover)
                                    },
                                ) {
                                    CoverImage(
                                        release.cover!!,
                                        contentDescription = label,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                    )
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasChosenCover) {
                TextButton(onClick = { onChoose(null) }) { Text(stringResource(R.string.cover_auto)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
