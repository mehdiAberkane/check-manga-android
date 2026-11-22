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

class MainActivity : ComponentActivity() {
    /** Demande d'ouverture de la liste « À acheter » (widget), consommée par l'écran. */
    private val openShopping = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openShopping.value = intent.getBooleanExtra(EXTRA_SHOPPING, false)
        setContent {
            MangaReminderTheme {
                MangaReminderApp(
                    openShopping = openShopping.value,
                    onShoppingOpened = { openShopping.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOPPING, false)) openShopping.value = true
    }

    companion object {
        const val EXTRA_SHOPPING = "com.mangareminder.SHOPPING"
    }
}

@Composable
private fun MangaReminderApp(
    openShopping: Boolean,
    onShoppingOpened: () -> Unit,
    viewModel: SeriesViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val releases by viewModel.releases.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val checkError by viewModel.checkError.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val coverSearch by viewModel.coverSearch.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val listFilter by viewModel.listFilter.collectAsStateWithLifecycle()
    val listSort by viewModel.listSort.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showShopping by rememberSaveable { mutableStateOf(false) }
    val selected = series.find { it.id == selectedId }

    LaunchedEffect(openShopping) {
        if (openShopping) {
            selectedId = null
            showShopping = true
            onShoppingOpened()
        }
    }

    if (selected == null && showShopping) {
        BackHandler { showShopping = false }
        ShoppingListScreen(
            series = series,
            releases = releases,
            onBack = { showShopping = false },
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
        )
    }

    if (updateState != UpdateState.Idle) {
        UpdateDialog(
            state = updateState,
            onInstall = updateViewModel::install,
            onOpenPermission = { context.startActivity(updateViewModel.permissionSettings(it)) },
            onDismiss = updateViewModel::dismiss,
        )
    }

    if (saveFailed) {
        SaveErrorDialog(onDismiss = viewModel::dismissSaveError)
    }
}
