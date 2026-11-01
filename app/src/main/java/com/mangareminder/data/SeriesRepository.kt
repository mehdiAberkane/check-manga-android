package com.mangareminder.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

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

    fun save(series: List<Series>) {
        val tmp = File(file.parentFile, "series.json.tmp")
        tmp.writeText(toJson(series))
        tmp.renameTo(file)
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

        /** @throws JSONException si le contenu n'est pas une collection valide. */
        fun fromJson(text: String): List<Series> {
            val array = JSONArray(text)
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val owned = o.getJSONArray("owned")
                Series(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    totalVolumes = if (o.has("total")) o.getInt("total") else null,
                    ownedVolumes = (0 until owned.length()).map { owned.getInt(it) }.toSet(),
                )
            }
        }
    }
}
