package com.mangareminder.widget

import com.mangareminder.data.Series
import com.mangareminder.releases.Release
import com.mangareminder.releases.SeriesReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetSummaryTest {

    private val today = LocalDate.of(2026, 11, 22)

    private fun release(volume: Int, date: String? = null) = Release(volume, "Tome $volume", date, "test", null)

    @Test
    fun `tomes à acheter, manquants plus nouveautés déjà parues`() {
        val finished = Series(1, "A", totalVolumes = 5, ownedVolumes = setOf(1, 2, 4)) // manquent 3 et 5
        val ongoing = Series(2, "B", ownedVolumes = setOf(1, 2, 3), watched = true)
        val releases = mapOf(
            2L to SeriesReleases(0, listOf(release(3), release(4, "2026-10-01"), release(5, "2026-12-05")), 0),
        )
        val summary = widgetSummary(listOf(finished, ongoing), releases, today)
        // 2 manquants + tome 4 de B déjà paru (le 5 est à paraître, le 3 est possédé).
        assertEquals(3, summary.toBuy)
        assertEquals(listOf("B" to 4), summary.nextReleases.map { it.first to it.second.volume })
    }

    @Test
    fun `séries non surveillées, pas de nouveautés comptées`() {
        val s = Series(1, "A", ownedVolumes = setOf(1), watched = false)
        val releases = mapOf(1L to SeriesReleases(0, listOf(release(2, "2026-01-01")), 0))
        val summary = widgetSummary(listOf(s), releases, today)
        assertEquals(0, summary.toBuy)
        assertEquals(emptyList<Pair<String, Release>>(), summary.nextReleases)
    }

    @Test
    fun `prochaines sorties datées d'abord, trois au plus`() {
        val series = listOf("D", "C", "B", "A").mapIndexed { i, t -> Series(i.toLong(), t, ownedVolumes = setOf(1), watched = true) }
        val releases = mapOf(
            0L to SeriesReleases(0, listOf(release(2)), 0),
            1L to SeriesReleases(0, listOf(release(2, "2027-01-10")), 0),
            2L to SeriesReleases(0, listOf(release(2, "2026-12-01"), release(3, "2026-12-20")), 0),
            3L to SeriesReleases(0, listOf(release(2, "2027-02")), 0),
        )
        val summary = widgetSummary(series, releases, today)
        assertEquals(listOf("B", "C", "A"), summary.nextReleases.map { it.first })
        assertEquals(2, summary.nextReleases.first().second.volume)
    }

    @Test
    fun `collection vide`() {
        assertEquals(WidgetSummary(0, emptyList()), widgetSummary(emptyList(), emptyMap(), today))
    }

    @Test
    fun `widget redessiné seulement quand son contenu change`() {
        val gate = WidgetRedrawGate()
        val a = WidgetSummary(3, emptyList())
        assertTrue(gate.shouldRedraw(a))
        assertFalse(gate.shouldRedraw(a))
        assertFalse(gate.shouldRedraw(WidgetSummary(3, emptyList())))
        assertTrue(gate.shouldRedraw(WidgetSummary(2, emptyList())))
    }
}
