package com.mangareminder.releases

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Résultats des vérifications, par id de série, dans releases.json.
 * Partagé entre l'écran et la tâche de fond (même processus).
 */
object ReleaseStore {
    private var file: File? = null

    private val _data = MutableStateFlow<Map<Long, SeriesReleases>>(emptyMap())
    val data: StateFlow<Map<Long, SeriesReleases>> = _data.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** Message de la dernière vérification si une source n'a pas répondu. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "releases.json")
        file = f
        _data.value = try {
            if (f.exists()) fromJson(f.readText()) else emptyMap()
        } catch (e: Exception) {
            // JSON illisible, id invalide, lecture impossible… Simple cache : il sera reconstruit
            // à la prochaine vérification.
            emptyMap()
        }
    }

    fun get(seriesId: Long): SeriesReleases? = _data.value[seriesId]

    @Synchronized
    fun put(seriesId: Long, releases: SeriesReleases) {
        _data.value = _data.value + (seriesId to releases)
        save()
    }

    /** Oublie les séries supprimées. */
    @Synchronized
    fun retainOnly(seriesIds: Set<Long>) {
        if (_data.value.keys.all { it in seriesIds }) return
        _data.value = _data.value.filterKeys { it in seriesIds }
        save()
    }

    fun setChecking(value: Boolean) {
        _checking.value = value
    }

    fun setError(message: String?) {
        _lastError.value = message
    }

    private fun save() {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, "releases.json.tmp")
            tmp.writeText(toJson(_data.value))
            tmp.renameTo(f)
        } catch (e: IOException) {
            // Cache non critique : on réessaiera à la prochaine vérification.
        }
    }

    private fun toJson(data: Map<Long, SeriesReleases>): String {
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

    private fun fromJson(text: String): Map<Long, SeriesReleases> {
        val root = JSONObject(text)
        return root.keys().asSequence().associate { key ->
            val o = root.getJSONObject(key)
            val array = o.getJSONArray("releases")
            val releases = (0 until array.length()).map { i ->
                val r = array.getJSONObject(i)
                Release(
                    volume = r.getInt("volume"),
                    title = r.getString("title"),
                    date = if (r.has("date")) r.getString("date") else null,
                    source = r.getString("source"),
                    link = if (r.has("link")) r.getString("link") else null,
                )
            }
            key.toLong() to SeriesReleases(o.getLong("checkedAt"), releases, o.optInt("notifiedMax", 0))
        }
    }
}
