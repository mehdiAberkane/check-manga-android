package com.mangareminder.releases

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Une source de sorties de tomes. D'autres sources (manga-news…) pourront s'ajouter. */
interface ReleaseSource {
    val name: String

    /** @throws IOException si la source ne répond pas. */
    fun search(seriesTitle: String): List<Release>
}

/**
 * API publique Google Books, sans clé : le quota est partagé entre toutes les applis
 * qui l'appellent ainsi, une vérification peut donc échouer et sera retentée plus tard.
 */
object GoogleBooksSource : ReleaseSource {
    override val name = "Google Books"

    override fun search(seriesTitle: String): List<Release> {
        val query = URLEncoder.encode("intitle:\"$seriesTitle\"", "UTF-8")
        val url = URL(
            "https://www.googleapis.com/books/v1/volumes?q=$query" +
                "&langRestrict=fr&printType=books&orderBy=newest&maxResults=40"
        )
        val items = JSONObject(Http.get(url)).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = listOf(info.optString("title"), info.optString("subtitle"))
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            val volume = extractVolume(title, seriesTitle) ?: return@mapNotNull null
            Release(
                volume = volume,
                title = title,
                date = info.optString("publishedDate").takeIf { it.matches(DATE) },
                source = name,
                link = info.optString("infoLink").takeIf { it.startsWith("https://") },
            )
        }
    }

    private val DATE = Regex("\\d{4}(-\\d{2}(-\\d{2})?)?")
}

object Http {
    private const val MAX_BYTES = 2_000_000

    /** @throws IOException en cas d'erreur réseau ou de réponse non 200. */
    fun get(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "MangaReminder (Android)")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("Réponse trop volumineuse")
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}
