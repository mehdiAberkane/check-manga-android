package com.mangareminder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.mangareminder.data.Series
import com.mangareminder.data.SeriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class SeriesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SeriesRepository(app)

    // Un seul thread d'écriture : les sauvegardes s'appliquent dans l'ordre des modifications.
    private val saver = Executors.newSingleThreadExecutor()

    private val _series = MutableStateFlow(sorted(repository.load()))
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    fun addSeries(title: String, totalVolumes: Int?): Long {
        val id = System.currentTimeMillis()
        update { it + Series(id = id, title = title, totalVolumes = totalVolumes) }
        return id
    }

    fun editSeries(id: Long, title: String, totalVolumes: Int?) = updateSeries(id) {
        it.copy(title = title, totalVolumes = totalVolumes)
    }

    fun deleteSeries(id: Long) = update { list -> list.filterNot { it.id == id } }

    fun toggleVolume(id: Long, volume: Int) = updateSeries(id) {
        val owned = if (volume in it.ownedVolumes) it.ownedVolumes - volume else it.ownedVolumes + volume
        it.copy(ownedVolumes = owned)
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
            resolver.openInputStream(uri)!!.use { SeriesRepository.fromJson(it.reader().readText()) }
        }.getOrNull()
    }

    fun replaceAll(series: List<Series>) = update { series }

    private fun updateSeries(id: Long, transform: (Series) -> Series) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    private fun update(transform: (List<Series>) -> List<Series>) {
        val newList = sorted(transform(_series.value))
        _series.value = newList
        saver.execute { repository.save(newList) }
    }

    private fun sorted(list: List<Series>) = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    override fun onCleared() {
        saver.shutdown()
    }
}
