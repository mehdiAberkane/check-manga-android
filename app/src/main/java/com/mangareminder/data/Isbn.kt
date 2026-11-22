package com.mangareminder.data

import com.mangareminder.releases.extractVolume
import org.json.JSONException
import org.json.JSONObject

// --- Tome scanné (ISBN), sans dépendance Android (testé en unitaire). ---

/**
 * ISBN-13 (978 / 979) ou ISBN-10 valide, sans tirets ni espaces ; null sinon.
 * La clé de contrôle évite de chercher un code mal lu ou mal tapé.
 */
fun normalizeIsbn(raw: String): String? {
    val code = raw.filter { it.isLetterOrDigit() }.uppercase()
    return when {
        code.length == 13 && code.all { it.isDigit() } && (code.startsWith("978") || code.startsWith("979")) -> {
            val sum = code.take(12).mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
            code.takeIf { (10 - sum % 10) % 10 == code[12] - '0' }
        }
        code.length == 10 && code.take(9).all { it.isDigit() } && (code[9].isDigit() || code[9] == 'X') -> {
            val sum = code.mapIndexed { i, c -> (10 - i) * if (c == 'X') 10 else c - '0' }.sum()
            code.takeIf { sum % 11 == 0 }
        }
        else -> null
    }
}

/** Titre complet du livre dans une réponse Google Books `isbn:…` ; null si aucun livre. */
fun parseIsbnLookup(json: String): String? = try {
    val info = JSONObject(json).optJSONArray("items")?.optJSONObject(0)?.optJSONObject("volumeInfo")
    info?.let {
        listOf(it.optString("title"), it.optString("subtitle"))
            .filter { part -> part.isNotBlank() }
            .joinToString(" - ")
            .take(SeriesRepository.MAX_TITLE_LENGTH)
            .ifBlank { null }
    }
} catch (e: JSONException) {
    null
}

/** Résultat du scan rapproché de la collection. */
sealed interface ScanMatch {
    /** Le livre est un tome d'une série de la collection. */
    data class InCollection(val series: Series, val volume: Int) : ScanMatch {
        val owned: Boolean get() = volume in series.ownedVolumes
    }

    /** Série inconnue : titre proposé pour la créer, et numéro du tome s'il a été reconnu. */
    data class NewSeries(val bookTitle: String, val suggestedTitle: String, val volume: Int?) : ScanMatch
}

/** Rattache le titre du livre (« One Piece - Tome 45 ») à une série de la collection. */
fun matchScannedBook(series: List<Series>, bookTitle: String): ScanMatch {
    // Le titre de série le plus long d'abord : « Naruto Shippuden » avant « Naruto ».
    series.sortedByDescending { searchKey(it.title).length }.forEach { s ->
        extractVolume(bookTitle, s.title)?.let { return ScanMatch.InCollection(s, it) }
    }
    val suggested = suggestSeriesTitle(bookTitle)
    return ScanMatch.NewSeries(bookTitle, suggested, extractVolume(bookTitle, suggested))
}

// « - Tome 45 », « T45 », « Vol. 3 », « 45 » en fin de titre, avec ce qui suit.
private val VOLUME_PART = Regex("(?i)[\\s\\-–—,.:]*\\b(tome|tomes|volume|vol|t|n°|n)\\.?\\s*\\d{1,3}\\b.*$|[\\s\\-–—,.:]+\\d{1,3}\\s*$")

/** Titre de série proposé à partir du titre du livre, sans la mention du tome. */
fun suggestSeriesTitle(bookTitle: String): String =
    bookTitle.replace(VOLUME_PART, "").trim().ifBlank { bookTitle.trim() }.take(SeriesRepository.MAX_TITLE_LENGTH)
