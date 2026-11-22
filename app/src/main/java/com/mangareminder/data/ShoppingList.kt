package com.mangareminder.data

import com.mangareminder.releases.Release
import com.mangareminder.releases.SeriesReleases
import java.time.LocalDate

// --- Liste « À acheter », sans dépendance Android (testé en unitaire). ---

/** Ce qu'il manque pour une série : trous de la collection et nouveaux tomes trouvés par la surveillance. */
data class ShoppingEntry(
    val series: Series,
    /** Tomes manquants (trous, ou jusqu'au total connu). */
    val missing: List<Int>,
    /** Tomes trouvés après le dernier connu (séries surveillées), parus ou à paraître, par numéro. */
    val newReleases: List<Release>,
) {
    /** Tomes déjà disponibles en magasin : les manquants et les nouveautés déjà parues. */
    fun availableCount(today: LocalDate = LocalDate.now()): Int =
        missing.size + newReleases.count { !it.isUpcoming(today) }
}

/** Séries qui ont quelque chose à acheter, dans l'ordre de la liste reçue. */
fun shoppingList(series: List<Series>, releases: Map<Long, SeriesReleases>): List<ShoppingEntry> =
    series.mapNotNull { s ->
        val newer = if (!s.watched) emptyList()
        else releases[s.id]?.releases.orEmpty()
            .filter { it.volume > s.lastKnownVolume }
            .distinctBy { it.volume }
            .sortedBy { it.volume }
        ShoppingEntry(s, s.missingVolumes, newer).takeIf { it.missing.isNotEmpty() || it.newReleases.isNotEmpty() }
    }

/**
 * Texte partagé, une ligne par série :
 * « One Piece : 45, 47-48 », « Frieren : 14 (paraît le 05/12/2026) ».
 * @param describeNew texte d'un nouveau tome, ex. « 14 (paraît le 05/12/2026) » (fourni par l'écran, en français).
 */
fun shoppingShareText(entries: List<ShoppingEntry>, describeNew: (Release) -> String): String =
    entries.joinToString("\n") { entry ->
        val parts = listOfNotNull(
            formatVolumes(entry.missing).takeIf { it.isNotEmpty() },
            entry.newReleases.joinToString(", ") { describeNew(it) }.takeIf { it.isNotEmpty() },
        )
        "${entry.series.title} : ${parts.joinToString(", ")}"
    }
