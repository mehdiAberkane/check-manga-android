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
                date = info.optString("publishedDate").takeIf { it.matches(RELEASE_DATE) },
                source = name,
                link = info.optString("infoLink").takeIf { isSafeLink(it) },
            ).sanitized()
        }
    }

}

object Http {
    private const val MAX_BYTES = 2_000_000
    private const val MAX_REDIRECTS = 5

    /**
     * HTTPS uniquement : une adresse ou une redirection vers un autre schéma est refusée.
     * @throws IOException en cas d'erreur réseau, de réponse non 200 ou d'adresse non HTTPS.
     */
    fun get(url: URL): String {
        var current = requireHttps(url)
        repeat(MAX_REDIRECTS + 1) {
            val connection = current.openConnection() as HttpURLConnection
            try {
                // Redirections suivies à la main pour vérifier le schéma de chacune.
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "MangaReminder (Android)")
                connection.setRequestProperty("Accept", "application/json")
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return connection.inputStream.use { input ->
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
                    301, 302, 303, 307, 308 -> current = redirectTarget(current, connection.getHeaderField("Location"))
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Trop de redirections")
    }

    /** Adresse de la redirection, résolue par rapport à la page courante ; HTTPS obligatoire. */
    fun redirectTarget(current: URL, location: String?): URL {
        if (location.isNullOrBlank()) throw IOException("Redirection sans adresse")
        return requireHttps(URL(current, location))
    }

    fun requireHttps(url: URL): URL {
        if (!url.protocol.equals("https", ignoreCase = true)) throw IOException("Adresse non HTTPS refusée : ${url.protocol}")
        return url
    }
}
