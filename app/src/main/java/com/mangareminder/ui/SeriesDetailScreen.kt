@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangareminder.data.MAX_VOLUME
import com.mangareminder.data.Series
import com.mangareminder.data.formatVolumes

@Composable
fun SeriesDetailScreen(
    series: Series,
    onBack: () -> Unit,
    onToggleVolume: (Int) -> Unit,
    onAddVolumes: (Set<Int>) -> Unit,
    onEdit: (title: String, totalVolumes: Int?) -> Unit,
    onDelete: () -> Unit,
) {
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showAddVolumes by rememberSaveable { mutableStateOf(false) }

    // Série en cours (total inconnu) : on affiche jusqu'au dernier tome possédé,
    // suivi d'une case « + » pour marquer le tome suivant.
    val gridSize = maxOf(series.totalVolumes ?: 0, series.lastOwned)
    val showNextTile = series.totalVolumes == null && gridSize < MAX_VOLUME

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddVolumes = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ajouter des tomes") },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 60.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SummaryCard(series)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Touche un tome pour le cocher ou le décocher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items((1..gridSize).toList(), key = { it }) { volume ->
                val owned = volume in series.ownedVolumes
                VolumeTile(
                    label = "$volume",
                    owned = owned,
                    onClick = { onToggleVolume(volume) },
                    modifier = Modifier.semantics {
                        contentDescription = "Tome $volume"
                        role = Role.Checkbox
                        toggleableState = ToggleableState(owned)
                    },
                )
            }
            if (showNextTile) {
                item(key = "next") {
                    VolumeTile(
                        label = "+",
                        owned = false,
                        onClick = { onToggleVolume(gridSize + 1) },
                        modifier = Modifier.semantics {
                            contentDescription = "J'ai le tome ${gridSize + 1}"
                            role = Role.Button
                        },
                    )
                }
            }
        }
    }

    if (showEdit) {
        SeriesDialog(
            heading = "Modifier la série",
            initialTitle = series.title,
            initialTotal = series.totalVolumes,
            onDismiss = { showEdit = false },
            onConfirm = { title, total ->
                showEdit = false
                onEdit(title, total)
            },
        )
    }
    if (showDelete) {
        DeleteSeriesDialog(
            title = series.title,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                onDelete()
            },
        )
    }
    if (showAddVolumes) {
        AddVolumesDialog(
            onDismiss = { showAddVolumes = false },
            onConfirm = {
                showAddVolumes = false
                onAddVolumes(it)
            },
        )
    }
}

@Composable
private fun SummaryCard(series: Series) {
    val missing = series.missingVolumes
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ownedSummary(series), style = MaterialTheme.typography.titleMedium)
            when {
                series.isComplete -> Text("Série complète 🎉")
                missing.isNotEmpty() -> Text("Manquants : ${formatVolumes(missing)}")
                series.totalVolumes == null && series.ownedCount > 0 ->
                    Text("Prochain à acheter : tome ${series.lastOwned + 1}")
            }
        }
    }
}

@Composable
private fun VolumeTile(label: String, owned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (owned) colors.primary else colors.surface,
        contentColor = if (owned) colors.onPrimary else colors.onSurfaceVariant,
        border = if (owned) null else BorderStroke(1.dp, colors.outline),
        modifier = modifier.aspectRatio(0.75f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Le texte visible est remplacé par la description de la case pour TalkBack.
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
