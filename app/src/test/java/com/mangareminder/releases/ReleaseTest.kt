package com.mangareminder.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseTest {

    @Test
    fun `numéro de tome dans les formats courants`() {
        assertEquals(105, extractVolume("One Piece - Édition originale - Tome 105", "One Piece"))
        assertEquals(105, extractVolume("One Piece Tome 105", "one piece"))
        assertEquals(12, extractVolume("L'Attaque des Titans T12", "L'Attaque des Titans"))
        assertEquals(3, extractVolume("Frieren Vol. 3", "Frieren"))
        assertEquals(7, extractVolume("Blue Lock - T.7", "Blue Lock"))
        assertEquals(22, extractVolume("Dandadan 22", "Dandadan"))
        assertEquals(4, extractVolume("Spy x Family n°4", "Spy x Family"))
    }

    @Test
    fun `accents et casse ignorés`() {
        assertEquals(2, extractVolume("DÉMON SLAYER tome 2", "Demon Slayer"))
    }

    @Test
    fun `autre série ou sans numéro`() {
        assertNull(extractVolume("Naruto Tome 3", "One Piece"))
        assertNull(extractVolume("One Piece", "One Piece"))
        // Le titre de la série doit être un mot entier : "Pieces" n'est pas "Piece".
        assertNull(extractVolume("One Pieces Tome 3", "One Piece"))
    }

    @Test
    fun `produits dérivés exclus`() {
        assertNull(extractVolume("One Piece - Coffret tome 4", "One Piece"))
        assertNull(extractVolume("One Piece Roman tome 2", "One Piece"))
        assertNull(extractVolume("One Piece Film Red tome 1", "One Piece"))
    }

    @Test
    fun `format des dates`() {
        assertEquals("05/12/2026", formatReleaseDate("2026-12-05"))
        assertEquals("12/2026", formatReleaseDate("2026-12"))
        assertEquals("2026", formatReleaseDate("2026"))
        assertNull(formatReleaseDate(null))
    }

    @Test
    fun `tome à paraître`() {
        val today = LocalDate.of(2026, 11, 16)
        val upcoming = Release(106, "One Piece 106", "2026-12-05", "test", null)
        val past = Release(105, "One Piece 105", "2026-07-02", "test", null)
        assertTrue(upcoming.isUpcoming(today))
        assertFalse(past.isUpcoming(today))
        assertEquals("Tome 106 · à paraître le 05/12/2026", upcoming.describe(today))
        assertEquals("Tome 105 · paru le 02/07/2026", past.describe(today))
    }

    @Test
    fun `fusion garde la date la plus précise et trie du plus récent`() {
        val merged = mergeReleases(
            listOf(
                Release(10, "a", "2026", "test", null),
                Release(11, "b", "2026-03-01", "test", null),
                Release(10, "c", "2026-01-15", "test", null),
            )
        )
        assertEquals(listOf(11, 10), merged.map { it.volume })
        assertEquals("2026-01-15", merged[1].date)
    }

    @Test
    fun `nouveauté seulement au-delà du dernier tome connu`() {
        val sr = SeriesReleases(0, listOf(Release(12, "t", null, "test", null)), 0)
        assertEquals(12, sr.newRelease(11)?.volume)
        assertNull(sr.newRelease(12))
    }
}
