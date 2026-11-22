package com.mangareminder.releases

import com.mangareminder.R
import com.mangareminder.UiMessage
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
    fun `séries dérivées écartées (audit, constat 1)`() {
        assertNull(extractVolume("Boruto - Naruto Next Generations tome 5", "Naruto"))
        assertNull(extractVolume("Dragon Ball Super tome 20", "Dragon Ball"))
        assertNull(extractVolume("L'Attaque des Titans Before the Fall T17", "L'Attaque des Titans"))
        assertNull(extractVolume("Naruto Shippuden Vol. 2", "Naruto"))
        // La série dérivée suivie pour elle-même reste reconnue, avec son titre complet.
        assertEquals(5, extractVolume("Boruto - Naruto Next Generations tome 5", "Boruto - Naruto Next Generations"))
        assertEquals(20, extractVolume("Dragon Ball Super tome 20", "Dragon Ball Super"))
        // Limite assumée : un titre de série abrégé ne couvre pas le sous-titre placé avant le numéro.
        assertNull(extractVolume("Boruto - Naruto Next Generations tome 5", "Boruto"))
    }

    @Test
    fun `éditions à numérotation différente écartées`() {
        assertNull(extractVolume("Dragon Ball Perfect Edition - Tome 1", "Dragon Ball"))
        assertNull(extractVolume("Fullmetal Alchemist - Édition double - Tome 3", "Fullmetal Alchemist"))
        assertNull(extractVolume("Naruto - Édition Hokage - Tome 1", "Naruto"))
        assertNull(extractVolume("One Piece - Coffret tome 4", "One Piece"))
    }

    @Test
    fun `mots neutres et sous-titre après le numéro acceptés`() {
        assertEquals(42, extractVolume("Dragon Ball (Édition originale) - Tome 42", "Dragon Ball"))
        assertEquals(101, extractVolume("One Piece - Édition originale - Tome 101 - Le dernier acte", "One Piece"))
        assertEquals(3, extractVolume("Kaiju No. 8 Tome 3", "Kaiju No. 8"))
        assertEquals(12, extractVolume("Frieren vol12", "Frieren"))
    }

    @Test
    fun `numéro donné à part par Google Books`() {
        assertEquals(105, extractVolume("One Piece", "One Piece", displayNumber = "105"))
        assertEquals(105, extractVolume("One Piece - Édition originale", "One Piece", displayNumber = " 105 "))
        // Le numéro du titre l'emporte ; un numéro hors bornes ou non numérique est ignoré.
        assertEquals(7, extractVolume("One Piece Tome 7", "One Piece", displayNumber = "8"))
        assertNull(extractVolume("One Piece", "One Piece", displayNumber = "1000"))
        assertNull(extractVolume("One Piece", "One Piece", displayNumber = "Tome 2"))
        // Une série dérivée reste écartée même avec un numéro à part.
        assertNull(extractVolume("Dragon Ball Super", "Dragon Ball", displayNumber = "20"))
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
        assertEquals(UiMessage(R.string.release_upcoming, 106, "05/12/2026"), upcoming.describe(today))
        assertEquals(UiMessage(R.string.release_published, 105, "02/07/2026"), past.describe(today))
        assertEquals(UiMessage(R.string.release_volume, 7), Release(7, "t", null, "test", null).describe(today))
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
