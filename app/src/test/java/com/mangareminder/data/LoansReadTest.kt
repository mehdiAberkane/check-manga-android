package com.mangareminder.data

import com.mangareminder.data.SeriesRepository.Companion.fromJson
import com.mangareminder.data.SeriesRepository.Companion.toJson
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class LoansReadTest {

    private val base = Series(1, "Naruto", ownedVolumes = setOf(1, 2, 3))

    @Test
    fun `lu et prêté seulement pour un tome possédé`() {
        val s = base.withRead(2, true).withRead(9, true).withLoan(3, "  Paul ", "2026-11-22").withLoan(9, "Marie", null)
        assertEquals(setOf(2), s.readVolumes)
        assertEquals(mapOf(3 to Loan("Paul", "2026-11-22")), s.loans)
        assertEquals(emptySet<Int>(), s.withRead(2, false).readVolumes)
        assertEquals(emptyMap<Int, Loan>(), s.withoutLoan(3).loans)
    }

    @Test
    fun `nom de prêt vide ou trop long refusé`() {
        assertSame(base, base.withLoan(1, "   ", null))
        assertSame(base, base.withLoan(1, "x".repeat(MAX_BORROWER_LENGTH + 1), null))
        assertEquals(1, base.withLoan(1, "x".repeat(MAX_BORROWER_LENGTH), null).loans.size)
    }

    @Test
    fun `retirer un tome efface aussi sa lecture et son prêt`() {
        val s = base.withRead(2, true).withLoan(2, "Paul", null).withoutVolume(2)
        assertEquals(setOf(1, 3), s.ownedVolumes)
        assertEquals(emptySet<Int>(), s.readVolumes)
        assertEquals(emptyMap<Int, Loan>(), s.loans)
    }

    @Test
    fun `aller-retour dans series json`() {
        val s = base.withRead(1, true).withRead(3, true).withLoan(2, "Paul", "2026-11-22").withLoan(3, "Marie", null)
        assertEquals(listOf(s), fromJson(toJson(listOf(s))))
    }

    @Test
    fun `anciennes sauvegardes sans lecture ni prêts`() {
        val s = fromJson("""[{"id": 1, "title": "Naruto", "owned": [1, 2]}]""").single()
        assertEquals(emptySet<Int>(), s.readVolumes)
        assertEquals(emptyMap<Int, Loan>(), s.loans)
    }

    @Test
    fun `import, statuts de tomes non possédés ignorés, date invalide retirée`() {
        val json = """[{"id": 1, "title": "Naruto", "owned": [1, 2], "read": [1, 5],
            "loans": [{"volume": 2, "to": "Paul", "date": "hier"}, {"volume": 7, "to": "Marie"}]}]"""
        val s = fromJson(json).single()
        assertEquals(setOf(1), s.readVolumes)
        assertEquals(mapOf(2 to Loan("Paul", null)), s.loans)
        assertNull(s.loans.getValue(2).date)
    }

    @Test
    fun `import, valeurs hors bornes refusées`() {
        fun rejects(extra: String) = assertThrows(JSONException::class.java) {
            fromJson("""[{"id": 1, "title": "Naruto", "owned": [1], $extra}]""")
        }
        rejects(""""read": [0]""")
        rejects(""""read": [1000]""")
        rejects(""""loans": [{"volume": 1000, "to": "Paul"}]""")
        rejects(""""loans": [{"volume": 1, "to": "  "}]""")
        rejects(""""loans": [{"volume": 1, "to": "${"x".repeat(MAX_BORROWER_LENGTH + 1)}"}]""")
        rejects(""""loans": [{"volume": 1}]""")
    }
}
