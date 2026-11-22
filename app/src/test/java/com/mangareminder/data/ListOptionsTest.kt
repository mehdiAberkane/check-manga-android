package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListOptionsTest {

    // Ids = heure de création : C est la plus récente.
    private val complete = Series(100, "A complète", totalVolumes = 2, ownedVolumes = setOf(1, 2))
    private val holes = Series(300, "B trous", totalVolumes = 10, ownedVolumes = setOf(1), watched = true)
    private val ongoing = Series(200, "C en cours", ownedVolumes = setOf(1, 2, 3), watched = true)
    private val lent = Series(50, "D prêtée", ownedVolumes = setOf(1, 3)).withLoan(1, "Paul", null)
    private val all = listOf(complete, holes, ongoing, lent) // déjà par titre

    private fun titles(filter: ListFilter, sort: ListSort = ListSort.TITLE, new: Set<Long> = emptySet()) =
        applyListOptions(all, filter, sort) { it.id in new }.map { it.title }

    @Test
    fun `filtres`() {
        assertEquals(all.map { it.title }, titles(ListFilter.ALL))
        assertEquals(listOf("B trous", "D prêtée"), titles(ListFilter.INCOMPLETE))
        assertEquals(listOf("A complète"), titles(ListFilter.COMPLETE))
        assertEquals(listOf("B trous", "C en cours"), titles(ListFilter.WATCHED))
        assertEquals(listOf("C en cours"), titles(ListFilter.NEW_RELEASE, new = setOf(200, 100)))
        assertEquals(listOf("D prêtée"), titles(ListFilter.LENT))
    }

    @Test
    fun `tris`() {
        assertEquals(listOf("B trous", "C en cours", "A complète", "D prêtée"), titles(ListFilter.ALL, ListSort.ADDED))
        // B : 9 manquants, D : 1 ; A et C : 0, dans l'ordre des titres.
        assertEquals(listOf("B trous", "D prêtée", "A complète", "C en cours"), titles(ListFilter.ALL, ListSort.MISSING))
    }

    @Test
    fun `filtre et tri combinés`() {
        assertEquals(listOf("B trous", "C en cours"), titles(ListFilter.WATCHED, ListSort.ADDED))
    }

    @Test
    fun `choix mémorisé inconnu`() {
        assertEquals(ListSort.MISSING, enumOrDefault("MISSING", ListSort.TITLE))
        assertEquals(ListSort.TITLE, enumOrDefault("PRIX", ListSort.TITLE))
        assertEquals(ListFilter.ALL, enumOrDefault(null, ListFilter.ALL))
    }
}
