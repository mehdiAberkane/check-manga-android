package com.mangareminder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.mangareminder.data.MAX_VOLUME
import com.mangareminder.data.Series
import com.mangareminder.data.SeriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors

class SeriesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SeriesRepository(app)

    // Un seul thread d'écriture : les sauvegardes s'appliquent dans l'ordre des modifications.
    private val saver = Executors.newSingleThreadExecutor()

    private val _series = MutableStateFlow(sorted(repository.load()))
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    /** Passe à true si une écriture échoue : les dernières modifications risquent d'être perdues. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun dismissSaveError() {
        _saveFailed.value = false
    }

    fun addSeries(title: String, totalVolumes: Int?): Long {
        // Deux ajouts dans la même milliseconde ne doivent pas partager un id.
        val id = maxOf(System.currentTimeMillis(), (_series.value.maxOfOrNull { it.id } ?: 0) + 1)
        update { it + Series(id = id, title = title, totalVolumes = totalVolumes) }
        return id
    }

    fun editSeries(id: Long, title: String, totalVolumes: Int?) = updateSeries(id) {
        it.copy(title = title, totalVolumes = totalVolumes)
    }

    fun deleteSeries(id: Long) = update { list -> list.filterNot { it.id == id } }

    fun toggleVolume(id: Long, volume: Int) {
        // Hors bornes, la collection serait rejetée au prochain chargement.
        if (volume !in 1..MAX_VOLUME) return
        updateSeries(id) {
            val owned = if (volume in it.ownedVolumes) it.ownedVolumes - volume else it.ownedVolumes + volume
            it.copy(ownedVolumes = owned)
        }
    }

    fun addVolumes(id: Long, volumes: Set<Int>) = updateSeries(id) {
        it.copy(ownedVolumes = it.ownedVolumes + volumes)
    }

    /** Écrit la collection dans un fichier choisi par l'utilisateur. */
    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(uri)!!.use {
                it.write(SeriesRepository.toJson(_series.value).toByteArray())
            }
        }.isSuccess
    }

    /** Lit un fichier exporté ; null s'il est illisible. N'applique rien. */
    suspend fun readBackup(uri: Uri): List<Series>? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val bytes = resolver.openInputStream(uri)!!.use { it.readAtMost(SeriesRepository.MAX_BACKUP_BYTES) }
            SeriesRepository.fromJson(bytes.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    /** Évite de charger en mémoire un gros fichier choisi par erreur. */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = read(buffer)
            if (n == -1) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw IOException("Fichier trop volumineux")
        }
        return out.toByteArray()
    }

    fun replaceAll(series: List<Series>) = update { series }

    private fun updateSeries(id: Long, transform: (Series) -> Series) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    private fun update(transform: (List<Series>) -> List<Series>) {
        val newList = sorted(transform(_series.value))
        _series.value = newList
        saver.execute {
            try {
                repository.save(newList)
            } catch (e: IOException) {
                _saveFailed.value = true
            }
        }
    }

    private fun sorted(list: List<Series>) = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    override fun onCleared() {
        saver.shutdown()
    }
}
