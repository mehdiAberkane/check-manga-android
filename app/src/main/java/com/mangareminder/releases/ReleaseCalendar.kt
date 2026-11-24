package com.mangareminder.releases

import com.mangareminder.data.Series
import java.time.LocalDate

// --- Écran « Prochaines sorties », sans dépendance Android (testé en unitaire). ---

/** Un tome d'une série surveillée, pas encore possédé. */
data class CalendarEntry(val series: Series, val release: Release)

data class ReleaseCalendar(
    /** Tomes à paraître, le plus proche d'abord. */
    val upcoming: List<CalendarEntry>,
    /** Tomes déjà parus (ou sans date) mais pas encore achetés, le plus récent d'abord. */
    val published: List<CalendarEntry>,
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && published.isEmpty()
}

/**
 * Date postérieure à aujourd'hui, à la précision donnée par la source : « 2027 » ou « 2027-01 »
 * sont à paraître le 05/12/2026, « 2026-12 » ne l'est plus (le mois est commencé).
 */
fun Release.isAfter(today: LocalDate): Boolean = date != null && date > today.toString().take(date.length)

/**
 * Tous les tomes trouvés par la surveillance (cache des sorties, aucune requête) que l'utilisateur
 * n'a pas, au-delà de son dernier tome possédé. Les trous plus anciens de la collection sont dans
 * la liste « À acheter », pas ici.
 */
fun releaseCalendar(
    series: List<Series>,
    releases: Map<Long, SeriesReleases>,
    today: LocalDate = LocalDate.now(),
): ReleaseCalendar {
    val entries = series.filter { it.watched }.flatMap { s ->
        releases[s.id]?.releases.orEmpty()
            .filter { it.volume > s.lastOwned && it.volume !in s.ownedVolumes }
            .distinctBy { it.volume }
            .map { CalendarEntry(s, it) }
    }
    val (upcoming, published) = entries.partition { it.release.isAfter(today) }
    return ReleaseCalendar(
        upcoming = upcoming.sortedWith(
            compareBy<CalendarEntry> { it.release.date }.thenBy { it.series.title }.thenBy { it.release.volume },
        ),
        published = published.sortedWith(
            compareBy<CalendarEntry> { it.release.date == null }
                .thenByDescending { it.release.date.orEmpty() }
                .thenBy { it.series.title }
                .thenBy { it.release.volume },
        ),
    )
}
