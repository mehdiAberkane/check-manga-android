package com.mangareminder.releases

import android.content.Context
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
            when {
                !f.exists() -> emptyMap()
                // Fichier anormalement gros : ignoré plutôt que chargé en mémoire.
                f.length() > MAX_RELEASE_CACHE_BYTES -> emptyMap()
                else -> releaseCacheFromJson(f.readText())
            }
        } catch (e: Exception) {
            // JSON illisible, id invalide, lecture impossible… Simple cache : il sera reconstruit
            // à la prochaine vérification.
            emptyMap()
        }
    }

    fun get(seriesId: Long): SeriesReleases? = _data.value[seriesId]

    @Synchronized
    fun put(seriesId: Long, releases: SeriesReleases) {
        _data.value = _data.value + (seriesId to releases.sanitized())
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
            tmp.writeText(releaseCacheToJson(_data.value))
            tmp.renameTo(f)
        } catch (e: IOException) {
            // Cache non critique : on réessaiera à la prochaine vérification.
        }
    }
}
