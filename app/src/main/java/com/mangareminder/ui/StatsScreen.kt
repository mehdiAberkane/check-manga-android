@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.data.Series
import com.mangareminder.data.collectionStats

/** Vue d'ensemble de la collection, calculée sur la liste en mémoire. */
@Composable
fun StatsScreen(
    series: List<Series>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val stats = remember(series) { collectionStats(series) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (stats.series == 0) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.stats_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatRow(stringResource(R.string.stats_series), stats.series)
                        StatRow(stringResource(R.string.stats_owned), stats.ownedVolumes)
                        StatRow(stringResource(R.string.stats_complete), stats.completeSeries)
                        StatRow(stringResource(R.string.stats_missing), stats.missingVolumes)
                        StatRow(stringResource(R.string.stats_watched), stats.watchedSeries)
                        StatRow(stringResource(R.string.stats_read), stats.readVolumes)
                        StatRow(stringResource(R.string.stats_lent), stats.lentVolumes)
                    }
                }
            }
            if (stats.mostOwned.isNotEmpty()) {
                item {
                    Ranking(stringResource(R.string.stats_most_owned), stats.mostOwned, onOpen) { s ->
                        s.totalVolumes?.let { pluralStringResource(R.plurals.owned_of_total, it, s.ownedCount, it) }
                            ?: pluralStringResource(R.plurals.stats_volumes, s.ownedCount, s.ownedCount)
                    }
                }
            }
            if (stats.mostMissing.isNotEmpty()) {
                item {
                    Ranking(stringResource(R.string.stats_most_missing), stats.mostMissing, onOpen) { s ->
                        pluralStringResource(R.plurals.stats_missing_count, s.missingVolumes.size, s.missingVolumes.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Ranking(title: String, series: List<Series>, onOpen: (Long) -> Unit, detail: @Composable (Series) -> String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            series.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onOpen(s.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        detail(s),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
