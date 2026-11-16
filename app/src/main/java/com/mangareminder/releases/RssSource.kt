package com.mangareminder.releases

import org.w3c.dom.Element
import java.io.IOException
import java.net.URL
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Une entrée d'un flux RSS ou Atom. */
data class FeedItem(val title: String, val link: String?, val date: String?)

/**
 * Flux RSS choisi dans les réglages (manga-news…). Le flux est téléchargé une seule fois
 * par vérification, puis chaque série y cherche ses tomes par le titre des entrées.
 */
class RssSource(private val feedUrl: String, override val name: String = "Flux RSS") : ReleaseSource {
    private var items: List<FeedItem>? = null

    override fun search(seriesTitle: String): List<Release> {
        val feed = items ?: parseFeed(Http.get(URL(feedUrl))).also { items = it }
        return feed.mapNotNull { item ->
            val volume = extractVolume(item.title, seriesTitle) ?: return@mapNotNull null
            Release(volume = volume, title = item.title, date = item.date, source = name, link = item.link)
        }
    }

    companion object {
        /** Lit un flux RSS 2.0 ou Atom. @throws IOException si ce n'est pas un flux lisible. */
        fun parseFeed(xml: String): List<FeedItem> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                // Pas de DTD ni d'entités externes (XXE). Toutes les implémentations ne
                // connaissent pas ces options : on ignore celles qui sont refusées.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val document = try {
                factory.newDocumentBuilder().parse(xml.byteInputStream())
            } catch (e: Exception) {
                throw IOException("Flux RSS illisible", e)
            }
            val rss = document.getElementsByTagNameNS("*", "item")
            val atom = document.getElementsByTagNameNS("*", "entry")
            val nodes = if (rss.length > 0) rss else atom
            return (0 until nodes.length).mapNotNull { i ->
                val e = nodes.item(i) as Element
                val title = e.child("title")?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val link = e.child("link")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: e.firstChild("link")?.getAttribute("href")?.takeIf { it.isNotEmpty() }
                val date = e.child("pubDate")?.let(::parseRfc822)
                    ?: (e.child("published") ?: e.child("updated"))?.let(::parseIso)
                FeedItem(title, link?.takeIf { it.startsWith("https://") || it.startsWith("http://") }, date)
            }
        }

        private fun Element.firstChild(name: String): Element? {
            val list = getElementsByTagNameNS("*", name)
            return if (list.length > 0) list.item(0) as Element else null
        }

        private fun Element.child(name: String): String? = firstChild(name)?.textContent

        /** "Tue, 05 Nov 2026 10:00:00 +0100" -> "2026-11-05". */
        fun parseRfc822(text: String): String? = runCatching {
            ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH))
                .toLocalDate().toString()
        }.getOrNull()

        /** "2026-11-05T10:00:00+01:00" -> "2026-11-05". */
        fun parseIso(text: String): String? = runCatching {
            LocalDate.parse(text.trim().take(10)).toString()
        }.getOrNull()
    }
}
