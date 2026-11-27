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
import androidx.compose.material.icons.outlined.Notifications
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
import com.mangareminder.releases.Release
import com.mangareminder.releases.SeriesReleases
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.ui.res.stringResource
import com.mangareminder.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.mangareminder.releases.autoCover
import com.mangareminder.backup.BackupStatus
import com.mangareminder.data.searchResults
import com.mangareminder.data.volumeAnswers
import com.mangareminder.data.VolumeAnswer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import com.mangareminder.data.ListFilter
import com.mangareminder.data.ListSort
import com.mangareminder.data.applyListOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.mangareminder.data.UndoAction

@Composable
fun SeriesListScreen(
    series: List<Series>,
    onOpen: (Long) -> Unit,
    onAdd: (title: String, totalVolumes: Int?) -> Unit,
    onExport: suspend (Uri) -> Boolean,
    pendingImport: List<Series>?,
    onReadBackup: suspend (Uri) -> Boolean,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    releases: Map<Long, SeriesReleases>,
    onCheckReleases: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenShopping: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenUpcoming: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenApiKey: () -> Unit,
    onScan: () -> Unit,
    backupStatus: BackupStatus,
    onEnableBackup: (Uri) -> Boolean,
    onDisableBackup: () -> Unit,
    onBackupNow: () -> Unit,
    onMarkOwned: (seriesId: Long, volume: Int) -> Unit,
    listFilter: ListFilter,
    listSort: ListSort,
    onListFilter: (ListFilter) -> Unit,
    onListSort: (ListSort) -> Unit,
    undo: UndoAction?,
    onUndo: () -> Unit,
    onForgetUndo: (UndoAction) -> Unit,
) {
    var showBackup by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Textes des snackbars, lus hors composition (dans les callbacks).
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        val message = when (action) {
            is UndoAction.Deleted -> resources.getString(R.string.series_deleted, action.series.title)
            is UndoAction.Imported -> resources.getQuantityString(R.plurals.series_imported, action.count, action.count)
        }
        val result = snackbar.showSnackbar(
            message,
            actionLabel = resources.getString(R.string.action_cancel),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo() else onForgetUndo(action)
    }
    val filtered = remember(series, query, listFilter, listSort, releases) {
        applyListOptions(searchResults(series, query), listFilter, listSort) { s ->
            releases[s.id]?.newRelease(s.lastKnownVolume) != null
        }
    }
    // « one piece 45 » : réponse directe en haut de la liste (usage en magasin).
    val answers = remember(series, query) { volumeAnswers(series, query) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            snackbar.showSnackbar(resources.getString(if (onExport(uri)) R.string.export_done else R.string.export_failed))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            if (!onReadBackup(uri)) snackbar.showSnackbar(resources.getString(R.string.import_invalid))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_scan)) },
                            onClick = {
                                menuOpen = false
                                onScan()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_shopping)) },
                            enabled = series.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                onOpenShopping()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.upcoming_title)) },
                            enabled = series.any { it.watched },
                            onClick = {
                                menuOpen = false
                                onOpenUpcoming()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stats_title)) },
                            enabled = series.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                onOpenStats()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_check_releases)) },
                            enabled = series.any { it.watched },
                            onClick = {
                                menuOpen = false
                                onCheckReleases()
                                scope.launch { snackbar.showSnackbar(resources.getString(R.string.checking_releases)) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_export)) },
                            enabled = series.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("manga-reminder-${LocalDate.now()}.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_import)) },
                            onClick = {
                                menuOpen = false
                                // Certains gestionnaires de fichiers ne typent pas les .json : on accepte tout et on valide à la lecture.
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.menu_auto_backup))
                                    Text(
                                        if (backupStatus.folder == null) stringResource(R.string.backup_menu_off)
                                        else if (backupStatus.lastSuccess == 0L) stringResource(R.string.backup_never)
                                        else stringResource(R.string.backup_menu_last, formatDateTime(backupStatus.lastSuccess)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (backupStatus.lastError != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                menuOpen = false
                                showBackup = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.api_key_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenApiKey()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.appearance_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenAppearance()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_check_update)) },
                            onClick = {
                                menuOpen = false
                                onCheckUpdate()
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
                text = { Text(stringResource(R.string.new_series)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (series.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            if (series.isNotEmpty()) {
                ListOptionsRow(
                    filter = listFilter,
                    sort = listSort,
                    showLent = listFilter == ListFilter.LENT || series.any { it.loans.isNotEmpty() },
                    onFilter = onListFilter,
                    onSort = onListSort,
                )
            }
            when {
                series.isEmpty() -> EmptyState(stringResource(R.string.empty_collection))
                filtered.isEmpty() && answers.isEmpty() -> EmptyState(
                    if (query.isBlank()) stringResource(R.string.no_match_filter)
                    else stringResource(R.string.no_match, query.trim()),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(answers, key = { "answer-${it.series.id}" }) { answer ->
                        VolumeAnswerCard(answer, onMarkOwned = { onMarkOwned(answer.series.id, answer.volume) })
                    }
                    items(filtered, key = { it.id }) { s ->
                        val newRelease = if (s.watched) releases[s.id]?.newRelease(s.lastKnownVolume) else null
                        SeriesCard(s, newRelease, cover = s.cover ?: releases[s.id].autoCover(), onClick = { onOpen(s.id) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        SeriesDialog(
            heading = stringResource(R.string.new_series),
            initialTitle = "",
            initialTotal = null,
            onDismiss = { showAdd = false },
            onConfirm = { title, total ->
                showAdd = false
                onAdd(title, total)
            },
        )
    }
    if (showBackup) {
        AutoBackupDialog(
            status = backupStatus,
            onFolderChosen = { folder ->
                if (!onEnableBackup(folder)) {
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.backup_folder_refused)) }
                }
            },
            onBackupNow = {
                onBackupNow()
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.backup_started)) }
            },
            onDisable = onDisableBackup,
            onDismiss = { showBackup = false },
        )
    }

    pendingImport?.let { imported ->
        ImportDialog(
            importedCount = imported.size,
            currentCount = series.size,
            onDismiss = onCancelImport,
            onConfirm = onConfirmImport,
        )
    }
}

/** « One Piece, tome 45 : tu l'as » ou « tu ne l'as pas » avec un bouton pour le cocher. */
@Composable
private fun VolumeAnswerCard(answer: VolumeAnswer, onMarkOwned: () -> Unit) {
    val owned = answer.owned
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (owned) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (owned) R.string.answer_owned else R.string.answer_missing,
                        answer.series.title,
                        answer.volume,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (owned) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                )
                val total = answer.series.totalVolumes
                if (!owned && answer.beyondTotal && total != null) {
                    Text(
                        pluralStringResource(R.plurals.answer_beyond_total, total, total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (!owned) {
                Button(onClick = onMarkOwned, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.action_bought))
                }
            }
        }
    }
}

@Composable
private fun SeriesCard(series: Series, newRelease: Release?, cover: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            cover?.let {
                CoverImage(it, contentDescription = null, modifier = Modifier.size(width = 48.dp, height = 72.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        series.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (series.watched) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.watched_releases),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (series.isComplete) {
                        Text(
                            stringResource(R.string.complete_badge),
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
                newRelease?.let {
                    Text(
                        stringResource(R.string.new_release, it.describe().text()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (series.loans.isNotEmpty()) {
                    Text(
                        loansSummary(series),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                val missing = series.missingVolumes
                if (missing.isNotEmpty()) {
                    Text(
                        stringResource(R.string.missing_volumes, formatVolumes(missing)),
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
}

/** « 3 / 30 tomes », « 4 tomes · dernier : 6 » ou « Aucun tome » ; l'accord suit le nombre affiché. */
@Composable
fun ownedSummary(series: Series): String {
    val count = series.ownedCount
    val total = series.totalVolumes
    return when {
        total != null -> pluralStringResource(R.plurals.owned_of_total, total, count, total)
        count == 0 -> stringResource(R.string.no_volume)
        else -> pluralStringResource(R.plurals.owned_last, count, count, series.lastOwned)
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
