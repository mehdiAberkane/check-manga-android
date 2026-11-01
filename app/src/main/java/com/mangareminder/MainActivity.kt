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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangareminder.ui.SeriesDetailScreen
import com.mangareminder.ui.SeriesListScreen
import com.mangareminder.ui.SeriesViewModel
import com.mangareminder.ui.theme.MangaReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MangaReminderTheme {
                MangaReminderApp()
            }
        }
    }
}

@Composable
private fun MangaReminderApp(viewModel: SeriesViewModel = viewModel()) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = series.find { it.id == selectedId }

    if (selected == null) {
        SeriesListScreen(
            series = series,
            onOpen = { selectedId = it },
            onAdd = { title, total -> selectedId = viewModel.addSeries(title, total) },
            onExport = viewModel::exportTo,
            onReadBackup = viewModel::readBackup,
            onReplaceAll = viewModel::replaceAll,
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
        )
    }
}
