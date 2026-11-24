@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import com.mangareminder.releases.ReleaseNotifier
import com.mangareminder.releases.SeriesReleases
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.res.stringResource
import com.mangareminder.R
import com.mangareminder.releases.GoogleBooksSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import com.mangareminder.releases.autoCover
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.remember
import com.mangareminder.releases.CheckError
import com.mangareminder.releases.CheckFailure
import androidx.compose.ui.res.pluralStringResource

@Composable
fun SeriesDetailScreen(
    series: Series,
    onBack: () -> Unit,
    onToggleVolume: (Int) -> Unit,
    onAddVolumes: (Set<Int>) -> Unit,
    onEdit: (title: String, totalVolumes: Int?) -> Unit,
    onDelete: () -> Unit,
    releases: SeriesReleases?,
    checking: Boolean,
    checkError: CheckError?,
    onToggleWatch: () -> Unit,
    onCheckNow: () -> Unit,
    coverSearch: CoverSearch?,
    onSearchCovers: () -> Unit,
    onChooseCover: (String?) -> Unit,
    onDismissCoverSearch: () -> Unit,
    onSetRead: (volume: Int, read: Boolean) -> Unit,
    onLend: (volume: Int, to: String) -> Unit,
    onReturnLoan: (volume: Int) -> Unit,
) {
    // Tome dont on modifie le statut (appui long sur une case possédée).
    var statusVolume by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    // Android 13+ : la permission de notifier est demandée quand on active la surveillance.
    // Un refus n'empêche pas la surveillance : les sorties restent visibles dans l'app.
    // Vérifiée à chaque retour sur l'écran (réglages modifiés) et après la demande de permission.
    var canNotify by remember { mutableStateOf(ReleaseNotifier.canNotify(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = ReleaseNotifier.canNotify(context)
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { canNotify = ReleaseNotifier.canNotify(context) }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!series.watched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !ReleaseNotifier.canNotify(context)
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onToggleWatch()
                    }) {
                        Icon(
                            if (series.watched) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = stringResource(
                                if (series.watched) R.string.watch_stop else R.string.watch_start,
                            ),
                        )
                    }
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddVolumes = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_volumes_title)) },
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
                SummaryCard(series, cover = series.cover ?: releases.autoCover(), onChooseCover = onSearchCovers)
            }
            if (series.watched) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ReleasesCard(
                        series, releases, checking, checkError, onCheckNow,
                        notificationsBlocked = !canNotify,
                        onOpenNotificationSettings = { context.startActivity(ReleaseNotifier.settingsIntent(context)) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items((1..gridSize).toList(), key = { it }) { volume ->
                val owned = volume in series.ownedVolumes
                val description = listOfNotNull(
                    stringResource(R.string.volume_n, volume),
                    stringResource(R.string.volume_read).takeIf { volume in series.readVolumes },
                    series.loans[volume]?.let { stringResource(R.string.volume_lent_to, it.to) },
                ).joinToString(", ")
                VolumeTile(
                    label = "$volume",
                    owned = owned,
                    read = volume in series.readVolumes,
                    lent = volume in series.loans,
                    onClick = { onToggleVolume(volume) },
                    onLongClick = if (owned) ({ statusVolume = volume }) else null,
                    modifier = Modifier.semantics {
                        contentDescription = description
                        role = Role.Checkbox
                        toggleableState = ToggleableState(owned)
                    },
                )
            }
            if (showNextTile) {
                item(key = "next") {
                    val description = stringResource(R.string.have_next_volume, gridSize + 1)
                    VolumeTile(
                        label = "+",
                        owned = false,
                        onClick = { onToggleVolume(gridSize + 1) },
                        modifier = Modifier.semantics {
                            contentDescription = description
                            role = Role.Button
                        },
                    )
                }
            }
        }
    }

    if (showEdit) {
        SeriesDialog(
            heading = stringResource(R.string.edit_series),
            initialTitle = series.title,
            initialTotal = series.totalVolumes,
            onDismiss = { showEdit = false },
            onConfirm = { title, total ->
                showEdit = false
                onEdit(title, total)
            },
        )
    }
    statusVolume?.takeIf { it in series.ownedVolumes }?.let { volume ->
        VolumeStatusDialog(
            volume = volume,
            read = volume in series.readVolumes,
            loan = series.loans[volume],
            onSetRead = { onSetRead(volume, it) },
            onLend = { onLend(volume, it) },
            onReturn = { onReturnLoan(volume) },
            onDismiss = { statusVolume = null },
        )
    }

    coverSearch?.let {
        CoverChooserDialog(
            search = it,
            hasChosenCover = series.cover != null,
            onChoose = onChooseCover,
            onDismiss = onDismissCoverSearch,
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
private fun SummaryCard(series: Series, cover: String?, onChooseCover: () -> Unit) {
    val missing = series.missingVolumes
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp)) {
            cover?.let {
                CoverImage(
                    it,
                    contentDescription = stringResource(R.string.cover_of, series.title),
                    modifier = Modifier.size(width = 72.dp, height = 108.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ownedSummary(series), style = MaterialTheme.typography.titleMedium)
                when {
                    series.isComplete -> Text(stringResource(R.string.series_complete))
                    missing.isNotEmpty() -> Text(stringResource(R.string.missing_volumes, formatVolumes(missing)))
                    series.totalVolumes == null && series.ownedCount > 0 ->
                        Text(stringResource(R.string.next_to_buy, series.lastOwned + 1))
                }
                if (series.readVolumes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.read_volumes, formatVolumes(series.readVolumes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (series.loans.isNotEmpty()) {
                    Text(loansSummary(series), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onChooseCover, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.cover_choose))
                }
            }
        }
    }
}

@Composable
private fun VolumeTile(
    label: String,
    owned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    read: Boolean = false,
    lent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (owned) colors.primary else colors.surface,
        contentColor = if (owned) colors.onPrimary else colors.onSurfaceVariant,
        border = if (owned) null else BorderStroke(1.dp, colors.outline),
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.volume_status_action) },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Le texte visible est remplacé par la description de la case pour TalkBack.
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {},
            )
            // Petits repères : lu (coche) et prêté (personne), décrits dans la description de la case.
            if (read) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(14.dp),
                )
            }
            if (lent) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ReleasesCard(
    series: Series,
    releases: SeriesReleases?,
    checking: Boolean,
    checkError: CheckError?,
    onCheckNow: () -> Unit,
    notificationsBlocked: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.watched_releases), style = MaterialTheme.typography.titleMedium)
            if (notificationsBlocked) {
                Text(
                    stringResource(R.string.notifications_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenNotificationSettings, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.notifications_settings))
                }
            }
            if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                releases == null -> Text(
                    stringResource(if (checking) R.string.checking else R.string.not_checked),
                    style = MaterialTheme.typography.bodyMedium,
                )
                releases.releases.isEmpty() -> Text(
                    stringResource(R.string.no_release_found),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> releases.releases.take(MAX_SHOWN_RELEASES).forEach { release ->
                    val isNew = release.volume > series.lastKnownVolume
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = release.link?.let { link ->
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_book)) {
                                    uriHandler.openUri(link)
                                }
                                .padding(vertical = 2.dp)
                        } ?: Modifier.fillMaxWidth(),
                    ) {
                        release.cover?.let {
                            CoverImage(it, contentDescription = null, modifier = Modifier.size(width = 32.dp, height = 48.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            release.describe().text().let { if (isNew) stringResource(R.string.new_release, it) else it },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
                            color = if (release.link != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            releases?.let {
                Text(
                    stringResource(
                        R.string.checked_at,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.checkedAt)),
                        GoogleBooksSource.name,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            checkError?.let {
                Text(checkErrorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onCheckNow, enabled = !checking) { Text(stringResource(R.string.action_check_now)) }
        }
    }
}

private const val MAX_SHOWN_RELEASES = 5

/** « 1 série non vérifiée le 24/11/2026 21:30 : Google Books refuse les requêtes (quota épuisé)… ». */
@Composable
private fun checkErrorText(error: CheckError): String = pluralStringResource(
    when (error.failure) {
        CheckFailure.QUOTA -> R.plurals.check_failed_quota
        CheckFailure.NETWORK -> R.plurals.check_failed_network
        CheckFailure.INVALID -> R.plurals.check_failed_invalid
    },
    error.failedSeries,
    error.failedSeries,
    formatDateTime(error.at),
)
