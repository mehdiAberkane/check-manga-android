package com.mangareminder.data

import android.content.Context
import com.mangareminder.releases.isSafeCoverUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stocke la collection dans un petit fichier JSON privé à l'application.
 * Il est inclus dans la sauvegarde automatique Android (allowBackup).
 */
class SeriesRepository(dir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(dir, "series.json")

    /**
     * Lit la collection en distinguant « pas encore de fichier » et « fichier illisible ».
     * @param setAside met une copie datée d'un fichier illisible de côté (l'écran le fait ; la
     *   tâche de fond, qui ne fait que lire, non).
     */
    fun load(setAside: Boolean = true): LoadResult {
        if (!file.exists()) return LoadResult.Missing
        return try {
            LoadResult.Ok(fromJson(file.readText()))
        } catch (e: JSONException) {
            if (setAside) setAsideCorrupt()
            LoadResult.Unreadable
        } catch (e: IOException) {
            if (setAside) setAsideCorrupt()
            LoadResult.Unreadable
        }
    }

    /** Contenu brut du fichier abîmé, pour l'exporter ; null s'il ne peut pas être lu du tout. */
    fun readDamaged(): ByteArray? = runCatching { file.readBytes() }.getOrNull()

    /**
     * Copie datée du fichier illisible (series.json.corrupt-AAAAMMJJ-HHMM), les plus récentes
     * gardées. Le fichier lui-même n'est pas touché : rien n'est écrit tant que l'utilisateur n'a
     * pas choisi quoi faire. Hors sauvegarde Android (seul series.json y est inclus).
     */
    private fun setAsideCorrupt() {
        runCatching {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            file.copyTo(File(file.parentFile, "$CORRUPT_PREFIX$stamp"), overwrite = true)
        }
        val names = file.parentFile?.list()?.toList().orEmpty()
        corruptCopiesToDelete(names).forEach { File(file.parentFile, it).delete() }
    }

    /** Contenu brut de series.json, ou null s'il n'existe pas. Ne met rien de côté (sauvegarde automatique). */
    fun readRaw(): String? = file.takeIf { it.exists() }?.readText()

    /** @throws IOException si la collection n'a pas pu être écrite. */
    fun save(series: List<Series>) {
        val tmp = File(file.parentFile, "series.json.tmp")
        tmp.writeText(toJson(series))
        // Remplacement atomique : series.json est soit l'ancienne, soit la nouvelle version.
        // (File.renameTo ne remplace pas un fichier existant sur toutes les plateformes.)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        /** Même format pour le stockage interne et les fichiers exportés. */
        fun toJson(series: List<Series>): String {
            val array = JSONArray()
            series.forEach { s ->
                array.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    s.totalVolumes?.let { put("total", it) }
                    put("owned", JSONArray(s.ownedVolumes.sorted()))
                    if (s.watched) put("watched", true)
                    s.cover?.let { put("cover", it) }
                    if (s.readVolumes.isNotEmpty()) put("read", JSONArray(s.readVolumes.sorted()))
                    if (s.loans.isNotEmpty()) {
                        put("loans", JSONArray().apply {
                            s.loans.toSortedMap().forEach { (volume, loan) ->
                                put(JSONObject().apply {
                                    put("volume", volume)
                                    put("to", loan.to)
                                    loan.date?.let { put("date", it) }
                                })
                            }
                        })
                    }
                })
            }
            return array.toString()
        }

        /**
         * Les valeurs sont bornées : un total ou un numéro de tome énorme ferait construire
         * une grille gigantesque, et des id en double feraient planter les listes Compose.
         * @throws JSONException si le contenu n'est pas une collection valide.
         */
        fun fromJson(text: String): List<Series> {
            val array = JSONArray(text)
            if (array.length() > MAX_SERIES) throw JSONException("Trop de séries")
            val series = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val title = o.getString("title").trim()
                if (title.isEmpty() || title.length > MAX_TITLE_LENGTH) throw JSONException("Titre invalide")
                val total = if (o.has("total")) o.getInt("total") else null
                if (total != null && total !in 1..MAX_VOLUME) throw JSONException("Total invalide")
                val owned = o.getJSONArray("owned")
                val volumes = (0 until owned.length()).map { owned.getInt(it) }.toSet()
                if (volumes.any { it !in 1..MAX_VOLUME }) throw JSONException("Tome invalide")
                Series(
                    id = o.getLong("id"),
                    title = title,
                    totalVolumes = total,
                    ownedVolumes = volumes,
                    watched = o.optBoolean("watched", false),
                    // Champ facultatif : une adresse invalide est ignorée plutôt que de refuser la sauvegarde.
                    cover = o.optString("cover").takeIf { isSafeCoverUrl(it) },
                    readVolumes = readVolumes(o, volumes),
                    loans = loans(o, volumes),
                )
            }
            if (series.distinctBy { it.id }.size != series.size) throw JSONException("Identifiants en double")
            return series
        }

        /** Champ facultatif « read » ; seuls les tomes possédés sont gardés. */
        private fun readVolumes(o: JSONObject, owned: Set<Int>): Set<Int> {
            val array = o.optJSONArray("read") ?: return emptySet()
            val read = (0 until array.length()).map { array.getInt(it) }.toSet()
            if (read.any { it !in 1..MAX_VOLUME }) throw JSONException("Tome lu invalide")
            return read intersect owned
        }

        /** Champ facultatif « loans » ; seuls les tomes possédés sont gardés. */
        private fun loans(o: JSONObject, owned: Set<Int>): Map<Int, Loan> {
            val array = o.optJSONArray("loans") ?: return emptyMap()
            if (array.length() > MAX_VOLUME) throw JSONException("Trop de prêts")
            val result = mutableMapOf<Int, Loan>()
            for (i in 0 until array.length()) {
                val l = array.getJSONObject(i)
                val volume = l.getInt("volume")
                if (volume !in 1..MAX_VOLUME) throw JSONException("Tome prêté invalide")
                val to = l.getString("to").trim()
                if (to.isEmpty() || to.length > MAX_BORROWER_LENGTH) throw JSONException("Nom de prêt invalide")
                if (volume in owned) result[volume] = Loan(to, loanDate(l.optString("date")))
            }
            return result
        }

        const val CORRUPT_PREFIX = "series.json.corrupt-"
        const val KEPT_CORRUPT_COPIES = 5

        /** Copies de côté à supprimer pour n'en garder que les plus récentes (le nom est trié par date). */
        fun corruptCopiesToDelete(names: Collection<String>, keep: Int = KEPT_CORRUPT_COPIES): List<String> =
            names.filter { it.matches(CORRUPT_NAME) }.sortedDescending().drop(keep)

        private val CORRUPT_NAME = Regex("series\\.json\\.corrupt-\\d{8}-\\d{4}")

        const val MAX_SERIES = 5_000
        const val MAX_TITLE_LENGTH = 200
        /** Une vraie sauvegarde fait quelques dizaines de Ko. */
        const val MAX_BACKUP_BYTES = 1_000_000
    }
}

/** Résultat de la lecture de series.json. */
sealed interface LoadResult {
    /** Pas encore de fichier : première ouverture, collection vide. */
    data object Missing : LoadResult
    data class Ok(val series: List<Series>) : LoadResult
    /** JSON invalide ou lecture impossible : ne rien écrire tant que l'utilisateur n'a pas choisi. */
    data object Unreadable : LoadResult
}
