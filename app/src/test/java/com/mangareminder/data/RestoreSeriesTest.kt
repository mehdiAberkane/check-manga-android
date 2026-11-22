package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreSeriesTest {

    private val naruto = Series(1, "Naruto", ownedVolumes = setOf(1, 2), watched = true, cover = null)
        .withRead(1, true)
        .withLoan(2, "Paul", "2026-11-22")
    private val onePiece = Series(2, "One Piece")

    @Test
    fun `la série supprimée revient avec tout son contenu`() {
        val restored = restoreSeries(listOf(onePiece), naruto)
        assertEquals(listOf(onePiece, naruto), restored)
        assertEquals(naruto, restored.last())
    }

    @Test
    fun `pas de doublon si la série est déjà revenue`() {
        assertEquals(listOf(onePiece, naruto), restoreSeries(listOf(onePiece, naruto), naruto))
    }
}
