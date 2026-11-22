package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeSearchTest {

    private val onePiece = Series(1, "One Piece", ownedVolumes = setOf(1, 2, 45))
    private val naruto = Series(2, "Naruto", totalVolumes = 72, ownedVolumes = setOf(1))
    private val kaiju = Series(3, "Kaiju No. 8", ownedVolumes = setOf(1))
    private val all = listOf(onePiece, naruto, kaiju)

    @Test
    fun `numéro en fin de recherche, sous plusieurs formes`() {
        for (q in listOf("one piece 45", "One Piece T45", "one piece t 45", "one piece tome 45", "one piece vol 45", "one piece #45", "  one piece 45  ")) {
            assertEquals(q, VolumeQuery("one piece", 45), parseVolumeQuery(q))
        }
    }

    @Test
    fun `pas de réponse sans titre, sans numéro ou hors bornes`() {
        assertNull(parseVolumeQuery("45"))
        assertNull(parseVolumeQuery("tome 45"))
        assertNull(parseVolumeQuery("one piece"))
        assertNull(parseVolumeQuery("one piece 0"))
        assertNull(parseVolumeQuery("one piece 1000"))
        assertNull(parseVolumeQuery(""))
    }

    @Test
    fun `tome possédé ou non`() {
        val owned = volumeAnswers(all, "one piece 45").single()
        assertEquals(onePiece, owned.series)
        assertTrue(owned.owned)
        val missing = volumeAnswers(all, "ONE PIÈCE 46").single()
        assertFalse(missing.owned)
        assertFalse(missing.beyondTotal)
    }

    @Test
    fun `tome au-delà du total connu`() {
        val answer = volumeAnswers(all, "naruto 80").single()
        assertFalse(answer.owned)
        assertTrue(answer.beyondTotal)
    }

    @Test
    fun `titre qui finit par un nombre, pas de réponse inventée`() {
        assertEquals(emptyList<VolumeAnswer>(), volumeAnswers(all, "kaiju no 8"))
        // Mais « kaiju no 8 3 » demande bien le tome 3 de Kaiju No. 8.
        assertEquals(listOf(kaiju to 3), volumeAnswers(all, "kaiju no 8 3").map { it.series to it.volume })
    }

    @Test
    fun `la liste montre aussi la série de la réponse`() {
        assertEquals(listOf(onePiece), searchResults(all, "one piece 45"))
        assertEquals(listOf(kaiju), searchResults(all, "kaiju no 8"))
        assertEquals(all, searchResults(all, ""))
        assertEquals(emptyList<Series>(), searchResults(all, "bleach 3"))
    }
}
