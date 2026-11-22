package com.mangareminder.releases

import com.mangareminder.data.Series
import com.mangareminder.data.SeriesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTest {

    private val gb = "https://books.google.com/books/content?id=abc&printsec=frontcover&img=1&zoom=1&source=gbs_api"

    @Test
    fun `miniature Google Books passée en https sans page cornée`() {
        assertEquals(
            gb,
            normalizeCoverUrl("http://books.google.com/books/content?id=abc&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api"),
        )
        assertEquals(gb, normalizeCoverUrl(" $gb "))
    }

    @Test
    fun `seuls les serveurs d'images de Google Books sont acceptés`() {
        assertTrue(isSafeCoverUrl(gb))
        assertTrue(isSafeCoverUrl("https://books.googleusercontent.com/content?id=x"))
        assertFalse(isSafeCoverUrl("https://evil.example/cover.jpg"))
        assertFalse(isSafeCoverUrl("https://books.google.com.evil.example/x"))
        assertFalse(isSafeCoverUrl("https://user@books.google.com/x"))
        assertFalse(isSafeCoverUrl("https://books.google.com:8443/x"))
        assertFalse(isSafeCoverUrl("http://books.google.com/x"))
        assertFalse(isSafeCoverUrl("https://books.google.com/" + "a".repeat(2_000)))
        assertNull(normalizeCoverUrl("javascript:alert(1)"))
        assertNull(normalizeCoverUrl(""))
        assertNull(normalizeCoverUrl(null))
    }

    @Test
    fun `nom de cache stable et sans l'adresse`() {
        val name = coverCacheName(gb)
        assertEquals(name, coverCacheName(gb))
        assertNotEquals(name, coverCacheName("$gb&x"))
        assertTrue(name.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `couverture automatique du plus petit tome et choix sans doublon`() {
        val releases = listOf(
            Release(5, "T5", null, "s", null, cover = "$gb&v=5"),
            Release(2, "T2", null, "s", null, cover = "$gb&v=2"),
            Release(1, "T1", null, "s", null),
            Release(3, "T3", null, "s", null, cover = "$gb&v=2"),
        )
        assertEquals("$gb&v=2", SeriesReleases(0, releases, 0).autoCover())
        assertNull(SeriesReleases(0, listOf(Release(1, "T1", null, "s", null)), 0).autoCover())
        assertNull((null as SeriesReleases?).autoCover())
        assertEquals(listOf(2, 5), coverChoices(releases).map { it.volume })
    }

    @Test
    fun `réponse Google Books lue avec sa couverture`() {
        val json = """
            {"items": [
              {"volumeInfo": {"title": "Naruto Tome 1", "publishedDate": "2002-03-01",
                "imageLinks": {"thumbnail": "http://books.google.com/books/content?id=abc&printsec=frontcover&img=1&zoom=1&edge=curl&source=gbs_api"}}},
              {"volumeInfo": {"title": "Naruto Tome 2", "imageLinks": {"thumbnail": "https://evil.example/x.jpg"}}},
              {"volumeInfo": {"title": "Naruto Tome 3"}},
              {"volumeInfo": {"title": "Naruto", "seriesInfo": {"bookDisplayNumber": "4"}}},
              {"volumeInfo": {"title": "Boruto - Naruto Next Generations tome 9"}}
            ]}
        """.trimIndent()
        val releases = GoogleBooksSource.parse(json, "Naruto").associateBy { it.volume }
        assertEquals(gb, releases.getValue(1).cover)
        assertNull(releases.getValue(2).cover)
        assertNull(releases.getValue(3).cover)
        // Numéro donné par seriesInfo ; série dérivée écartée.
        assertEquals(setOf(1, 2, 3, 4), releases.keys)
    }

    @Test
    fun `couverture conservée dans le cache des sorties, refusée si invalide`() {
        val data = mapOf(1L to SeriesReleases(0, listOf(Release(1, "T1", null, "s", null, cover = gb)), 0))
        assertEquals(gb, releaseCacheFromJson(releaseCacheToJson(data)).getValue(1).releases.single().cover)
        val bad = """{"1": {"checkedAt": 0, "releases": [{"volume": 1, "title": "t", "source": "s", "cover": "https://evil.example/x"}]}}"""
        assertNull(releaseCacheFromJson(bad).getValue(1).releases.single().cover)
    }

    @Test
    fun `couverture d'une série dans series json, facultative et ignorée si invalide`() {
        val series = listOf(Series(1, "Naruto", cover = gb), Series(2, "One Piece"))
        assertEquals(series, SeriesRepository.fromJson(SeriesRepository.toJson(series)))
        val bad = """[{"id": 1, "title": "Naruto", "owned": [], "cover": "https://evil.example/x.jpg"}]"""
        assertNull(SeriesRepository.fromJson(bad).single().cover)
    }
}
