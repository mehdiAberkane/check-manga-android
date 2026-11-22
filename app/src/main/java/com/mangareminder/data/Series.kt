package com.mangareminder.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class Series(
    val id: Long,
    val title: String,
    /** Nombre de tomes parus, ou null si la série est en cours / inconnue. */
    val totalVolumes: Int? = null,
    val ownedVolumes: Set<Int> = emptySet(),
    /** Surveillance des sorties : l'app cherche régulièrement les nouveaux tomes. */
    val watched: Boolean = false,
) {
    val ownedCount: Int get() = ownedVolumes.size

    val lastOwned: Int get() = ownedVolumes.maxOrNull() ?: 0

    /** Dernier tome connu de l'utilisateur : possédé ou compté dans le total. */
    val lastKnownVolume: Int get() = maxOf(lastOwned, totalVolumes ?: 0)

    val isComplete: Boolean
        get() = totalVolumes != null && (1..totalVolumes).all { it in ownedVolumes }

    /** Jusqu'au total si on le connaît, sinon les trous avant le dernier tome possédé. */
    val missingVolumes: List<Int>
        get() = (1..(totalVolumes ?: lastOwned)).filterNot { it in ownedVolumes }
}

/** Formate une liste de tomes en plages compactes : [1, 2, 3, 5, 7, 8] -> "1-3, 5, 7-8". */
fun formatVolumes(volumes: Collection<Int>): String {
    val sorted = volumes.sorted()
    if (sorted.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = sorted.first()
    var end = start
    for (v in sorted.drop(1)) {
        if (v == end + 1) {
            end = v
        } else {
            ranges += if (start == end) "$start" else "$start-$end"
            start = v
            end = v
        }
    }
    ranges += if (start == end) "$start" else "$start-$end"
    return ranges.joinToString(", ")
}

/** Lit une saisie du type "1-12, 15 17" ; renvoie null si elle est invalide. */
fun parseVolumes(input: String): Set<Int>? {
    val parts = input
        .replace(Regex("\\s*-\\s*"), "-")
        .split(',', ';', ' ')
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return null

    val result = mutableSetOf<Int>()
    for (part in parts) {
        val bounds = part.split('-')
        when (bounds.size) {
            1 -> result += bounds[0].toIntOrNull()?.takeIf { it in 1..MAX_VOLUME } ?: return null
            2 -> {
                val from = bounds[0].toIntOrNull() ?: return null
                val to = bounds[1].toIntOrNull() ?: return null
                if (from < 1 || to < from || to > MAX_VOLUME) return null
                result += from..to
            }
            else -> return null
        }
    }
    return result
}

const val MAX_VOLUME = 999

/**
 * Clé de recherche et de tri d'un titre : minuscules, sans accents, ponctuation (dont l'apostrophe
 * typographique ’) remplacée par une espace. Les lettres de tous les alphabets sont gardées.
 * "L’Attaque des Titans" -> "l attaque des titans", "Dédé" -> "dede".
 */
fun searchKey(text: String): String =
    Normalizer.normalize(
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, ""),
        Normalizer.Form.NFC,
    )
        .lowercase(Locale.ROOT)
        .replace(NOT_LETTER_OR_DIGIT, " ")
        .trim()

/** Vrai si le titre contient la recherche, sans tenir compte des accents, de la casse ni de la ponctuation. */
fun matchesSearch(title: String, query: String): Boolean = searchKey(title).contains(searchKey(query))

/**
 * Ordre alphabétique français : « Été » entre « E » et « F », pas après « Z ».
 * Nouveau comparateur à chaque appel : un Collator ne doit pas être partagé entre threads.
 */
fun seriesOrder(): Comparator<Series> {
    val collator = Collator.getInstance(Locale.FRENCH)
    return compareBy<Series, String>(collator) { searchKey(it.title) }
        .thenBy(collator) { it.title }
        .thenBy { it.id }
}

// Accents latins seulement (é, è, ç…) : les marques des autres écritures (ex. le maru de ピ)
// font partie de la lettre et sont recomposées par NFC.
private val DIACRITICS = Regex("[\\u0300-\\u036f]+")
private val NOT_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")
