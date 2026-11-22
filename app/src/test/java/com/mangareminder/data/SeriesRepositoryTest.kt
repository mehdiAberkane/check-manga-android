package com.mangareminder.data

import com.mangareminder.data.SeriesRepository.Companion.MAX_SERIES
import com.mangareminder.data.SeriesRepository.Companion.MAX_TITLE_LENGTH
import com.mangareminder.data.SeriesRepository.Companion.fromJson
import com.mangareminder.data.SeriesRepository.Companion.toJson
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SeriesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        id: Any = 1,
        title: String = "\"Naruto\"",
        total: Any? = 72,
        owned: String = "[1, 2, 3]",
        watched: String? = null,
    ) = buildString {
        append("{\"id\": $id, \"title\": $title, \"owned\": $owned")
        if (total != null) append(", \"total\": $total")
        if (watched != null) append(", \"watched\": $watched")
        append("}")
    }

    private fun rejects(json: String) {
        assertThrows(JSONException::class.java) { fromJson(json) }
    }

    @Test
    fun `aller-retour sans perte`() {
        val series = listOf(
            Series(1, "Naruto", totalVolumes = 72, ownedVolumes = setOf(1, 2, 3), watched = true),
            Series(2, "One Piece", ownedVolumes = setOf(105)),
            Series(3, "Frieren"),
        )
        assertEquals(series, fromJson(toJson(series)))
    }

    @Test
    fun `anciennes sauvegardes sans champ watched ni total`() {
        val s = fromJson("[${entry(total = null)}]").single()
        assertFalse(s.watched)
        assertNull(s.totalVolumes)
        assertEquals(setOf(1, 2, 3), s.ownedVolumes)
    }

    @Test
    fun `titre nettoyé`() {
        assertEquals("Naruto", fromJson("[${entry(title = "\"  Naruto  \"")}]").single().title)
    }

    @Test
    fun `titre vide ou trop long refusé`() {
        rejects("[${entry(title = "\"   \"")}]")
        rejects("[${entry(title = "\"${"x".repeat(MAX_TITLE_LENGTH + 1)}\"")}]")
        assertEquals(1, fromJson("[${entry(title = "\"${"x".repeat(MAX_TITLE_LENGTH)}\"")}]").size)
    }

    @Test
    fun `total hors bornes refusé`() {
        rejects("[${entry(total = 0)}]")
        rejects("[${entry(total = 1000)}]")
        rejects("[${entry(total = 2_000_000_000)}]")
        rejects("[${entry(total = "\"beaucoup\"")}]")
    }

    @Test
    fun `tome hors bornes refusé`() {
        rejects("[${entry(owned = "[0]")}]")
        rejects("[${entry(owned = "[1, 1000]")}]")
        rejects("[${entry(owned = "[-3]")}]")
        rejects("[${entry(owned = "[\"un\"]")}]")
        assertEquals(setOf(999), fromJson("[${entry(owned = "[999]")}]").single().ownedVolumes)
    }

    @Test
    fun `identifiants en double refusés`() {
        rejects("[${entry(id = 1)}, ${entry(id = 1, title = "\"Autre\"")}]")
    }

    @Test
    fun `champs obligatoires et structure`() {
        rejects("""[{"title": "Naruto", "owned": []}]""")
        rejects("""[{"id": 1, "owned": []}]""")
        rejects("""[{"id": 1, "title": "Naruto"}]""")
        rejects("""[{"id": "abc", "title": "Naruto", "owned": []}]""")
        rejects("""{"series": []}""")
        rejects("""["pas un objet"]""")
        rejects("pas du json")
    }

    @Test
    fun `nombre de séries plafonné`() {
        val ok = (1..MAX_SERIES).joinToString(",", "[", "]") { entry(id = it, owned = "[]") }
        assertEquals(MAX_SERIES, fromJson(ok).size)
        val tooMany = (1..MAX_SERIES + 1).joinToString(",", "[", "]") { entry(id = it, owned = "[]") }
        rejects(tooMany)
    }

    @Test
    fun `fichier absent donne une collection vide`() {
        assertEquals(emptyList<Series>(), SeriesRepository(tmp.root).load())
    }

    @Test
    fun `enregistrement relu à l'identique, sans fichier temporaire restant`() {
        val repo = SeriesRepository(tmp.root)
        val series = listOf(Series(1, "Naruto", ownedVolumes = setOf(1, 2)))
        repo.save(series)
        assertEquals(series, repo.load())
        assertFalse(File(tmp.root, "series.json.tmp").exists())
    }

    @Test
    fun `fichier illisible mis de côté, jamais écrasé`() {
        val file = File(tmp.root, "series.json")
        val corrupt = File(tmp.root, "series.json.corrupt")
        file.writeText("[{\"id\": 1, \"title\": \"\", \"owned\": []}]")
        val original = file.readText()

        val repo = SeriesRepository(tmp.root)
        assertEquals(emptyList<Series>(), repo.load())
        assertTrue(corrupt.exists())
        assertEquals(original, corrupt.readText())

        // La sauvegarde suivante remplace series.json, mais la copie mise de côté reste intacte.
        repo.save(listOf(Series(2, "One Piece")))
        assertEquals(original, corrupt.readText())
    }
}
