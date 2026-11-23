package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class CollectionStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Écritures exécutées tout de suite, dans l'ordre : comme le fil d'écriture unique de l'app. */
    private val direct = Executor { it.run() }
    private var saves = 0

    private fun store(clock: () -> Long = { 1_000L }) =
        CollectionStore(SeriesRepository(tmp.root), direct, clock, afterSaved = { saves++ })

    private fun reload() = SeriesRepository(tmp.root).load()

    @Test
    fun `modifications enregistrées dans l'ordre et relues à l'identique`() {
        val s = store()
        val id = s.addSeries("Naruto", 72)
        s.toggleVolume(id, 1)
        s.toggleVolume(id, 2)
        s.toggleVolume(id, 1)
        s.editSeries(id, "Naruto (édition originale)", 72)
        assertEquals(LoadResult.Ok(s.series.value), reload())
        assertEquals(setOf(2), s.series.value.single().ownedVolumes)
        assertEquals(5, saves)
    }

    @Test
    fun `ids uniques même dans la même milliseconde`() {
        val s = store(clock = { 42L })
        val a = s.addSeries("A", null)
        val b = s.addSeries("B", null)
        val c = s.addSeries("C", null)
        assertEquals(3, setOf(a, b, c).size)
        assertTrue(b > a && c > b)
    }

    @Test
    fun `liste toujours rangée par titre`() {
        val s = store()
        s.addSeries("Zelda", null)
        s.addSeries("Été indien", null)
        s.addSeries("Akira", null)
        assertEquals(listOf("Akira", "Été indien", "Zelda"), s.series.value.map { it.title })
    }

    @Test
    fun `échec d'écriture signalé`() {
        // Un dossier à la place du fichier temporaire : l'écriture lève une IOException.
        File(tmp.root, "series.json.tmp").mkdir()
        val s = store()
        assertFalse(s.saveFailed.value)
        s.addSeries("Naruto", null)
        assertTrue(s.saveFailed.value)
        assertEquals(0, saves)
        s.dismissSaveError()
        assertFalse(s.saveFailed.value)
    }

    @Test
    fun `tome hors bornes ignoré, retirer un tome efface son prêt`() {
        val s = store()
        val id = s.addSeries("Naruto", null)
        s.toggleVolume(id, 0)
        s.toggleVolume(id, 1000)
        s.addVolumes(id, setOf(1, 2, 1000))
        s.lend(id, 2, "Paul", "2026-11-22")
        s.setRead(id, 2, true)
        s.toggleVolume(id, 2)
        val series = s.series.value.single()
        assertEquals(setOf(1), series.ownedVolumes)
        assertEquals(emptyMap<Int, Loan>(), series.loans)
        assertEquals(emptySet<Int>(), series.readVolumes)
    }

    @Test
    fun `suppression puis annulation`() {
        val s = store()
        val id = s.addSeries("Naruto", null)
        s.toggleVolume(id, 3)
        val before = s.series.value.single()
        s.deleteSeries(id)
        assertEquals(emptyList<Series>(), s.series.value)
        assertTrue(s.undo())
        assertEquals(listOf(before), s.series.value)
        assertEquals(LoadResult.Ok(listOf(before)), reload())
        assertFalse(s.undo())
    }

    @Test
    fun `import en attente, confirmé puis annulé`() {
        val s = store()
        s.addSeries("Naruto", null)
        val previous = s.series.value
        val imported = listOf(Series(7, "One Piece"), Series(8, "Frieren"))
        s.setPendingImport(imported)
        // Rien n'est appliqué avant la confirmation.
        assertEquals(previous, s.series.value)
        s.confirmImport()
        assertNull(s.pendingImport.value)
        assertEquals(listOf("Frieren", "One Piece"), s.series.value.map { it.title })
        assertEquals(UndoAction.Imported(previous, 2), s.undo.value)
        s.undo()
        assertEquals(previous, s.series.value)
    }

    @Test
    fun `annulation oubliée quand le message disparaît`() {
        val s = store()
        val id = s.addSeries("Naruto", null)
        s.deleteSeries(id)
        val action = s.undo.value!!
        s.forgetUndo(UndoAction.Deleted(Series(99, "Autre")))
        assertEquals(action, s.undo.value)
        s.forgetUndo(action)
        assertNull(s.undo.value)
    }

    @Test
    fun `collection illisible, aucune écriture avant le choix de l'utilisateur`() {
        val file = File(tmp.root, "series.json")
        file.writeText("pas du json")
        val s = store()
        assertTrue(s.unreadable.value)
        s.addSeries("Naruto", null)
        assertEquals("pas du json", file.readText())
        assertEquals(emptyList<Series>(), s.series.value)

        // Restauration par import : pas de « Annuler », rien à quoi revenir.
        s.setPendingImport(listOf(Series(1, "One Piece")))
        s.confirmImport()
        assertFalse(s.unreadable.value)
        assertNull(s.undo.value)
        assertEquals(LoadResult.Ok(listOf(Series(1, "One Piece"))), reload())
    }

    @Test
    fun `collection illisible, repartir de zéro`() {
        File(tmp.root, "series.json").writeText("pas du json")
        val s = store()
        s.startEmpty()
        val id = s.addSeries("Naruto", null)
        assertNotEquals(0L, id)
        assertEquals(LoadResult.Ok(s.series.value), reload())
    }

    @Test
    fun `surveillance activée puis coupée`() {
        val s = store()
        val id = s.addSeries("Naruto", null)
        assertFalse(s.anyWatched)
        assertTrue(s.toggleWatch(id))
        assertTrue(s.anyWatched)
        assertFalse(s.toggleWatch(id))
    }
}
