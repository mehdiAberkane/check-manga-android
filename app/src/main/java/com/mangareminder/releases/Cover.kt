package com.mangareminder.releases

import com.mangareminder.data.Series
import java.net.URI
import java.security.MessageDigest

// --- Couvertures, sans dépendance Android (testé en unitaire). ---
// Les adresses viennent de Google Books, puis de series.json / releases.json (qui peuvent
// venir d'un import) : elles sont revalidées partout où on les lit.

/** Serveurs d'images de Google Books. Aucun autre hôte n'est contacté pour une couverture. */
val COVER_HOSTS = setOf("books.google.com", "books.googleusercontent.com")

private const val MAX_COVER_URL_LENGTH = 1_000

/** Une miniature Google Books fait une dizaine de Ko. */
const val MAX_COVER_BYTES = 500_000

/**
 * Adresse de miniature Google Books utilisable, ou null.
 * Google Books la donne souvent en http:// et avec un effet « page cornée » (edge=curl) :
 * on passe en https et on retire l'effet.
 */
fun normalizeCoverUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val url = raw.trim()
        .replaceFirst(Regex("^http://"), "https://")
        .replace("&edge=curl", "")
    return url.takeIf { isSafeCoverUrl(it) }
}

/** HTTPS, hôte Google Books, sans identifiants ni port exotique, longueur raisonnable. */
fun isSafeCoverUrl(url: String): Boolean {
    if (url.length > MAX_COVER_URL_LENGTH || url.any { it.isWhitespace() }) return false
    return runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host in COVER_HOSTS && uri.userInfo == null && uri.port in setOf(-1, 443)
    }.getOrDefault(false)
}

/** Nom du fichier de cache d'une couverture : empreinte de l'adresse, jamais l'adresse elle-même. */
fun coverCacheName(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

/** Couverture automatique d'une série : celle du plus petit tome trouvé qui en a une (idéalement le tome 1). */
fun SeriesReleases?.autoCover(): String? =
    this?.releases?.filter { it.cover != null }?.minByOrNull { it.volume }?.cover

/** Couvertures proposées au choix, une par tome, dans l'ordre des tomes. */
fun coverChoices(releases: List<Release>): List<Release> =
    releases.filter { it.cover != null }.distinctBy { it.cover }.sortedBy { it.volume }

/**
 * Fichiers du cache des couvertures à supprimer : ceux qui ne correspondent à aucune couverture
 * encore utile (série ou sortie connue), et les fichiers temporaires laissés par un téléchargement
 * interrompu.
 */
fun coverFilesToDelete(fileNames: Collection<String>, usedUrls: Collection<String>): List<String> {
    val keep = usedUrls.map { coverCacheName(it) }.toSet()
    return fileNames.filter { it !in keep }
}

/** Adresses des couvertures encore utilisées : choisies pour une série, ou trouvées par la surveillance. */
fun usedCoverUrls(series: List<Series>, releases: Map<Long, SeriesReleases>): Set<String> =
    series.mapNotNull { it.cover }.toSet() + releases.values.flatMap { sr -> sr.releases.mapNotNull { it.cover } }

