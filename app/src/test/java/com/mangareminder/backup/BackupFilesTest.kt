package com.mangareminder.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupFilesTest {

    @Test
    fun `un fichier par jour`() {
        assertEquals("manga-reminder-2026-11-22.json", backupFileName(LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun `seules les sauvegardes au-delà des plus récentes sont supprimées`() {
        val days = (1..10).map { backupFileName(LocalDate.of(2026, 11, it)) }
        assertEquals(
            listOf("manga-reminder-2026-11-03.json", "manga-reminder-2026-11-02.json", "manga-reminder-2026-11-01.json"),
            backupsToDelete(days.shuffled()),
        )
        assertEquals(emptyList<String>(), backupsToDelete(days.take(KEPT_BACKUPS)))
        assertEquals(listOf("manga-reminder-2026-11-01.json"), backupsToDelete(days.take(3), keep = 2))
    }

    @Test
    fun `les autres fichiers du dossier ne sont jamais touchés`() {
        val others = listOf(
            "manga-reminder-sauvegarde.json",
            "manga-reminder-2026-11-22.json.bak",
            "manga-reminder-2026-13-40.json",
            "copie manga-reminder-2026-11-01.json",
            "manga-reminder-2026-11-01.JSON",
            "notes.txt",
        )
        assertEquals(emptyList<String>(), backupsToDelete(others, keep = 0))
        // Le seul fichier valide de la liste, avec keep = 0, est supprimé ; le reste non.
        assertEquals(
            listOf("manga-reminder-2026-11-02.json"),
            backupsToDelete(others + "manga-reminder-2026-11-02.json", keep = 0),
        )
    }

    @Test
    fun `sauvegarde la plus récente`() {
        val names = listOf("manga-reminder-2026-11-20.json", "notes.txt", "manga-reminder-2026-11-22.json", "manga-reminder-2026-11-21.json")
        assertEquals("manga-reminder-2026-11-22.json", latestBackupName(names))
        assertEquals(null, latestBackupName(listOf("notes.txt")))
    }
}
