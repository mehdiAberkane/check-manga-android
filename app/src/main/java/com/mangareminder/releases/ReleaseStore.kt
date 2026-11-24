package com.mangareminder.releases

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.SharedPreferences
import androidx.core.content.edit

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

    private val _lastError = MutableStateFlow<CheckError?>(null)
    /** Échec de la dernière vérification, gardé (préférences privées) jusqu'à la suivante qui réussit. */
    val lastError: StateFlow<CheckError?> = _lastError.asStateFlow()

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        prefs = context.applicationContext.getSharedPreferences("release_check", Context.MODE_PRIVATE).also { p ->
            val failure = p.getString("error", null)?.let { name -> CheckFailure.entries.firstOrNull { it.name == name } }
            _lastError.value = failure?.let { CheckError(it, p.getInt("failed", 0), p.getLong("at", 0)) }
        }
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

    fun setError(error: CheckError?) {
        _lastError.value = error
        prefs?.edit {
            if (error == null) {
                clear()
            } else {
                putString("error", error.failure.name)
                putInt("failed", error.failedSeries)
                putLong("at", error.at)
            }
        }
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
