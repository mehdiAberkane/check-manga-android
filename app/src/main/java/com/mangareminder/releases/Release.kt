package com.mangareminder.releases

import java.text.Normalizer
import java.time.LocalDate

/** Un tome trouvé chez une source (Google Books…). */
data class Release(
    val volume: Int,
    val title: String,
    /** Date de parution telle que donnée par la source : "2026-12-05", "2026-12" ou "2026". */
    val date: String?,
    val source: String,
    val link: String?,
) {
    /** Vrai seulement pour une date complète postérieure à aujourd'hui. */
    fun isUpcoming(today: LocalDate = LocalDate.now()): Boolean =
        date != null && date.length == 10 && date > today.toString()

    fun describe(today: LocalDate = LocalDate.now()): String {
        val formatted = formatReleaseDate(date) ?: return "Tome $volume"
        return if (isUpcoming(today)) "Tome $volume · à paraître le $formatted"
        else "Tome $volume · paru le $formatted"
    }
}

/** Résultat de la dernière vérification d'une série. */
data class SeriesReleases(
    val checkedAt: Long,
    /** Tomes trouvés, du plus récent au plus ancien. */
    val releases: List<Release>,
    /** Plus haut tome déjà notifié, pour ne pas notifier deux fois. */
    val notifiedMax: Int,
) {
    val latest: Release? get() = releases.maxByOrNull { it.volume }

    /** Le dernier tome trouvé s'il est plus récent que ce que l'utilisateur connaît. */
    fun newRelease(lastKnownVolume: Int): Release? = latest?.takeIf { it.volume > lastKnownVolume }
}

// --- Analyse des titres, sans dépendance Android (testée en unitaire). ---

/** Minuscules, sans accents ni ponctuation : "Édition T.12" -> "edition t 12". */
fun normalizeTitle(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/** Produits dérivés qui partagent le titre de la série mais ne sont pas des tomes. */
private val EXCLUDED_WORDS = setOf(
    "coffret", "artbook", "fanbook", "databook", "guidebook", "guide", "roman", "novel",
    "coloriage", "calendrier", "agenda", "anime", "film", "making",
)

private val VOLUME_PATTERN = Regex("\\b(?:tome|tomes|volume|vol|t|n)\\s*(\\d{1,3})\\b")
private val TRAILING_NUMBER = Regex("^(\\d{1,3})$")

/**
 * Numéro de tome d'un livre de la série, ou null si le livre n'appartient pas à la série
 * ou n'a pas de numéro reconnaissable.
 */
fun extractVolume(bookTitle: String, seriesTitle: String): Int? {
    val book = normalizeTitle(bookTitle)
    val series = normalizeTitle(seriesTitle)
    if (series.isEmpty()) return null
    val index = " $book ".indexOf(" $series ")
    if (index < 0) return null
    val rest = book.substring(minOf(book.length, index + series.length)).trim()
    if (rest.split(' ').any { it in EXCLUDED_WORDS }) return null

    val number = VOLUME_PATTERN.find(rest)?.groupValues?.get(1)
        ?: TRAILING_NUMBER.find(rest)?.groupValues?.get(1)
        ?: return null
    return number.toInt().takeIf { it in 1..999 }
}

/** "2026-12-05" -> "05/12/2026", "2026-12" -> "12/2026", "2026" -> "2026". */
fun formatReleaseDate(date: String?): String? {
    if (date.isNullOrBlank()) return null
    val parts = date.split('-')
    return parts.reversed().joinToString("/")
}

/** Garde un tome par numéro, en préférant la date la plus précise. */
fun mergeReleases(releases: List<Release>): List<Release> =
    releases.groupBy { it.volume }
        .map { (_, sameVolume) -> sameVolume.maxBy { it.date?.length ?: 0 } }
        .sortedByDescending { it.volume }
