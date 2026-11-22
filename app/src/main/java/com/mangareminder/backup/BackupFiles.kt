package com.mangareminder.backup

import java.time.LocalDate

// --- Noms des fichiers de sauvegarde automatique, sans dépendance Android (testé en unitaire). ---

/** Nombre de versions datées gardées dans le dossier de sauvegarde. */
const val KEPT_BACKUPS = 7

private val BACKUP_NAME = Regex("manga-reminder-(\\d{4}-\\d{2}-\\d{2})\\.json")

/** Un fichier par jour, réécrit à chaque sauvegarde de la journée : manga-reminder-2026-11-22.json. */
fun backupFileName(day: LocalDate): String = "manga-reminder-$day.json"

/**
 * Fichiers à supprimer pour ne garder que les [keep] sauvegardes les plus récentes.
 * Seuls les fichiers nommés exactement comme une sauvegarde automatique sont concernés :
 * le reste du dossier (autres fichiers, exports manuels) n'est jamais touché.
 */
fun backupsToDelete(names: Collection<String>, keep: Int = KEPT_BACKUPS): List<String> =
    names.mapNotNull { name ->
        BACKUP_NAME.matchEntire(name)?.let { match ->
            runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()?.let { name to it }
        }
    }
        .distinctBy { it.first }
        .sortedByDescending { it.second }
        .drop(keep)
        .map { it.first }

/** Sauvegarde automatique la plus récente du dossier, ou null s'il n'y en a pas. */
fun latestBackupName(names: Collection<String>): String? =
    names.mapNotNull { name ->
        BACKUP_NAME.matchEntire(name)?.let { match ->
            runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()?.let { name to it }
        }
    }.maxByOrNull { it.second }?.first
