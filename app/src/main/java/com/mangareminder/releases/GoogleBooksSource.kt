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
/** Seul hôte de l'API Google Books (recherche des sorties, ISBN). */
val GOOGLE_BOOKS_HOSTS = setOf("www.googleapis.com")

object GoogleBooksSource : ReleaseSource {
    override val name = "Google Books"

    override fun search(seriesTitle: String): List<Release> {
        val query = URLEncoder.encode("intitle:\"$seriesTitle\"", "UTF-8")
        val url = URL(
            "https://www.googleapis.com/books/v1/volumes?q=$query" +
                "&langRestrict=fr&printType=books&orderBy=newest&maxResults=40"
        )
        return parse(Http.get(url, GOOGLE_BOOKS_HOSTS), seriesTitle)
    }

    /** Tomes de la série dans une réponse de l'API (sans réseau : testé en unitaire). */
    fun parse(json: String, seriesTitle: String): List<Release> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = listOf(info.optString("title"), info.optString("subtitle"))
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            // seriesInfo.bookDisplayNumber : numéro donné à part, quand Google Books le connaît.
            val displayNumber = info.optJSONObject("seriesInfo")?.optString("bookDisplayNumber")
            val volume = extractVolume(title, seriesTitle, displayNumber) ?: return@mapNotNull null
            Release(
                volume = volume,
                title = title,
                date = info.optString("publishedDate").takeIf { it.matches(RELEASE_DATE) },
                source = name,
                link = info.optString("infoLink").takeIf { isSafeLink(it) },
                cover = normalizeCoverUrl(info.optJSONObject("imageLinks")?.optString("thumbnail")),
            ).sanitized()
        }
    }

}

object Http {
    private const val MAX_BYTES = 2_000_000
    private const val MAX_REDIRECTS = 5

    /**
     * HTTPS uniquement, vers les [hosts] donnés : une adresse ou une redirection vers un autre
     * schéma ou un autre hôte est refusée (même règle que le module de mise à jour).
     * @throws IOException en cas d'erreur réseau, de réponse non 200 ou d'adresse refusée.
     */
    fun get(url: URL, hosts: Set<String>): String = getBytes(url, MAX_BYTES, hosts).toString(Charsets.UTF_8)

    /**
     * Comme [get], en octets, avec une taille maximale donnée (images).
     * @throws IOException en cas d'erreur réseau, de réponse non 200, de réponse trop grosse ou d'adresse refusée.
     */
    fun getBytes(url: URL, maxBytes: Int, hosts: Set<String>): ByteArray {
        var current = requireAllowed(url, hosts)
        repeat(MAX_REDIRECTS + 1) {
            val connection = current.openConnection() as HttpURLConnection
            try {
                // Redirections suivies à la main pour vérifier le schéma de chacune.
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "MangaReminder (Android)")
                connection.setRequestProperty("Accept", "application/json, image/*")
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return connection.inputStream.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            if (out.size() > maxBytes) throw IOException("Réponse trop volumineuse")
                        }
                        out.toByteArray()
                    }
                    301, 302, 303, 307, 308 ->
                        current = redirectTarget(current, connection.getHeaderField("Location"), hosts)
                    else -> throw HttpStatusException(code)
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Trop de redirections")
    }

    /** Adresse de la redirection, résolue par rapport à la page courante ; HTTPS et hôte autorisé obligatoires. */
    fun redirectTarget(current: URL, location: String?, hosts: Set<String>): URL {
        if (location.isNullOrBlank()) throw IOException("Redirection sans adresse")
        return requireAllowed(URL(current, location), hosts)
    }

    /** HTTPS, hôte de la liste, sans identifiants ni port exotique. */
    fun requireAllowed(url: URL, hosts: Set<String>): URL {
        requireHttps(url)
        if (url.host.lowercase() !in hosts || url.userInfo != null || url.port !in setOf(-1, 443)) {
            throw IOException("Hôte non autorisé : ${url.host}")
        }
        return url
    }

    fun requireHttps(url: URL): URL {
        if (!url.protocol.equals("https", ignoreCase = true)) throw IOException("Adresse non HTTPS refusée : ${url.protocol}")
        return url
    }
}
