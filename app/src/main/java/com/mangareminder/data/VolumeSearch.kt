package com.mangareminder.data

// --- Recherche « série + numéro » en magasin, sans dépendance Android (testé en unitaire). ---

/** Une recherche du type « one piece 45 » : le titre cherché et le numéro du tome. */
data class VolumeQuery(val title: String, val volume: Int)

/** Réponse affichée en haut de la liste : est-ce que j'ai ce tome ? */
data class VolumeAnswer(val series: Series, val volume: Int) {
    val owned: Boolean get() = volume in series.ownedVolumes

    /** Le tome dépasse le nombre de tomes parus indiqué pour la série. */
    val beyondTotal: Boolean get() = series.totalVolumes?.let { volume > it } ?: false
}

const val MAX_VOLUME_ANSWERS = 3

// Numéro en fin de recherche, éventuellement précédé de « t », « tome », « vol », « n » ou « # ».
private val VOLUME_SUFFIX = Regex("^(.*?)\\s*(?:\\b(?:tome|tomes|vol|volume|t|n)|#)?\\s*(\\d{1,3})$")

/** « one piece 45 », « One Piece T45 », « one piece tome 45 », « one piece #45 » ; null sinon. */
fun parseVolumeQuery(query: String): VolumeQuery? {
    val match = VOLUME_SUFFIX.matchEntire(query.trim().lowercase()) ?: return null
    val title = match.groupValues[1].trim()
    val volume = match.groupValues[2].toInt()
    if (searchKey(title).isEmpty() || volume !in 1..MAX_VOLUME) return null
    return VolumeQuery(title, volume)
}

/**
 * Réponses pour les séries dont le titre correspond à la recherche sans le numéro.
 * Une série dont le titre correspond déjà à la recherche entière (« Kaiju No. 8 ») n'a pas de
 * réponse : le nombre fait partie de son titre.
 */
fun volumeAnswers(series: List<Series>, query: String): List<VolumeAnswer> {
    val parsed = parseVolumeQuery(query) ?: return emptyList()
    return series
        .filter { matchesSearch(it.title, parsed.title) && !matchesSearch(it.title, query) }
        .take(MAX_VOLUME_ANSWERS)
        .map { VolumeAnswer(it, parsed.volume) }
}

/** Séries de la liste : celles qui correspondent à la recherche, plus celles d'une réponse « série + numéro ». */
fun searchResults(series: List<Series>, query: String): List<Series> {
    val answered = volumeAnswers(series, query).map { it.series.id }.toSet()
    return series.filter { matchesSearch(it.title, query) || it.id in answered }
}
