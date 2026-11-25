package com.mangareminder.update

import com.mangareminder.BuildConfig
import com.mangareminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Recherche d'une nouvelle version dans la dernière release GitHub. L'app ne télécharge ni
 * n'installe d'APK (Play Protect bloque les applis qui en installent) : le lien du fichier est
 * ouvert dans le navigateur et c'est l'installeur d'Android qui prend le relais.
 */
class Updater {

    /** Dernière version publiée si elle est plus récente que celle installée, sinon null. */
    suspend fun findUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val release = try {
            parseLatestRelease(readText(LATEST_RELEASE_URL, 1_000_000))
        } catch (e: HttpStatusException) {
            if (e.code == 404) throw UpdateException(R.string.update_err_no_release) else throw e
        }
        val versionJson = release.assets.find { it.name == "version.json" }
            ?: throw UpdateException(R.string.update_err_no_version_json, release.tag)
        val info = parseUpdate(release, readText(versionJson.url, 64 * 1024))
        info.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    private fun readText(url: String, maxBytes: Int): String = open(url).let { connection ->
        try {
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("Réponse trop volumineuse")
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Suit les redirections à la main pour vérifier chaque hôte (HTTPS, GitHub uniquement). */
    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowedUrl(current)) throw UpdateException(R.string.update_err_url_refused)
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "MangaReminder (Android)")
            if (current.startsWith("https://api.github.com/")) {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    current = URL(URL(current), location ?: throw IOException("Redirection sans adresse")).toString()
                }
                else -> {
                    connection.disconnect()
                    throw HttpStatusException(code)
                }
            }
        }
        throw IOException("Trop de redirections")
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val MAX_REDIRECTS = 5
    }
}
