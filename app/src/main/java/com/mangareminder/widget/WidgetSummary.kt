package com.mangareminder.widget

import com.mangareminder.data.Series
import com.mangareminder.releases.Release
import com.mangareminder.releases.SeriesReleases
import java.time.LocalDate
import com.mangareminder.data.shoppingList

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
    // Même calcul que l'écran « À acheter » : les deux affichent le même nombre.
    val entries = shoppingList(series, releases)
    val next = entries.mapNotNull { entry -> entry.newReleases.firstOrNull()?.let { entry.series.title to it } }
    // Sorties datées d'abord (la plus proche en premier), puis les autres par titre.
    val sorted = next.sortedWith(
        compareBy<Pair<String, Release>> { it.second.date == null }
            .thenBy { it.second.date.orEmpty() }
            .thenBy { it.first },
    )
    return WidgetSummary(entries.sumOf { it.availableCount(today) }, sorted.take(WIDGET_MAX_RELEASES))
}

/**
 * Évite de redessiner le widget quand son contenu n'a pas changé (la plupart des appuis sur un
 * tome ne changent ni le nombre à acheter ni les prochaines sorties). Partagé entre les fils.
 */
class WidgetRedrawGate {
    private var last: WidgetSummary? = null

    /** Vrai si [summary] diffère du dernier contenu dessiné ; le retient comme dessiné. */
    @Synchronized
    fun shouldRedraw(summary: WidgetSummary): Boolean {
        if (summary == last) return false
        last = summary
        return true
    }
}
