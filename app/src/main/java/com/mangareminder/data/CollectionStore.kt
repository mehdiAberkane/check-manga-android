package com.mangareminder.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.Executor

/** Dernière opération annulable quelques secondes (message « Annuler »). */
sealed interface UndoAction {
    data class Deleted(val series: Series) : UndoAction
    /** previous : la collection remplacée par l'import. */
    data class Imported(val previous: List<Series>, val count: Int) : UndoAction
}

/**
 * La collection en mémoire et son enregistrement, sans dépendance Android (testée en unitaire
 * sur un dossier temporaire). Le ViewModel n'y ajoute que les effets propres à Android.
 *
 * La collection est lue par [load], à appeler hors du fil principal. Avant la fin du chargement,
 * les modifications sont ignorées : elles écraseraient le fichier par une collection vide.
 *
 * @param saver un seul fil d'écriture : les enregistrements s'appliquent dans l'ordre des modifications.
 * @param afterSaved appelé après chaque enregistrement réussi, avec la collection enregistrée
 *   (sauvegarde automatique, widget).
 */
class CollectionStore(
    private val repository: SeriesRepository,
    private val saver: Executor,
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterSaved: (List<Series>) -> Unit = {},
) {
    private val _loaded = MutableStateFlow(false)
    /** Faux tant que series.json n'a pas été lu : l'écran affiche un chargement. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _unreadable = MutableStateFlow(false)
    /**
     * series.json existe mais n'a pas pu être lu. Tant que l'utilisateur n'a pas choisi (restaurer,
     * importer, repartir de zéro), aucune modification n'est enregistrée : le fichier reste intact.
     */
    val unreadable: StateFlow<Boolean> = _unreadable.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    /** Passe à true si une écriture échoue : les dernières modifications risquent d'être perdues. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _pendingImport = MutableStateFlow<List<Series>?>(null)
    /** Sauvegarde lue, en attente de confirmation (survit à une rotation : gardée hors de l'écran). */
    val pendingImport: StateFlow<List<Series>?> = _pendingImport.asStateFlow()

    private val _undo = MutableStateFlow<UndoAction?>(null)
    val undo: StateFlow<UndoAction?> = _undo.asStateFlow()

    val anyWatched: Boolean get() = _series.value.any { it.watched }

    /** Lit series.json (sans effet s'il a déjà été lu). À appeler hors du fil principal. */
    @Synchronized
    fun load() {
        if (_loaded.value) return
        val result = repository.load()
        _series.value = sorted((result as? LoadResult.Ok)?.series.orEmpty())
        _unreadable.value = result is LoadResult.Unreadable
        _loaded.value = true
    }

    fun dismissSaveError() {
        _saveFailed.value = false
    }

    /** Après les écritures en attente (la tâche de fond relit series.json). */
    fun afterSave(action: () -> Unit) = saver.execute(action)

    fun addSeries(title: String, totalVolumes: Int?): Long {
        // Deux ajouts dans la même milliseconde ne doivent pas partager un id.
        val id = maxOf(clock(), (_series.value.maxOfOrNull { it.id } ?: 0) + 1)
        update { it + Series(id = id, title = title, totalVolumes = totalVolumes) }
        return id
    }

    fun editSeries(id: Long, title: String, totalVolumes: Int?) = updateSeries(id) {
        it.copy(title = title, totalVolumes = totalVolumes)
    }

    /** @return true si la série est maintenant surveillée. */
    fun toggleWatch(id: Long): Boolean {
        updateSeries(id) { it.copy(watched = !it.watched) }
        return _series.value.find { it.id == id }?.watched == true
    }

    fun setCover(id: Long, url: String?) = updateSeries(id) { it.copy(cover = url) }

    fun deleteSeries(id: Long) {
        val deleted = _series.value.find { it.id == id } ?: return
        update { list -> list.filterNot { it.id == id } }
        _undo.value = UndoAction.Deleted(deleted)
    }

    /** @return false s'il n'y avait rien à annuler. */
    fun undo(): Boolean {
        when (val action = _undo.value) {
            is UndoAction.Deleted -> update { restoreSeries(it, action.series) }
            is UndoAction.Imported -> update { action.previous }
            null -> return false
        }
        _undo.value = null
        return true
    }

    /** Le message a disparu sans « Annuler » : on oublie l'état précédent. */
    fun forgetUndo(action: UndoAction) {
        if (_undo.value == action) _undo.value = null
    }

    fun toggleVolume(id: Long, volume: Int) {
        // Hors bornes, la collection serait rejetée au prochain chargement.
        if (volume !in 1..MAX_VOLUME) return
        updateSeries(id) {
            // Un tome retiré perd aussi son statut de lecture et son prêt.
            if (volume in it.ownedVolumes) it.withoutVolume(volume) else it.copy(ownedVolumes = it.ownedVolumes + volume)
        }
    }

    fun addVolumes(id: Long, volumes: Set<Int>) = updateSeries(id) {
        it.copy(ownedVolumes = it.ownedVolumes + volumes.filter { v -> v in 1..MAX_VOLUME })
    }

    fun setRead(id: Long, volume: Int, read: Boolean) = updateSeries(id) { it.withRead(volume, read) }

    fun lend(id: Long, volume: Int, to: String, date: String) = updateSeries(id) { it.withLoan(volume, to, date) }

    fun returnLoan(id: Long, volume: Int) = updateSeries(id) { it.withoutLoan(volume) }

    fun setPendingImport(series: List<Series>) {
        _pendingImport.value = series
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    /** Remplace la collection par la sauvegarde en attente. */
    fun confirmImport() {
        val series = _pendingImport.value ?: return
        _pendingImport.value = null
        val recovering = _unreadable.value
        _unreadable.value = false
        val previous = _series.value
        update { series }
        // Depuis l'écran « collection illisible », il n'y a rien à quoi revenir.
        if (!recovering) _undo.value = UndoAction.Imported(previous, series.size)
    }

    /**
     * Collection illisible : l'utilisateur choisit de repartir de zéro. Une copie datée du fichier
     * abîmé a été mise de côté au chargement ; series.json sera remplacé à la prochaine modification.
     */
    fun startEmpty() {
        _unreadable.value = false
    }

    fun readDamaged(): ByteArray? = repository.readDamaged()

    private fun updateSeries(id: Long, transform: (Series) -> Series) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    private fun update(transform: (List<Series>) -> List<Series>) {
        if (!_loaded.value || _unreadable.value) return
        val newList = sorted(transform(_series.value))
        _series.value = newList
        saver.execute {
            try {
                repository.save(newList)
                afterSaved(newList)
            } catch (e: IOException) {
                _saveFailed.value = true
            }
        }
    }

    private fun sorted(list: List<Series>) = list.sortedWith(seriesOrder())
}
