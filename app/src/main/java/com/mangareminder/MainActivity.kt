package com.mangareminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangareminder.ui.SaveErrorDialog
import com.mangareminder.ui.SeriesDetailScreen
import com.mangareminder.ui.SeriesListScreen
import com.mangareminder.ui.SeriesViewModel
import com.mangareminder.ui.UpdateDialog
import com.mangareminder.ui.UpdateViewModel
import com.mangareminder.ui.theme.MangaReminderTheme
import com.mangareminder.update.UpdateState
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.mangareminder.ui.ShoppingListScreen
import com.mangareminder.ui.UpcomingReleasesScreen
import com.mangareminder.ui.StatsScreen
import com.mangareminder.ui.ScanDialog
import com.mangareminder.ui.startBarcodeScan
import com.mangareminder.ui.UnreadableCollectionScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import android.widget.Toast
import androidx.compose.ui.unit.dp
import com.mangareminder.ui.AppBackground
import com.mangareminder.ui.AppearanceScreen
import com.mangareminder.ui.AppearanceViewModel

class MainActivity : ComponentActivity() {
    /** Demande d'ouverture de la liste « À acheter » (widget), consommée par l'écran. */
    private val openShopping = mutableStateOf(false)

    /** Série à ouvrir (notification de sortie), consommée par l'écran ; id inconnu : rien. */
    private val openSeriesId = mutableStateOf<Long?>(null)

    /** Recherche de mise à jour demandée (notification de nouvelle version), consommée par l'écran. */
    private val checkUpdate = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            openShopping.value = intent.getBooleanExtra(EXTRA_SHOPPING, false)
            openSeriesId.value = seriesIdOf(intent)
            checkUpdate.value = intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)
        }
        setContent {
            val appearanceViewModel: AppearanceViewModel = viewModel()
            val appearance by appearanceViewModel.settings.collectAsStateWithLifecycle()
            MangaReminderTheme(appearance) {
                AppBackground(appearance, appearanceViewModel::loadBackground) {
                    MangaReminderApp(
                        openShopping = openShopping.value,
                        onShoppingOpened = { openShopping.value = false },
                        openSeriesId = openSeriesId.value,
                        onSeriesOpened = { openSeriesId.value = null },
                        checkUpdate = checkUpdate.value,
                        onUpdateChecked = { checkUpdate.value = false },
                        appearanceViewModel = appearanceViewModel,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOPPING, false)) openShopping.value = true
        seriesIdOf(intent)?.let { openSeriesId.value = it }
        if (intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)) checkUpdate.value = true
    }

    /** L'activité est exportée : l'extra peut venir de n'importe où, il est seulement lu comme un id. */
    private fun seriesIdOf(intent: Intent): Long? =
        intent.getLongExtra(EXTRA_SERIES_ID, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }

    companion object {
        const val EXTRA_SHOPPING = "com.mangareminder.SHOPPING"
        const val EXTRA_SERIES_ID = "com.mangareminder.SERIES_ID"
        const val EXTRA_CHECK_UPDATE = "com.mangareminder.CHECK_UPDATE"
    }
}

