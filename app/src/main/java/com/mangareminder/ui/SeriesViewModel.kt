package com.mangareminder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.mangareminder.backup.AutoBackup
import com.mangareminder.backup.BackupStatus
import com.mangareminder.data.MAX_VOLUME
import com.mangareminder.widget.MangaWidgetUpdater
import com.mangareminder.data.Series
import com.mangareminder.data.SeriesRepository
import com.mangareminder.data.seriesOrder
import androidx.lifecycle.viewModelScope
import com.mangareminder.releases.GoogleBooksSource
import com.mangareminder.releases.Release
import com.mangareminder.releases.ReleaseScheduler
import com.mangareminder.releases.coverChoices
import com.mangareminder.releases.ReleaseStore
import com.mangareminder.releases.SeriesReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    init {
        ReleaseStore.init(app)
        AutoBackup.init(app)
        ReleaseScheduler.sync(app, _series.value.any { it.watched })
    }

    /** Dernières sorties trouvées, par id de série. */
    val releases: StateFlow<Map<Long, SeriesReleases>> = ReleaseStore.data
    val checking: StateFlow<Boolean> = ReleaseStore.checking
    val checkError: StateFlow<String?> = ReleaseStore.lastError

    /** Active ou coupe la surveillance ; une activation lance une vérification immédiate. */
    fun toggleWatch(id: Long) {
        updateSeries(id) { it.copy(watched = !it.watched) }
        val nowWatched = _series.value.find { it.id == id }?.watched == true
        afterSave {
            ReleaseScheduler.sync(getApplication<Application>(), _series.value.any { it.watched })
            if (nowWatched) ReleaseScheduler.checkNow(getApplication<Application>())
        }
    }

    fun checkReleasesNow() {
        afterSave { ReleaseScheduler.checkNow(getApplication<Application>()) }
    }

    /** La tâche de fond relit series.json : on attend que les écritures en cours soient faites. */
    private fun afterSave(action: () -> Unit) {
        saver.execute(action)
    }

    private val _coverSearch = MutableStateFlow<CoverSearch?>(null)
    /** Recherche de couvertures en cours ou terminée, pour le choix de la couverture d'une série. */
    val coverSearch: StateFlow<CoverSearch?> = _coverSearch.asStateFlow()

    /**
     * Une seule recherche Google Books, lancée par l'utilisateur (le quota de l'API est partagé).
     * Si elle échoue, on propose au moins les couvertures déjà trouvées par la surveillance.
     */
    fun searchCovers(series: Series) {
        if (_coverSearch.value == CoverSearch.Loading) return
        _coverSearch.value = CoverSearch.Loading
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { GoogleBooksSource.search(series.title) } }
            val cached = releases.value[series.id]?.releases.orEmpty()
            _coverSearch.value = found.fold(
                onSuccess = { CoverSearch.Results(coverChoices(it + cached)) },
                onFailure = {
                    if (coverChoices(cached).isEmpty()) CoverSearch.Failed
                    else CoverSearch.Results(coverChoices(cached), partial = true)
                },
            )
        }
    }

    /** null : revenir à la couverture automatique. */
    fun chooseCover(id: Long, url: String?) {
        updateSeries(id) { it.copy(cover = url) }
        _coverSearch.value = null
    }

    fun dismissCoverSearch() {
        _coverSearch.value = null
    }

    val backupStatus: StateFlow<BackupStatus> = AutoBackup.status

    /** @return false si le dossier choisi ne peut pas être gardé (accès refusé par Android). */
    fun enableBackup(folder: Uri): Boolean = runCatching {
        AutoBackup.enable(getApplication<Application>(), folder)
    }.isSuccess

    fun disableBackup() = AutoBackup.disable(getApplication<Application>())

    /** Après les écritures en attente, pour sauvegarder la dernière version. */
    fun backupNow() = afterSave { AutoBackup.backupNow(getApplication<Application>()) }

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

    private val _pendingImport = MutableStateFlow<List<Series>?>(null)
    /**
     * Sauvegarde lue, en attente de confirmation. Gardée ici plutôt que dans l'écran : elle
     * survit à une rotation ou à un changement de thème pendant que la confirmation est affichée.
     */
    val pendingImport: StateFlow<List<Series>?> = _pendingImport.asStateFlow()

    /**
     * Lit un fichier exporté et le met en attente de confirmation, sans rien appliquer.
     * @return false si le fichier n'est pas une sauvegarde valide.
     */
    suspend fun readBackup(uri: Uri): Boolean {
        val series = parseBackup(uri) ?: return false
        _pendingImport.value = series
        return true
    }

    /** Remplace la collection par la sauvegarde en attente ; renvoie le nombre de séries importées. */
    fun confirmImport(): Int {
        val series = _pendingImport.value ?: return 0
        _pendingImport.value = null
        replaceAll(series)
        return series.size
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    private suspend fun parseBackup(uri: Uri): List<Series>? = withContext(Dispatchers.IO) {
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

    private fun replaceAll(series: List<Series>) {
        update { series }
        afterSave { ReleaseScheduler.sync(getApplication<Application>(), _series.value.any { it.watched }) }
    }

    private fun updateSeries(id: Long, transform: (Series) -> Series) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    private fun update(transform: (List<Series>) -> List<Series>) {
        val newList = sorted(transform(_series.value))
        _series.value = newList
        saver.execute {
            try {
                repository.save(newList)
                AutoBackup.scheduleAfterChange(getApplication<Application>())
                MangaWidgetUpdater.update(getApplication<Application>())
            } catch (e: IOException) {
                _saveFailed.value = true
            }
        }
    }

    private fun sorted(list: List<Series>) = list.sortedWith(seriesOrder())

    override fun onCleared() {
        saver.shutdown()
    }
}

sealed interface CoverSearch {
    data object Loading : CoverSearch
    /** partial : Google Books n'a pas répondu, seules les couvertures déjà connues sont proposées. */
    data class Results(val choices: List<Release>, val partial: Boolean = false) : CoverSearch
    data object Failed : CoverSearch
}
