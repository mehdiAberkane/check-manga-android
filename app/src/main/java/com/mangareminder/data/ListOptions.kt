package com.mangareminder.data

// --- Filtres et tris de la liste des séries, sans dépendance Android (testé en unitaire). ---

enum class ListFilter { ALL, INCOMPLETE, COMPLETE, WATCHED, NEW_RELEASE, LENT }

enum class ListSort { TITLE, ADDED, MISSING }

/**
 * Applique le filtre puis le tri à une liste déjà rangée par titre ([seriesOrder]).
 * Les tris sont stables : à égalité, l'ordre par titre est gardé.
 * @param hasNewRelease vrai si une sortie plus récente que le dernier tome connu a été trouvée.
 */
fun applyListOptions(
    series: List<Series>,
    filter: ListFilter,
    sort: ListSort,
    hasNewRelease: (Series) -> Boolean,
): List<Series> {
    val filtered = when (filter) {
        ListFilter.ALL -> series
        // Une série en cours sans trou n'est ni complète ni incomplète : elle n'a pas de manquant connu.
        ListFilter.INCOMPLETE -> series.filter { it.missingVolumes.isNotEmpty() }
        ListFilter.COMPLETE -> series.filter { it.isComplete }
        ListFilter.WATCHED -> series.filter { it.watched }
        ListFilter.NEW_RELEASE -> series.filter { it.watched && hasNewRelease(it) }
        ListFilter.LENT -> series.filter { it.loans.isNotEmpty() }
    }
    return when (sort) {
        ListSort.TITLE -> filtered
        // L'id est l'heure de création (ms) : les plus récentes d'abord.
        ListSort.ADDED -> filtered.sortedByDescending { it.id }
        ListSort.MISSING -> filtered.sortedByDescending { it.missingVolumes.size }
    }
}

/** Relit un choix mémorisé ; valeur par défaut si elle est inconnue (ancienne version, préférence abîmée). */
inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
