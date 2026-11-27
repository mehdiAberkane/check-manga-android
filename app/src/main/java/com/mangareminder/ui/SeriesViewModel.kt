package com.mangareminder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.mangareminder.backup.AutoBackup
import com.mangareminder.backup.BackupStatus
import com.mangareminder.data.Series
import com.mangareminder.data.SeriesRepository
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
import java.time.LocalDate
import java.io.IOException
import java.io.InputStream
import android.content.Context
import androidx.core.content.edit
import com.mangareminder.data.ListFilter
import com.mangareminder.data.ListSort
import com.mangareminder.data.enumOrDefault
import androidx.annotation.StringRes
import com.mangareminder.R
import com.mangareminder.data.ScanMatch
import com.mangareminder.data.matchScannedBook
import com.mangareminder.data.normalizeIsbn
import com.mangareminder.data.parseIsbnLookup
import com.mangareminder.releases.GOOGLE_BOOKS_HOSTS
import com.mangareminder.releases.GoogleBooksKeyStore
import com.mangareminder.releases.KeyTestResult
import com.mangareminder.releases.maskApiKey
import com.mangareminder.releases.normalizeApiKey
import com.mangareminder.releases.googleBooksUrl
import com.mangareminder.releases.Http
import com.mangareminder.data.UndoAction
import com.mangareminder.AppCollection
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.mangareminder.releases.CheckError

class SeriesViewModel(app: Application) : AndroidViewModel(app) {
    /**
     * Collection et enregistrement (logique testée en unitaire), partagés avec les actions des
     * notifications ; ici, seulement les effets Android.
     */
    private val store = AppCollection.get(app)

    /** Faux tant que la collection n'est pas lue (lecture hors du fil principal). */
    val loaded: StateFlow<Boolean> = store.loaded

    val series: StateFlow<List<Series>> = store.series
    /** series.json illisible : rien n'est enregistré tant que l'utilisateur n'a pas choisi. */
    val collectionUnreadable: StateFlow<Boolean> = store.unreadable
    val saveFailed: StateFlow<Boolean> = store.saveFailed
    val pendingImport: StateFlow<List<Series>?> = store.pendingImport
    val undo: StateFlow<UndoAction?> = store.undo

    init {
        AutoBackup.init(app)
        // Lecture des fichiers hors du fil principal ; l'écran affiche un chargement d'ici là.
        viewModelScope.launch(Dispatchers.IO) {
            ReleaseStore.init(app)
            GoogleBooksKeyStore.init(app)
            _apiKeyMask.value = GoogleBooksKeyStore.key?.let(::maskApiKey)
            store.load()
            ReleaseScheduler.sync(app, store.anyWatched)
            CoverCache.cleanUpDaily(app, store.series.value, ReleaseStore.data.value)
        }
    }

    private val _apiKeyMask = MutableStateFlow<String?>(null)
    /** Fin de la clé d'API Google Books enregistrée (« …a1B2 »), ou null : requêtes anonymes. */
    val apiKeyMask: StateFlow<String?> = _apiKeyMask.asStateFlow()

    /** @return false si la clé n'a pas la bonne forme ou n'a pas pu être enregistrée. */
    suspend fun saveApiKey(raw: String): Boolean = withContext(Dispatchers.IO) {
        GoogleBooksKeyStore.save(getApplication(), raw).also { saved ->
            if (saved) _apiKeyMask.value = GoogleBooksKeyStore.key?.let(::maskApiKey)
        }
    }

    suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        GoogleBooksKeyStore.clear(getApplication())
        _apiKeyMask.value = null
    }

    /** Teste la clé tapée, ou l'enregistrée si [raw] est vide ; null s'il n'y a aucune clé valable. */
    suspend fun testApiKey(raw: String): KeyTestResult? {
        val key = (if (raw.isBlank()) GoogleBooksKeyStore.key else normalizeApiKey(raw)) ?: return null
        return withContext(Dispatchers.IO) { GoogleBooksSource.testKey(key) }
    }

    /** Dernières sorties trouvées, par id de série. */
    val releases: StateFlow<Map<Long, SeriesReleases>> = ReleaseStore.data
    /**
     * Vérification en cours, ou demandée et pas encore lancée (attente du réseau). Une tâche en
     * attente d'un nouvel essai ne compte pas : le bouton « Vérifier maintenant » resterait grisé.
     */
    val checking: StateFlow<Boolean> = combine(
        ReleaseStore.checking,
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(ReleaseScheduler.NOW_WORK),
    ) { running, infos ->
        running || infos.any {
            it.state == WorkInfo.State.RUNNING || (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount == 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val checkError: StateFlow<CheckError?> = ReleaseStore.lastError

    /** Active ou coupe la surveillance ; une activation lance une vérification immédiate. */
    fun toggleWatch(id: Long) {
        val nowWatched = store.toggleWatch(id)
        store.afterSave {
            ReleaseScheduler.sync(getApplication<Application>(), store.anyWatched)
            if (nowWatched) ReleaseScheduler.checkNow(getApplication<Application>())
        }
    }

    fun checkReleasesNow() {
        store.afterSave { ReleaseScheduler.checkNow(getApplication<Application>()) }
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
        store.setCover(id, url)
        _coverSearch.value = null
    }

    fun dismissCoverSearch() {
        _coverSearch.value = null
    }

    val backupStatus: StateFlow<BackupStatus> = AutoBackup.status

    // Filtre et tri de la liste, mémorisés entre deux ouvertures de l'app (préférences privées).
    private val listPrefs = app.getSharedPreferences("list", Context.MODE_PRIVATE)
    private val _listFilter = MutableStateFlow(enumOrDefault(listPrefs.getString("filter", null), ListFilter.ALL))
    val listFilter: StateFlow<ListFilter> = _listFilter.asStateFlow()
    private val _listSort = MutableStateFlow(enumOrDefault(listPrefs.getString("sort", null), ListSort.TITLE))
    val listSort: StateFlow<ListSort> = _listSort.asStateFlow()

    fun setListFilter(filter: ListFilter) {
        _listFilter.value = filter
        listPrefs.edit { putString("filter", filter.name) }
    }

    fun setListSort(sort: ListSort) {
        _listSort.value = sort
        listPrefs.edit { putString("sort", sort.name) }
    }

    /** @return false si le dossier choisi ne peut pas être gardé (accès refusé par Android). */
    fun enableBackup(folder: Uri): Boolean = runCatching {
        AutoBackup.enable(getApplication<Application>(), folder)
    }.isSuccess

    fun disableBackup() = AutoBackup.disable(getApplication<Application>())

    /** Après les écritures en attente, pour sauvegarder la dernière version. */
    fun backupNow() = store.afterSave { AutoBackup.backupNow(getApplication<Application>()) }

    private val _scan = MutableStateFlow<ScanState?>(null)
    /** Tome scanné : recherche en cours, résultat, ou saisie manuelle de l'ISBN. */
    val scan: StateFlow<ScanState?> = _scan.asStateFlow()

    /** Scanner indisponible (pas de services Play…) : saisie de l'ISBN au clavier. */
    fun scanManually() {
        _scan.value = ScanState.Manual()
    }

    /** Code lu par le scanner ou tapé : vérifié, puis cherché sur Google Books. */
    fun lookupIsbn(raw: String) {
        val isbn = normalizeIsbn(raw)
        if (isbn == null) {
            _scan.value = ScanState.Manual(raw.take(20), R.string.scan_isbn_invalid)
            return
        }
        _scan.value = ScanState.Looking(isbn)
        viewModelScope.launch {
            val title = withContext(Dispatchers.IO) {
                runCatching {
                    parseIsbnLookup(Http.get(googleBooksUrl("isbn:$isbn"), GOOGLE_BOOKS_HOSTS))
                }
            }
            // La recherche peut avoir été fermée entre-temps.
            if (_scan.value != ScanState.Looking(isbn)) return@launch
            _scan.value = title.fold(
                onSuccess = { found ->
                    if (found == null) ScanState.Manual(isbn, R.string.scan_not_found)
                    else ScanState.Found(isbn, matchScannedBook(series.value, found))
                },
                onFailure = { ScanState.Manual(isbn, R.string.scan_lookup_failed) },
            )
        }
    }

    fun markScannedOwned(match: ScanMatch.InCollection) {
        addVolumes(match.series.id, setOf(match.volume))
        val updated = series.value.find { it.id == match.series.id } ?: return
        (_scan.value as? ScanState.Found)?.let { _scan.value = it.copy(match = match.copy(series = updated)) }
    }

    /** Crée la série proposée, avec le tome scanné s'il a été reconnu ; renvoie son id. */
    fun createScannedSeries(match: ScanMatch.NewSeries): Long {
        val id = addSeries(match.suggestedTitle, null)
        match.volume?.let { addVolumes(id, setOf(it)) }
        _scan.value = null
        return id
    }

    fun dismissScan() {
        _scan.value = null
    }

    fun dismissSaveError() = store.dismissSaveError()

    fun addSeries(title: String, totalVolumes: Int?): Long = store.addSeries(title, totalVolumes)

    fun editSeries(id: Long, title: String, totalVolumes: Int?) = store.editSeries(id, title, totalVolumes)

    fun deleteSeries(id: Long) = store.deleteSeries(id)

    fun undo() {
        if (store.undo()) syncScheduler()
    }

    fun forgetUndo(action: UndoAction) = store.forgetUndo(action)

    fun toggleVolume(id: Long, volume: Int) = store.toggleVolume(id, volume)

    fun setRead(id: Long, volume: Int, read: Boolean) = store.setRead(id, volume, read)

    fun lend(id: Long, volume: Int, to: String) = store.lend(id, volume, to, LocalDate.now().toString())

    fun returnLoan(id: Long, volume: Int) = store.returnLoan(id, volume)

    fun addVolumes(id: Long, volumes: Set<Int>) = store.addVolumes(id, volumes)

    /** Écrit la collection dans un fichier choisi par l'utilisateur. */
    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(uri)!!.use {
                it.write(SeriesRepository.toJson(series.value).toByteArray())
            }
        }.isSuccess
    }

    /**
     * Lit un fichier exporté et le met en attente de confirmation, sans rien appliquer.
     * @return false si le fichier n'est pas une sauvegarde valide.
     */
    suspend fun readBackup(uri: Uri): Boolean {
        val series = parseBackup(uri) ?: return false
        store.setPendingImport(series)
        return true
    }

    fun confirmImport() {
        store.confirmImport()
        syncScheduler()
    }

    fun cancelImport() = store.cancelImport()

    /** Collection illisible : propose la dernière sauvegarde automatique (via la confirmation d'import). */
    suspend fun loadLatestBackup(): Boolean {
        val (_, text) = AutoBackup.readLatest(getApplication<Application>()) ?: return false
        val series = runCatching { SeriesRepository.fromJson(text) }.getOrNull() ?: return false
        store.setPendingImport(series)
        return true
    }

    /** Collection illisible : copie du fichier abîmé dans un fichier choisi par l'utilisateur. */
    suspend fun exportDamaged(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = store.readDamaged() ?: return@withContext false
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        }.isSuccess
    }

    /** Collection illisible : repartir de zéro (choix confirmé par l'utilisateur). */
    fun startEmpty() = store.startEmpty()

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

    /** La surveillance périodique suit la présence de séries surveillées, après les écritures en cours. */
    private fun syncScheduler() {
        store.afterSave { ReleaseScheduler.sync(getApplication<Application>(), store.anyWatched) }
    }

}

sealed interface CoverSearch {
    data object Loading : CoverSearch
    /** partial : Google Books n'a pas répondu, seules les couvertures déjà connues sont proposées. */
    data class Results(val choices: List<Release>, val partial: Boolean = false) : CoverSearch
    data object Failed : CoverSearch
}

sealed interface ScanState {
    data class Looking(val isbn: String) : ScanState
    data class Found(val isbn: String, val match: ScanMatch) : ScanState
    /** Saisie au clavier, avec le code déjà lu et la raison (introuvable, réseau…) s'il y en a une. */
    data class Manual(val isbn: String? = null, @param:StringRes val message: Int? = null) : ScanState
}
