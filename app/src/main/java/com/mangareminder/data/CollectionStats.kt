package com.mangareminder.data

// --- Écran « Statistiques », sans dépendance Android (testé en unitaire). ---

/** Nombre de séries montrées dans chaque classement. */
const val STATS_TOP = 5

data class CollectionStats(
    val series: Int,
    val ownedVolumes: Int,
    val completeSeries: Int,
    val missingVolumes: Int,
    val watchedSeries: Int,
    /** Tomes lus, parmi les tomes possédés. */
    val readVolumes: Int,
    /** Tomes prêtés en ce moment. */
    val lentVolumes: Int,
    /** Séries où l'on a le plus de tomes. */
    val mostOwned: List<Series>,
    /** Séries où il manque le plus de tomes (trous, ou jusqu'au total connu). */
    val mostMissing: List<Series>,
)

/** Calculé sur la collection en mémoire ; à égalité, les séries sont rangées par titre. */
fun collectionStats(series: List<Series>): CollectionStats {
    val byTitle = seriesOrder()
    return CollectionStats(
        series = series.size,
        ownedVolumes = series.sumOf { it.ownedCount },
        completeSeries = series.count { it.isComplete },
        missingVolumes = series.sumOf { it.missingVolumes.size },
        watchedSeries = series.count { it.watched },
        readVolumes = series.sumOf { s -> s.readVolumes.count { it in s.ownedVolumes } },
        lentVolumes = series.sumOf { s -> s.loans.keys.count { it in s.ownedVolumes } },
        mostOwned = series.filter { it.ownedCount > 0 }
            .sortedWith(compareByDescending<Series> { it.ownedCount }.then(byTitle))
            .take(STATS_TOP),
        mostMissing = series.filter { it.missingVolumes.isNotEmpty() }
            .sortedWith(compareByDescending<Series> { it.missingVolumes.size }.then(byTitle))
            .take(STATS_TOP),
    )
}
