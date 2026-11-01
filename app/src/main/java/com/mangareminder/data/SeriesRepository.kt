package com.mangareminder.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Stocke la collection dans un petit fichier JSON privé à l'application.
 * Il est inclus dans la sauvegarde automatique Android (allowBackup).
 */
class SeriesRepository(context: Context) {
    private val file = File(context.filesDir, "series.json")

    fun load(): List<Series> {
        if (!file.exists()) return emptyList()
        return try {
            fromJson(file.readText())
        } catch (e: JSONException) {
            // On met le fichier illisible de côté plutôt que de l'écraser à la prochaine sauvegarde.
            file.copyTo(File(file.parentFile, "series.json.corrupt"), overwrite = true)
            emptyList()
        }
    }

    /** @throws IOException si la collection n'a pas pu être écrite. */
    fun save(series: List<Series>) {
        val tmp = File(file.parentFile, "series.json.tmp")
        tmp.writeText(toJson(series))
        if (!tmp.renameTo(file)) throw IOException("Impossible de remplacer ${file.name}")
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
                Series(id = o.getLong("id"), title = title, totalVolumes = total, ownedVolumes = volumes)
            }
            if (series.distinctBy { it.id }.size != series.size) throw JSONException("Identifiants en double")
            return series
        }

        const val MAX_SERIES = 5_000
        const val MAX_TITLE_LENGTH = 200
        /** Une vraie sauvegarde fait quelques dizaines de Ko. */
        const val MAX_BACKUP_BYTES = 1_000_000
    }
}
