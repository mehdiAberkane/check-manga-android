package com.mangareminder.releases

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// --- Format de releases.json, sans dépendance Android (testé en unitaire). ---
// Les données viennent de Google Books puis d'un fichier : elles sont bornées à l'écriture
// comme à la relecture.

/** Au-delà, le fichier est ignoré (cache reconstruit) : une collection réelle en est très loin. */
const val MAX_RELEASE_CACHE_BYTES = 2_000_000L

const val MAX_RELEASES_PER_SERIES = 40
const val MAX_RELEASE_TITLE_LENGTH = 300
private const val MAX_SOURCE_LENGTH = 50
private const val MAX_LINK_LENGTH = 2_000
private const val MAX_RELEASE_VOLUME = 999

/** "2026-12-05", "2026-12" ou "2026". */
val RELEASE_DATE = Regex("\\d{4}(-\\d{2}(-\\d{2})?)?")

/**
 * Sortie ramenée dans les bornes, ou null si elle est inutilisable (tome hors bornes, titre
 * ou source vides). Date invalide et lien non HTTPS sont simplement retirés.
 */
fun Release.sanitized(): Release? {
    if (volume !in 1..MAX_RELEASE_VOLUME) return null
    val cleanTitle = title.trim().take(MAX_RELEASE_TITLE_LENGTH)
    val cleanSource = source.trim().take(MAX_SOURCE_LENGTH)
    if (cleanTitle.isEmpty() || cleanSource.isEmpty()) return null
    return copy(
        title = cleanTitle,
        source = cleanSource,
        date = date?.takeIf { it.matches(RELEASE_DATE) },
        link = link?.takeIf { isSafeLink(it) },
    )
}

/** Lien ouvert depuis la fiche série : HTTPS uniquement, longueur raisonnable, sans espace. */
fun isSafeLink(link: String): Boolean =
    link.length <= MAX_LINK_LENGTH && link.startsWith("https://") && link.none { it.isWhitespace() }

/** Bornes communes à l'écriture et à la relecture. */
fun SeriesReleases.sanitized(): SeriesReleases = SeriesReleases(
    checkedAt = checkedAt.coerceAtLeast(0),
    releases = mergeReleases(releases.mapNotNull { it.sanitized() }).take(MAX_RELEASES_PER_SERIES),
    notifiedMax = notifiedMax.coerceIn(0, MAX_RELEASE_VOLUME),
)

fun releaseCacheToJson(data: Map<Long, SeriesReleases>): String {
    val root = JSONObject()
    data.forEach { (id, sr) ->
        root.put(id.toString(), JSONObject().apply {
            put("checkedAt", sr.checkedAt)
            put("notifiedMax", sr.notifiedMax)
            put("releases", JSONArray().apply {
                sr.releases.forEach { r ->
                    put(JSONObject().apply {
                        put("volume", r.volume)
                        put("title", r.title)
                        r.date?.let { put("date", it) }
                        put("source", r.source)
                        r.link?.let { put("link", it) }
                    })
                }
            })
        })
    }
    return root.toString()
}

/**
 * Relit releases.json. Une entrée invalide (série ou sortie) est ignorée, pas tout le cache.
 * @throws JSONException si le fichier n'est pas un objet JSON.
 */
fun releaseCacheFromJson(text: String): Map<Long, SeriesReleases> {
    val root = JSONObject(text)
    val result = mutableMapOf<Long, SeriesReleases>()
    for (key in root.keys()) {
        val id = key.toLongOrNull() ?: continue
        val o = root.optJSONObject(key) ?: continue
        val array = o.optJSONArray("releases") ?: continue
        val releases = (0 until minOf(array.length(), MAX_RELEASES_PER_SERIES * 4)).mapNotNull { i ->
            val r = array.optJSONObject(i) ?: return@mapNotNull null
            // optInt renverrait 0 pour une valeur non numérique : rejetée par sanitized().
            Release(
                volume = r.optInt("volume", 0),
                title = r.optString("title"),
                date = r.optString("date").takeIf { it.isNotEmpty() },
                source = r.optString("source"),
                link = r.optString("link").takeIf { it.isNotEmpty() },
            )
        }
        result[id] = SeriesReleases(o.optLong("checkedAt", 0), releases, o.optInt("notifiedMax", 0)).sanitized()
    }
    return result
}
