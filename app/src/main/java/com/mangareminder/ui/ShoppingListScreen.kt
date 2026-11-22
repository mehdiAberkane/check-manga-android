@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.data.Series
import com.mangareminder.data.ShoppingEntry
import com.mangareminder.data.formatVolumes
import com.mangareminder.data.shoppingList
import com.mangareminder.data.shoppingShareText
import com.mangareminder.releases.SeriesReleases
import com.mangareminder.releases.formatReleaseDate

/** Tomes à acheter, toutes séries confondues, avec partage en texte simple. */
@Composable
fun ShoppingListScreen(
    series: List<Series>,
    releases: Map<Long, SeriesReleases>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val entries = remember(series, releases) { shoppingList(series, releases) }
    val available = entries.sumOf { it.availableCount() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = {
                            val text = shoppingShareText(entries) { release ->
                                val date = formatReleaseDate(release.date)
                                when {
                                    date == null -> release.volume.toString()
                                    release.isUpcoming() -> resources.getString(R.string.shopping_new_upcoming, release.volume, date)
                                    else -> resources.getString(R.string.shopping_new_published, release.volume, date)
                                }
                            }
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, resources.getString(R.string.shopping_title)))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.shopping_empty),
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
                item {
                    Text(
                        if (available == 0) stringResource(R.string.shopping_only_upcoming)
                        else pluralStringResource(R.plurals.widget_to_buy, available, available),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(entries, key = { it.series.id }) { entry ->
                    ShoppingCard(entry, onClick = { onOpen(entry.series.id) })
                }
            }
        }
    }
}

@Composable
private fun ShoppingCard(entry: ShoppingEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.series.title, style = MaterialTheme.typography.titleMedium)
            if (entry.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.missing_volumes, formatVolumes(entry.missing)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            entry.newReleases.forEach { release ->
                Text(
                    stringResource(R.string.new_release, release.describe().text()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
