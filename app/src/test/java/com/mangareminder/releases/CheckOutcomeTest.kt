package com.mangareminder.releases

import com.mangareminder.data.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
