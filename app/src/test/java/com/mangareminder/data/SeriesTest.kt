package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesTest {

    @Test
    fun `saisie de tomes en plages, listes et séparateurs`() {
        assertEquals((1..12).toSet() + 15 + 17, parseVolumes("1-12, 15, 17"))
        assertEquals(setOf(1, 2, 3, 7), parseVolumes("1 - 3 7"))
        assertEquals(setOf(4, 5, 9), parseVolumes("4;5 ; 9"))
        assertEquals(setOf(3), parseVolumes("3-3"))
        assertEquals(setOf(1, 2, 3), parseVolumes("1-3, 2"))
        assertEquals(setOf(999), parseVolumes("999"))
    }

    @Test
    fun `saisie de tomes invalide`() {
        assertNull(parseVolumes(""))
        assertNull(parseVolumes(" , ; "))
        assertNull(parseVolumes("0"))
        assertNull(parseVolumes("1000"))
        assertNull(parseVolumes("998-1000"))
        assertNull(parseVolumes("0-3"))
        assertNull(parseVolumes("5-2"))
        assertNull(parseVolumes("1-2-3"))
        assertNull(parseVolumes("abc"))
        assertNull(parseVolumes("1, x"))
        assertNull(parseVolumes("-3"))
    }

    @Test
    fun `affichage compact des tomes`() {
        assertEquals("", formatVolumes(emptyList()))
        assertEquals("4", formatVolumes(listOf(4)))
        assertEquals("1-3, 5, 7-8", formatVolumes(listOf(8, 1, 3, 2, 5, 7)))
        assertEquals("1-12", formatVolumes((1..12).toList()))
    }

    @Test
    fun `tomes manquants et série complète`() {
        val finished = Series(1, "A", totalVolumes = 5, ownedVolumes = setOf(1, 2, 4))
        assertEquals(listOf(3, 5), finished.missingVolumes)
        assertFalse(finished.isComplete)
        assertTrue(finished.copy(ownedVolumes = (1..5).toSet()).isComplete)

        // Série en cours : seuls les trous avant le dernier tome possédé manquent.
        val ongoing = Series(2, "B", ownedVolumes = setOf(1, 2, 6))
        assertEquals(listOf(3, 4, 5), ongoing.missingVolumes)
        assertFalse(ongoing.isComplete)
        assertEquals(emptyList<Int>(), Series(3, "C").missingVolumes)
    }

    @Test
    fun `dernier tome connu`() {
        assertEquals(0, Series(1, "A").lastKnownVolume)
        assertEquals(6, Series(1, "A", ownedVolumes = setOf(2, 6)).lastKnownVolume)
        assertEquals(10, Series(1, "A", totalVolumes = 10, ownedVolumes = setOf(2, 6)).lastKnownVolume)
        assertEquals(12, Series(1, "A", totalVolumes = 10, ownedVolumes = setOf(12)).lastKnownVolume)
        assertEquals(6, Series(1, "A", ownedVolumes = setOf(2, 6)).lastOwned)
        assertEquals(2, Series(1, "A", ownedVolumes = setOf(2, 6)).ownedCount)
    }
}
