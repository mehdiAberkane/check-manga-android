package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionStatsTest {

    @Test
    fun `collection vide`() {
        val stats = collectionStats(emptyList())
        assertEquals(0, stats.series)
        assertEquals(0, stats.ownedVolumes)
        assertTrue(stats.mostOwned.isEmpty())
        assertTrue(stats.mostMissing.isEmpty())
    }

    @Test
    fun `totaux de la collection`() {
        val series = listOf(
            // Complète, 3 tomes, 2 lus, 1 prêté.
            Series(1, "Akira", totalVolumes = 3, ownedVolumes = setOf(1, 2, 3), readVolumes = setOf(1, 2),
                loans = mapOf(3 to Loan("Léa"))),
            // 2 manquants jusqu'au total, surveillée.
            Series(2, "Bleach", totalVolumes = 4, ownedVolumes = setOf(1, 3), watched = true),
            // Sans total : seul le trou (tome 2) manque.
            Series(3, "Claymore", ownedVolumes = setOf(1, 3)),
            Series(4, "Dorohedoro"),
        )
        val stats = collectionStats(series)
        assertEquals(4, stats.series)
        assertEquals(7, stats.ownedVolumes)
        assertEquals(1, stats.completeSeries)
        assertEquals(3, stats.missingVolumes)
        assertEquals(1, stats.watchedSeries)
        assertEquals(2, stats.readVolumes)
        assertEquals(1, stats.lentVolumes)
    }

    @Test
    fun `classements limités, égalités rangées par titre`() {
        val series = (1..8).map { i ->
            Series(i.toLong(), "Série ${'A' + (8 - i)}", totalVolumes = 10, ownedVolumes = (1..i).toSet())
        } + Series(20, "Vide", totalVolumes = 2) + Series(21, "Égal", totalVolumes = 10, ownedVolumes = (1..8).toSet())
        val stats = collectionStats(series)
        assertEquals(STATS_TOP, stats.mostOwned.size)
        // 8 tomes : « Égal » et « Série A » ; ordre alphabétique sans tenir compte des accents.
        assertEquals(listOf("Égal", "Série A", "Série B", "Série C", "Série D"), stats.mostOwned.map { it.title })
        assertEquals(listOf("Série H", "Série G", "Série F", "Série E", "Série D"), stats.mostMissing.map { it.title })
        assertTrue(stats.mostOwned.none { it.title == "Vide" })
    }
}
