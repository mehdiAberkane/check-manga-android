package com.mangareminder.releases

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCacheTest {

    private fun release(
        volume: Int = 12,
        title: String = "One Piece Tome 12",
        date: String? = "2026-12-05",
        link: String? = "https://books.google.fr/books?id=x",
    ) = Release(volume, title, date, "Google Books", link)

    @Test
    fun `aller-retour sans perte`() {
        val data = mapOf(
            42L to SeriesReleases(1_700_000_000_000, listOf(release(12), release(11, date = null, link = null)), 11, remindedMax = 10),
        )
        assertEquals(data, releaseCacheFromJson(releaseCacheToJson(data)))
    }

    @Test
    fun `tome hors bornes, titre ou source vides ignorés`() {
        assertNull(release(volume = 0).sanitized())
        assertNull(release(volume = 1000).sanitized())
        assertNull(release(title = "   ").sanitized())
        assertNull(Release(1, "t", null, "", null).sanitized())
    }

    @Test
    fun `titre tronqué, date et lien invalides retirés`() {
        val r = release(title = "x".repeat(5_000), date = "demain", link = "http://books.google.fr").sanitized()!!
        assertEquals(MAX_RELEASE_TITLE_LENGTH, r.title.length)
        assertNull(r.date)
        assertNull(r.link)
    }

    @Test
    fun `liens acceptés en HTTPS uniquement`() {
        assertTrue(isSafeLink("https://books.google.fr/books?id=x"))
        assertFalse(isSafeLink("http://books.google.fr"))
        assertFalse(isSafeLink("javascript:alert(1)"))
        assertFalse(isSafeLink("intent://scan#Intent;end"))
        assertFalse(isSafeLink("https://a.fr/ b"))
        assertFalse(isSafeLink("https://" + "a".repeat(3_000)))
    }

    @Test
    fun `entrée invalide ignorée sans perdre le reste du cache`() {
        val json = """
            {
              "abc": {"checkedAt": 1, "releases": []},
              "7": "pas un objet",
              "8": {"checkedAt": 1},
              "9": {"checkedAt": -5, "notifiedMax": 5000, "releases": [
                {"volume": "douze", "title": "t", "source": "s"},
                {"volume": 3, "title": "Tome 3", "source": "s", "link": "javascript:alert(1)"},
                "pas un objet",
                {"volume": 4, "title": "Tome 4", "source": "s", "date": "2026-01-01"}
              ]}
            }
        """.trimIndent()
        val data = releaseCacheFromJson(json)
        assertEquals(setOf(9L), data.keys)
        val sr = data.getValue(9)
        assertEquals(0L, sr.checkedAt)
        assertEquals(999, sr.notifiedMax)
        assertEquals(listOf(4, 3), sr.releases.map { it.volume })
        assertNull(sr.releases.last().link)
    }

    @Test
    fun `nombre de sorties par série plafonné`() {
        val many = (1..500).map { release(volume = it) }
        val sr = SeriesReleases(0, many, 0).sanitized()
        assertEquals(MAX_RELEASES_PER_SERIES, sr.releases.size)
        assertEquals(500, sr.releases.first().volume)
    }

    @Test
    fun `fichier qui n'est pas un objet JSON refusé`() {
        assertThrows(JSONException::class.java) { releaseCacheFromJson("[]") }
        assertThrows(JSONException::class.java) { releaseCacheFromJson("<html>") }
    }

    @Test
    fun `ancien cache sans remindedMax relu, valeur bornée`() {
        val old = releaseCacheFromJson("""{"1":{"checkedAt":5,"notifiedMax":3,"releases":[]}}""")
        assertEquals(0, old.getValue(1L).remindedMax)
        val tooHigh = releaseCacheFromJson("""{"1":{"checkedAt":5,"remindedMax":5000,"releases":[]}}""")
        assertEquals(999, tooHigh.getValue(1L).remindedMax)
    }
}
