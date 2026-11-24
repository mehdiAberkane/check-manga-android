package com.mangareminder.releases

import com.mangareminder.data.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CheckOutcomeTest {

    private val series = Series(1, "One Piece", ownedVolumes = (1..105).toSet(), watched = true)
    private fun release(volume: Int) = Release(volume, "One Piece Tome $volume", null, "test", null)

    @Test
    fun `nouveau tome notifié une seule fois`() {
        val first = checkOutcome(series, listOf(release(105), release(106)), previous = null, now = 10)
        assertEquals(106, first.notify?.volume)
        assertEquals(106, first.stored.notifiedMax)
        assertEquals(10L, first.stored.checkedAt)

        // Vérification suivante, même résultat : pas de seconde notification.
        val second = checkOutcome(series, listOf(release(106)), previous = first.stored, now = 20)
        assertNull(second.notify)
        assertEquals(106, second.stored.notifiedMax)
    }

    @Test
    fun `rien à notifier si le tome est déjà connu`() {
        assertNull(checkOutcome(series, listOf(release(104), release(105)), null, 0).notify)
        // Total indiqué par l'utilisateur au-delà du tome trouvé.
        val withTotal = series.copy(totalVolumes = 110)
        assertNull(checkOutcome(withTotal, listOf(release(107)), null, 0).notify)
    }

    @Test
    fun `tome suivant notifié après un précédent`() {
        val previous = SeriesReleases(0, listOf(release(106)), notifiedMax = 106)
        assertEquals(107, checkOutcome(series, listOf(release(106), release(107)), previous, 1).notify?.volume)
    }

    @Test
    fun `recherche vide, résultats connus gardés`() {
        val previous = SeriesReleases(0, listOf(release(106)), notifiedMax = 106)
        val outcome = checkOutcome(series, emptyList(), previous, 50)
        assertNull(outcome.notify)
        assertEquals(previous.releases, outcome.stored.releases)
        assertEquals(106, outcome.stored.notifiedMax)
        assertEquals(50L, outcome.stored.checkedAt)
        assertEquals(emptyList<Release>(), checkOutcome(series, emptyList(), null, 0).stored.releases)
    }

    private val today = LocalDate.of(2026, 12, 5)
    private fun dated(volume: Int, date: String) = Release(volume, "One Piece Tome $volume", date, "test", null)

    @Test
    fun `tome annoncé puis rappelé le jour de sa sortie, une seule fois`() {
        val announced = checkOutcome(series, listOf(dated(106, "2026-12-05")), null, 1, today.minusDays(10))
        assertEquals(106, announced.notify?.volume)
        assertNull(announced.remind)

        val outDay = checkOutcome(series, listOf(dated(106, "2026-12-05")), announced.stored, 2, today)
        assertNull(outDay.notify)
        assertEquals(106, outDay.remind?.volume)
        assertEquals(106, outDay.stored.remindedMax)

        assertNull(checkOutcome(series, listOf(dated(106, "2026-12-05")), outDay.stored, 3, today).remind)
    }

    @Test
    fun `rappel aussi si la recherche est vide, grâce aux résultats connus`() {
        val previous = SeriesReleases(0, listOf(dated(106, "2026-12-03")), notifiedMax = 106)
        assertEquals(106, checkOutcome(series, emptyList(), previous, 1, today).remind?.volume)
    }

    @Test
    fun `pas de rappel pour un tome possédé, futur, sans date complète ou trop ancien`() {
        val previous = SeriesReleases(0, emptyList(), notifiedMax = 110)
        fun remind(vararg r: Release) = checkOutcome(series, r.toList(), previous, 1, today).remind
        assertNull(remind(dated(105, "2026-12-05")))
        assertNull(remind(dated(106, "2026-12-06")))
        assertNull(remind(dated(106, "2026-12")))
        assertNull(remind(Release(106, "t", null, "test", null)))
        assertNull(remind(dated(106, "2026-11-27")))
        assertEquals(106, remind(dated(106, "2026-11-28"))?.volume)
    }

    @Test
    fun `tome annoncé à l'instant déjà sorti, pas de rappel en plus de l'annonce`() {
        val outcome = checkOutcome(series, listOf(dated(106, "2026-12-04")), null, 1, today)
        assertEquals(106, outcome.notify?.volume)
        assertNull(outcome.remind)
        assertEquals(106, outcome.stored.remindedMax)
    }

    @Test
    fun `tome manquant sous le dernier connu rappelé quand même`() {
        val withTotal = Series(1, "One Piece", ownedVolumes = (1..105).toSet(), totalVolumes = 107, watched = true)
        val previous = SeriesReleases(0, emptyList(), notifiedMax = 107)
        assertEquals(107, checkOutcome(withTotal, listOf(dated(107, "2026-12-05")), previous, 1, today).remind?.volume)
    }
}
