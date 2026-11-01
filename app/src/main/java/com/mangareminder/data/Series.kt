package com.mangareminder.data

data class Series(
    val id: Long,
    val title: String,
    /** Nombre de tomes parus, ou null si la série est en cours / inconnue. */
    val totalVolumes: Int? = null,
    val ownedVolumes: Set<Int> = emptySet(),
) {
    val ownedCount: Int get() = ownedVolumes.size

    val lastOwned: Int get() = ownedVolumes.maxOrNull() ?: 0

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
