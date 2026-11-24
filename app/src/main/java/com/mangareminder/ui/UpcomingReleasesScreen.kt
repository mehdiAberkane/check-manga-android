@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.data.Series
import com.mangareminder.releases.CalendarEntry
import com.mangareminder.releases.SeriesReleases
import com.mangareminder.releases.formatReleaseDate
import com.mangareminder.releases.releaseCalendar

/**
 * Tomes à paraître de toutes les séries surveillées, puis ceux déjà parus mais pas encore achetés.
 * Lit seulement le cache des sorties : aucune requête réseau.
 */
@Composable
fun UpcomingReleasesScreen(
    series: List<Series>,
    releases: Map<Long, SeriesReleases>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val calendar = remember(series, releases) { releaseCalendar(series, releases) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upcoming_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (calendar.isEmpty) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.upcoming_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (calendar.upcoming.isNotEmpty()) {
                    item(key = "upcoming") { SectionTitle(stringResource(R.string.upcoming_section_next)) }
                    items(calendar.upcoming, key = { "u-${it.series.id}-${it.release.volume}" }) { entry ->
                        ReleaseRow(entry, upcoming = true, onClick = { onOpen(entry.series.id) })
                    }
                }
                if (calendar.published.isNotEmpty()) {
                    item(key = "published") { SectionTitle(stringResource(R.string.upcoming_section_published)) }
                    items(calendar.published, key = { "p-${it.series.id}-${it.release.volume}" }) { entry ->
                        ReleaseRow(entry, upcoming = false, onClick = { onOpen(entry.series.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ReleaseRow(entry: CalendarEntry, upcoming: Boolean, onClick: () -> Unit) {
    val release = entry.release
    val date = formatReleaseDate(release.date)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover = release.cover ?: entry.series.cover
            if (cover != null) {
                CoverImage(cover, contentDescription = null, modifier = Modifier.size(width = 40.dp, height = 60.dp))
            } else {
                Spacer(Modifier.size(width = 40.dp, height = 60.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.series.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        date == null -> stringResource(R.string.release_volume, release.volume)
                        upcoming -> stringResource(R.string.release_upcoming, release.volume, date)
                        else -> stringResource(R.string.release_published, release.volume, date)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (upcoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