@Composable
private fun MangaReminderApp(
    openShopping: Boolean,
    onShoppingOpened: () -> Unit,
    openSeriesId: Long?,
    onSeriesOpened: () -> Unit,
    checkUpdate: Boolean,
    onUpdateChecked: () -> Unit,
    appearanceViewModel: AppearanceViewModel,
    viewModel: SeriesViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val releases by viewModel.releases.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val checkError by viewModel.checkError.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val autoCheck by updateViewModel.autoCheck.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val coverSearch by viewModel.coverSearch.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val listFilter by viewModel.listFilter.collectAsStateWithLifecycle()
    val listSort by viewModel.listSort.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val unreadable by viewModel.collectionUnreadable.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val startScan = { startBarcodeScan(context, onCode = viewModel::lookupIsbn, onUnavailable = viewModel::scanManually) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showShopping by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showUpcoming by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    val appearance by appearanceViewModel.settings.collectAsStateWithLifecycle()
    val selected = series.find { it.id == selectedId }

    // Attend la lecture de la collection : avant, la série demandée ne serait pas trouvée.
    LaunchedEffect(openSeriesId, loaded) {
        if (openSeriesId != null && loaded) {
            // Id inconnu (série supprimée, intention forgée) : on reste sur la liste.
            if (series.any { it.id == openSeriesId }) {
                showShopping = false
                showUpcoming = false
                showStats = false
                selectedId = openSeriesId
            }
            onSeriesOpened()
        }
    }

    // Nouvelle lecture de la release : la notification peut dater de plusieurs jours.
    LaunchedEffect(checkUpdate) {
        if (checkUpdate) {
            updateViewModel.check()
            onUpdateChecked()
        }
    }

    LaunchedEffect(openShopping) {
        if (openShopping) {
            selectedId = null
            showUpcoming = false
            showStats = false
            showShopping = true
            onShoppingOpened()
        }
    }

    // Lecture de la collection en cours (hors du fil principal) : quelques millisecondes en général.
    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // series.json illisible : rien d'autre n'est affiché tant que l'utilisateur n'a pas choisi.
    if (unreadable) {
        UnreadableCollectionScreen(
            hasAutoBackup = backupStatus.folder != null,
            pendingImport = pendingImport,
            onRestoreLatest = viewModel::loadLatestBackup,
            onReadBackup = viewModel::readBackup,
            onConfirmImport = viewModel::confirmImport,
            onCancelImport = viewModel::cancelImport,
            onExportDamaged = viewModel::exportDamaged,
            onStartEmpty = viewModel::startEmpty,
        )
        return
    }

    if (selected == null && showAppearance) {
        BackHandler { showAppearance = false }
        AppearanceScreen(
            settings = appearance,
            onBack = { showAppearance = false },
            onThemeMode = appearanceViewModel::setThemeMode,
            onPureBlack = appearanceViewModel::setPureBlack,
            onDynamicColor = appearanceViewModel::setDynamicColor,
            onAccent = appearanceViewModel::setAccent,
            onGridSize = appearanceViewModel::setGridSize,
            onPickBackground = appearanceViewModel::setBackground,
            onRemoveBackground = appearanceViewModel::removeBackground,
            onBackgroundDim = appearanceViewModel::setBackgroundDim,
            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        )
    } else if (selected == null && showShopping) {
        BackHandler { showShopping = false }
        ShoppingListScreen(
            series = series,
            releases = releases,
            onBack = { showShopping = false },
            onOpen = { selectedId = it },
        )
    } else if (selected == null && showUpcoming) {
        BackHandler { showUpcoming = false }
        UpcomingReleasesScreen(
            series = series,
            releases = releases,
            onBack = { showUpcoming = false },
            onOpen = { selectedId = it },
        )
    } else if (selected == null && showStats) {
        BackHandler { showStats = false }
        StatsScreen(
            series = series,
            onBack = { showStats = false },
            onOpen = { selectedId = it },
        )
    } else if (selected == null) {
        SeriesListScreen(
            series = series,
            onOpen = { selectedId = it },
            onAdd = { title, total -> selectedId = viewModel.addSeries(title, total) },
            onExport = viewModel::exportTo,
            pendingImport = pendingImport,
            onReadBackup = viewModel::readBackup,
            onConfirmImport = viewModel::confirmImport,
            onCancelImport = viewModel::cancelImport,
            releases = releases,
            onCheckReleases = viewModel::checkReleasesNow,
            onCheckUpdate = updateViewModel::check,
            onOpenShopping = { showShopping = true },
            onOpenAppearance = { showAppearance = true },
            onOpenUpcoming = { showUpcoming = true },
            onOpenStats = { showStats = true },
            onScan = startScan,
            backupStatus = backupStatus,
            onEnableBackup = viewModel::enableBackup,
            onDisableBackup = viewModel::disableBackup,
            onBackupNow = viewModel::backupNow,
            onMarkOwned = { id, volume -> viewModel.addVolumes(id, setOf(volume)) },
            listFilter = listFilter,
            listSort = listSort,
            onListFilter = viewModel::setListFilter,
            onListSort = viewModel::setListSort,
            undo = undo,
            onUndo = viewModel::undo,
            onForgetUndo = viewModel::forgetUndo,
        )
    } else {
        BackHandler { selectedId = null }
        SeriesDetailScreen(
            series = selected,
            onBack = { selectedId = null },
            onToggleVolume = { viewModel.toggleVolume(selected.id, it) },
            onAddVolumes = { viewModel.addVolumes(selected.id, it) },
            onEdit = { title, total -> viewModel.editSeries(selected.id, title, total) },
            onDelete = {
                viewModel.deleteSeries(selected.id)
                selectedId = null
            },
            releases = releases[selected.id],
            checking = checking,
            checkError = checkError,
            onToggleWatch = { viewModel.toggleWatch(selected.id) },
            onCheckNow = viewModel::checkReleasesNow,
            coverSearch = coverSearch,
            onSearchCovers = { viewModel.searchCovers(selected) },
            onChooseCover = { viewModel.chooseCover(selected.id, it) },
            onDismissCoverSearch = viewModel::dismissCoverSearch,
            onSetRead = { volume, read -> viewModel.setRead(selected.id, volume, read) },
            onLend = { volume, to -> viewModel.lend(selected.id, volume, to) },
            onReturnLoan = { volume -> viewModel.returnLoan(selected.id, volume) },
            gridMinSize = appearance.gridSize.minDp.dp,
        )
    }

    if (updateState != UpdateState.Idle) {
        UpdateDialog(
            state = updateState,
            onDownload = { updateViewModel.download(context, it) },
            onDismiss = updateViewModel::dismiss,
            autoCheck = autoCheck,
            onAutoCheck = updateViewModel::setAutoCheck,
        )
    }

    scan?.let { state ->
        ScanDialog(
            state = state,
            onIsbn = viewModel::lookupIsbn,
            onScanAgain = {
                viewModel.dismissScan()
                startScan()
            },
            onMarkOwned = viewModel::markScannedOwned,
            onCreateSeries = { selectedId = viewModel.createScannedSeries(it) },
            onDismiss = viewModel::dismissScan,
        )
    }

    if (saveFailed) {
        SaveErrorDialog(onDismiss = viewModel::dismissSaveError)
    }
}
