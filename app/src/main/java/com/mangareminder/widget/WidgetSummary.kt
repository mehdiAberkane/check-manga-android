package com.mangareminder.widget

import com.mangareminder.data.Series
import com.mangareminder.releases.Release
import com.mangareminder.releases.SeriesReleases
import java.time.LocalDate

// --- Contenu du widget, sans dépendance Android (testé en unitaire). ---

const val WIDGET_MAX_RELEASES = 3

data class WidgetSummary(
    /** Tomes à acheter : manquants de la collection + tomes déjà parus après le dernier connu. */
    val toBuy: Int,
    /** Prochain tome de chaque série surveillée, le plus proche d'abord. */
    val nextReleases: List<Pair<String, Release>>,
)

fun widgetSummary(
    series: List<Series>,
    releases: Map<Long, SeriesReleases>,
    today: LocalDate = LocalDate.now(),
): WidgetSummary {
    var toBuy = 0
    val next = mutableListOf<Pair<String, Release>>()
    for (s in series) {
        toBuy += s.missingVolumes.size
        if (!s.watched) continue
        // Tomes trouvés après le dernier que l'utilisateur connaît (possédé ou compté dans le total).
        val newer = releases[s.id]?.releases.orEmpty()
            .filter { it.volume > s.lastKnownVolume }
            .distinctBy { it.volume }
        toBuy += newer.count { !it.isUpcoming(today) }
        newer.minByOrNull { it.volume }?.let { next += s.title to it }
    }
    // Sorties datées d'abord (la plus proche en premier), puis les autres par titre.
    val sorted = next.sortedWith(
        compareBy<Pair<String, Release>> { it.second.date == null }
            .thenBy { it.second.date.orEmpty() }
            .thenBy { it.first },
    )
    return WidgetSummary(toBuy, sorted.take(WIDGET_MAX_RELEASES))
}
