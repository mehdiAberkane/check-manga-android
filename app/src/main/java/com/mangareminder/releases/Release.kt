package com.mangareminder.releases

import com.mangareminder.R
import com.mangareminder.data.Series
import com.mangareminder.UiMessage
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
    /** Miniature de la couverture (https, Google Books), si la source en donne une. */
    val cover: String? = null,
) {
    /** Vrai seulement pour une date complète postérieure à aujourd'hui. */
    fun isUpcoming(today: LocalDate = LocalDate.now()): Boolean =
        date != null && date.length == 10 && date > today.toString()

    /** « Tome 12 · paru le 05/12/2026 », résolu à l'affichage (strings.xml). */
    fun describe(today: LocalDate = LocalDate.now()): UiMessage {
        val formatted = formatReleaseDate(date) ?: return UiMessage(R.string.release_volume, volume)
        return if (isUpcoming(today)) UiMessage(R.string.release_upcoming, volume, formatted)
        else UiMessage(R.string.release_published, volume, formatted)
    }
}

/**
 * Résultat d'une vérification pour une série : ce qu'on garde en cache, la sortie à annoncer et
 * le tome à rappeler le jour de sa sortie (sans dépendance Android, testé en unitaire).
 */
data class CheckOutcome(val stored: SeriesReleases, val notify: Release?, val remind: Release? = null)

/**
 * Délai pendant lequel un tome sorti reste rappelé : la vérification passe toutes les 12 h, mais le
 * téléphone peut rester plusieurs jours sans réseau. Au-delà, c'est une vieille sortie.
 */
const val RELEASE_REMINDER_DAYS = 7L

/**
 * Décide quoi garder et quoi notifier après une recherche.
 * - On annonce le plus haut tome trouvé s'il dépasse le dernier tome connu de l'utilisateur et
 *   le dernier tome déjà notifié (jamais deux fois le même).
 * - On rappelle le jour de sa sortie le plus haut tome à date complète, sorti depuis au plus
 *   [RELEASE_REMINDER_DAYS] jours, que l'utilisateur ne possède pas et qui n'a pas encore été
 *   rappelé. Pas de rappel pour le tome annoncé à l'instant (l'annonce donne déjà sa date).
 * - Une recherche vide (quota, titre mal reconnu) n'efface pas les résultats connus.
 */
fun checkOutcome(
    series: Series,
    found: List<Release>,
    previous: SeriesReleases?,
    now: Long,
    today: LocalDate = LocalDate.now(),
): CheckOutcome {
    val notifiedMax = previous?.notifiedMax ?: 0
    val remindedMax = previous?.remindedMax ?: 0
    val releases = found.ifEmpty { previous?.releases ?: emptyList() }
    val latest = found.maxByOrNull { it.volume }
    val notify = latest?.takeIf { it.volume > series.lastKnownVolume && it.volume > notifiedMax }
    val due = releases
        .filter { it.volume > remindedMax && it.volume !in series.ownedVolumes && it.isOutSince(today) }
        .maxByOrNull { it.volume }
    return CheckOutcome(
        stored = SeriesReleases(
            checkedAt = now,
            releases = releases,
            notifiedMax = notify?.volume ?: notifiedMax,
            remindedMax = due?.volume ?: remindedMax,
        ),
        notify = notify,
        remind = due?.takeIf { it.volume != notify?.volume },
    )
}

/** Date complète comprise entre il y a [RELEASE_REMINDER_DAYS] jours et aujourd'hui inclus. */
private fun Release.isOutSince(today: LocalDate): Boolean {
    if (date == null || date.length != 10) return false
    return date <= today.toString() && date >= today.minusDays(RELEASE_REMINDER_DAYS).toString()
}

/** Résultat de la dernière vérification d'une série. */
data class SeriesReleases(
    val checkedAt: Long,
    /** Tomes trouvés, du plus récent au plus ancien. */
    val releases: List<Release>,
    /** Plus haut tome déjà notifié, pour ne pas notifier deux fois. */
    val notifiedMax: Int,
    /** Plus haut tome déjà rappelé le jour de sa sortie. */
    val remindedMax: Int = 0,
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

/**
 * Mots acceptés entre le titre de la série et le numéro du tome : ils ne changent ni la série ni
 * la numérotation (« One Piece - Édition originale - Tome 105 »). Tout autre mot (« Super »,
 * « Next Generations », « Perfect », « double », « coffret »…) désigne une autre série ou une
 * autre édition, dont les numéros ne correspondent pas : le livre est écarté.
 */
private val NEUTRAL_WORDS = setOf(
    "edition", "editions", "originale", "original", "nouvelle", "francaise", "fr", "vf", "manga",
    "tome", "tomes", "volume", "volumes", "vol", "t", "n", "no", "numero",
)

private val NUMBER = Regex("^(\\d{1,3})$")
// « t12 », « vol3 », « n4 » : préfixe collé au numéro après normalisation.
private val PREFIXED_NUMBER = Regex("^(?:t|tome|vol|volume|n|no)(\\d{1,3})$")

/**
 * Numéro de tome d'un livre de la série, ou null si le livre n'appartient pas à la série
 * ou n'a pas de numéro reconnaissable.
 * - le titre de la série doit être au début du titre du livre ;
 * - entre les deux, seulement des mots neutres ([NEUTRAL_WORDS]) ;
 * - après le numéro, tout est accepté (sous-titre du tome).
 * @param displayNumber numéro donné à part par la source (Google Books `seriesInfo`), utilisé si
 *   le titre n'en contient pas.
 */
fun extractVolume(bookTitle: String, seriesTitle: String, displayNumber: String? = null): Int? {
    val book = normalizeTitle(bookTitle)
    val series = normalizeTitle(seriesTitle)
    if (series.isEmpty() || !(book == series || book.startsWith("$series "))) return null
    val words = book.removePrefix(series).trim().split(' ').filter { it.isNotEmpty() }
    for (word in words) {
        (NUMBER.find(word) ?: PREFIXED_NUMBER.find(word))?.let { match ->
            return match.groupValues[1].toInt().takeIf { it in 1..999 }
        }
        if (word !in NEUTRAL_WORDS) return null
    }
    return displayNumber?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }
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
