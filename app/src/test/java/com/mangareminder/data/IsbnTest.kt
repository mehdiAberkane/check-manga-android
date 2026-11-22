package com.mangareminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnTest {

    @Test
    fun `isbn valides, avec ou sans tirets`() {
        assertEquals("9782723434621", normalizeIsbn("978-2-7234-3462-1"))
        assertEquals("9782723434621", normalizeIsbn(" 9782723434621 "))
        assertEquals("2723434621", normalizeIsbn("2-7234-3462-1"))
        assertEquals("080442957X", normalizeIsbn("0-8044-2957-x"))
    }

    @Test
    fun `codes invalides refusés`() {
        assertNull(normalizeIsbn("9782723434622")) // mauvaise clé
        assertNull(normalizeIsbn("1234567890128")) // EAN qui n'est pas un livre
        assertNull(normalizeIsbn("2723434622"))
        assertNull(normalizeIsbn("abc"))
        assertNull(normalizeIsbn(""))
    }

    @Test
    fun `titre du livre dans la réponse Google Books`() {
        assertEquals(
            "One Piece - Tome 45",
            parseIsbnLookup("""{"items": [{"volumeInfo": {"title": "One Piece", "subtitle": "Tome 45"}}]}"""),
        )
        assertNull(parseIsbnLookup("""{"totalItems": 0}"""))
        assertNull(parseIsbnLookup("<html>"))
    }

    @Test
    fun `tome d'une série de la collection`() {
        val onePiece = Series(1, "One Piece", ownedVolumes = setOf(44))
        val naruto = Series(2, "Naruto")
        val shippuden = Series(3, "Naruto Shippuden")
        val match = matchScannedBook(listOf(onePiece, naruto), "One Piece - Tome 45") as ScanMatch.InCollection
        assertEquals(onePiece, match.series)
        assertEquals(45, match.volume)
        assertFalse(match.owned)
        assertTrue((matchScannedBook(listOf(onePiece), "ONE PIECE T44") as ScanMatch.InCollection).owned)
        // Le titre de série le plus précis l'emporte.
        assertEquals(shippuden, (matchScannedBook(listOf(naruto, shippuden), "Naruto Shippuden Vol. 2") as ScanMatch.InCollection).series)
    }

    @Test
    fun `série inconnue, titre proposé sans le numéro du tome`() {
        assertEquals(ScanMatch.NewSeries("Frieren - Tome 3", "Frieren", 3), matchScannedBook(emptyList(), "Frieren - Tome 3"))
        assertEquals("Blue Lock", suggestSeriesTitle("Blue Lock T12"))
        assertEquals("Dandadan", suggestSeriesTitle("Dandadan 22"))
        assertEquals("L'Attaque des Titans", suggestSeriesTitle("L'Attaque des Titans, vol. 34"))
        assertEquals("Akira", suggestSeriesTitle("Akira"))
    }
}
