package com.mangareminder.releases

import com.mangareminder.data.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseCalendarTest {

    private val today = LocalDate.of(2026, 12, 5)
    private fun release(volume: Int, date: String?) = Release(volume, "Tome $volume", date, "test", null)
    private fun ReleaseCalendar.upcomingKeys() = upcoming.map { it.series.title to it.release.volume }
    private fun ReleaseCalendar.publishedKeys() = published.map { it.series.title to it.release.volume }

    @Test
    fun `à paraître par date, puis déjà parus du plus récent au plus ancien`() {
        val series = listOf(
            Series(1, "One Piece", ownedVolumes = (1..105).toSet(), watched = true),
            Series(2, "Frieren", ownedVolumes = (1..12).toSet(), watched = true),
        )
        val releases = mapOf(
            1L to SeriesReleases(0, listOf(release(108, "2027-03-01"), release(107, "2026-12-20"), release(106, "2026-11-02")), 0),
            2L to SeriesReleases(0, listOf(release(14, "2026-12-10"), release(13, "2026-12-01"), release(15, null)), 0),
        )
        val calendar = releaseCalendar(series, releases, today)
        assertEquals(listOf("Frieren" to 14, "One Piece" to 107, "One Piece" to 108), calendar.upcomingKeys())
        assertEquals(listOf("Frieren" to 13, "One Piece" to 106, "Frieren" to 15), calendar.publishedKeys())
    }

    @Test
    fun `séries non surveillées, tomes possédés et anciens trous écartés`() {
        val series = listOf(
            Series(1, "One Piece", ownedVolumes = (1..105).toSet() - 50, watched = true),
            Series(2, "Bleach", ownedVolumes = setOf(1), watched = false),
        )
        val releases = mapOf(
            1L to SeriesReleases(0, listOf(release(50, "2010-01-01"), release(105, "2026-12-20")), 0),
            2L to SeriesReleases(0, listOf(release(2, "2026-12-20")), 0),
        )
        assertTrue(releaseCalendar(series, releases, today).isEmpty)
    }

    @Test
    fun `date partielle à paraître seulement si la période n'est pas commencée`() {
        assertTrue(release(1, "2027").isAfter(today))
        assertTrue(release(1, "2027-01").isAfter(today))
        assertFalse(release(1, "2026-12").isAfter(today))
        assertFalse(release(1, "2026").isAfter(today))
        assertFalse(release(1, "2026-12-05").isAfter(today))
        assertTrue(release(1, "2026-12-06").isAfter(today))
        assertFalse(release(1, null).isAfter(today))
    }

    @Test
    fun `même tome en double gardé une fois`() {
        val series = listOf(Series(1, "One Piece", ownedVolumes = setOf(1), watched = true))
        val releases = mapOf(1L to SeriesReleases(0, listOf(release(2, "2027-01-01"), release(2, "2027-01")), 0))
        assertEquals(1, releaseCalendar(series, releases, today).upcoming.size)
    }
}
