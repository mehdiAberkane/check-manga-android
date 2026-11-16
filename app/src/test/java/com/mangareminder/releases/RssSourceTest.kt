package com.mangareminder.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class RssSourceTest {

    @Test
    fun `flux RSS 2_0`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>Manga News</title>
              <item>
                <title>One Piece Vol.106</title>
                <link>https://www.manga-news.com/index.php/manga/One-Piece/vol-106</link>
                <pubDate>Thu, 05 Nov 2026 10:00:00 +0100</pubDate>
              </item>
              <item><title>Dandadan T22 &amp; coffret</title><link>https://example.com/d</link></item>
              <item><title></title></item>
            </channel></rss>
        """.trimIndent()
        val items = RssSource.parseFeed(xml)
        assertEquals(2, items.size)
        assertEquals("One Piece Vol.106", items[0].title)
        assertEquals("https://www.manga-news.com/index.php/manga/One-Piece/vol-106", items[0].link)
        assertEquals("2026-11-05", items[0].date)
        assertEquals("Dandadan T22 & coffret", items[1].title)
        assertNull(items[1].date)
        assertEquals(106, extractVolume(items[0].title, "One Piece"))
    }

    @Test
    fun `flux Atom`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Frieren Tome 14</title>
                <link href="https://example.com/frieren-14"/>
                <updated>2026-10-30T08:00:00+01:00</updated>
              </entry>
            </feed>
        """.trimIndent()
        val items = RssSource.parseFeed(xml)
        assertEquals(listOf(FeedItem("Frieren Tome 14", "https://example.com/frieren-14", "2026-10-30")), items)
    }

    @Test(expected = IOException::class)
    fun `contenu non XML`() {
        RssSource.parseFeed("<html><body>Erreur")
    }

    @Test
    fun `entités externes refusées`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>&xxe;</title></item></channel></rss>
        """.trimIndent()
        val result = runCatching { RssSource.parseFeed(xml) }
        // Soit le DOCTYPE est refusé, soit l'entité n'est pas résolue : jamais le contenu du fichier.
        result.getOrNull()?.forEach { assert(!it.title.contains("root:")) }
    }
}
