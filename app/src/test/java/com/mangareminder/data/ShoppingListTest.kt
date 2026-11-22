package com.mangareminder.data

import com.mangareminder.releases.Release
import com.mangareminder.releases.SeriesReleases
import com.mangareminder.releases.formatReleaseDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShoppingListTest {

    private val today = LocalDate.of(2026, 11, 22)
    private fun release(volume: Int, date: String? = null) = Release(volume, "T$volume", date, "test", null)

    private val onePiece = Series(1, "One Piece", totalVolumes = 48, ownedVolumes = (1..48).toSet() - setOf(45, 47, 48))
    private val frieren = Series(2, "Frieren", ownedVolumes = (1..13).toSet(), watched = true)
    private val complete = Series(3, "Akira", totalVolumes = 6, ownedVolumes = (1..6).toSet())
    private val notWatched = Series(4, "Dandadan", ownedVolumes = setOf(1, 2))

    private val releases = mapOf(
        2L to SeriesReleases(0, listOf(release(14, "2026-12-05"), release(13), release(12)), 0),
        4L to SeriesReleases(0, listOf(release(3, "2026-01-01")), 0),
    )

    @Test
    fun `manquants et nouveautés, séries complètes et non surveillées sans nouveauté`() {
        val list = shoppingList(listOf(complete, notWatched, frieren, onePiece), releases)
        assertEquals(listOf("Frieren", "One Piece"), list.map { it.series.title })
        assertEquals(listOf(45, 47, 48), list.last().missing)
        assertEquals(listOf(14), list.first().newReleases.map { it.volume })
    }

    @Test
    fun `tomes disponibles en magasin, sans les tomes à paraître`() {
        val list = shoppingList(listOf(frieren, onePiece), releases).associateBy { it.series.title }
        assertEquals(0, list.getValue("Frieren").availableCount(today))
        assertEquals(3, list.getValue("One Piece").availableCount(today))
    }

    @Test
    fun `texte partagé au format du ticket`() {
        val text = shoppingShareText(shoppingList(listOf(onePiece, frieren), releases)) { r ->
            r.date?.let { "${r.volume} (paraît le ${formatReleaseDate(it)})" } ?: "${r.volume}"
        }
        assertEquals("One Piece : 45, 47-48\nFrieren : 14 (paraît le 05/12/2026)", text)
    }

    @Test
    fun `rien à acheter`() {
        assertEquals(emptyList<ShoppingEntry>(), shoppingList(listOf(complete), emptyMap()))
        assertEquals("", shoppingShareText(emptyList()) { "" })
    }
}
