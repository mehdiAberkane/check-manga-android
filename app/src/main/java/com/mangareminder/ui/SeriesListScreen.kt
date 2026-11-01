@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangareminder.data.Series
import com.mangareminder.data.formatVolumes
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SeriesListScreen(
    series: List<Series>,
    onOpen: (Long) -> Unit,
    onAdd: (title: String, totalVolumes: Int?) -> Unit,
    onExport: suspend (Uri) -> Boolean,
    onReadBackup: suspend (Uri) -> List<Series>?,
    onReplaceAll: (List<Series>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<List<Series>?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val filtered = remember(series, query) {
        series.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(if (onExport(uri)) "Collection exportée" else "Échec de l'export")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = onReadBackup(uri)
            if (imported == null) snackbar.showSnackbar("Ce fichier n'est pas une sauvegarde Manga Reminder")
            else pendingImport = imported
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manga Reminder") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Plus d'options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Exporter la collection") },
                            enabled = series.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("manga-reminder-${LocalDate.now()}.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer une sauvegarde") },
                            onClick = {
                                menuOpen = false
                                // Certains gestionnaires de fichiers ne typent pas les .json : on accepte tout et on valide à la lecture.
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nouvelle série") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (series.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Rechercher une série") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            when {
                series.isEmpty() -> EmptyState("Aucune série pour l'instant.\nAjoute ta première série avec le bouton en bas.")
                filtered.isEmpty() -> EmptyState("Aucune série ne correspond à « ${query.trim()} ».")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { s ->
                        SeriesCard(s, onClick = { onOpen(s.id) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        SeriesDialog(
            heading = "Nouvelle série",
            initialTitle = "",
            initialTotal = null,
            onDismiss = { showAdd = false },
            onConfirm = { title, total ->
                showAdd = false
                onAdd(title, total)
            },
        )
    }
    pendingImport?.let { imported ->
        ImportDialog(
            importedCount = imported.size,
            currentCount = series.size,
            onDismiss = { pendingImport = null },
            onConfirm = {
                pendingImport = null
                onReplaceAll(imported)
                scope.launch { snackbar.showSnackbar("${imported.size} série(s) importée(s)") }
            },
        )
    }
}

@Composable
private fun SeriesCard(series: Series, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    series.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (series.isComplete) {
                    Text(
                        "Complète",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(
                ownedSummary(series),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            series.totalVolumes?.let { total ->
                LinearProgressIndicator(
                    progress = { (series.ownedCount.toFloat() / total).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            val missing = series.missingVolumes
            if (missing.isNotEmpty()) {
                Text(
                    "Manquants : ${formatVolumes(missing)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

fun ownedSummary(series: Series): String {
    val count = series.ownedCount
    val tomes = if (count > 1) "tomes" else "tome"
    return when {
        series.totalVolumes != null -> "$count / ${series.totalVolumes} $tomes"
        count == 0 -> "Aucun tome"
        else -> "$count $tomes · dernier : ${series.lastOwned}"
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
