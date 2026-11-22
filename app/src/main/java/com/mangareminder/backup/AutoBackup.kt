package com.mangareminder.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mangareminder.R
import com.mangareminder.UiMessage
import com.mangareminder.data.SeriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** État de la sauvegarde automatique, affiché dans le menu. */
data class BackupStatus(
    /** Dossier choisi (arborescence SAF), ou null si la sauvegarde automatique est désactivée. */
    val folder: Uri? = null,
    val folderName: String? = null,
    /** Heure de la dernière sauvegarde réussie (ms), 0 si aucune. */
    val lastSuccess: Long = 0,
    /** Erreur de la dernière tentative, effacée par la suivante qui réussit. */
    val lastError: UiMessage? = null,
)

/**
 * Sauvegarde automatique de la collection dans un dossier choisi par l'utilisateur
 * (Storage Access Framework : stockage du téléphone, Google Drive…).
 * Réglages dans des préférences privées, hors sauvegarde Android : l'accès au dossier ne
 * vaut que sur ce téléphone.
 */
object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_LAST_SUCCESS = "last_success"
    private const val WORK = "auto-backup"

    /** Plusieurs modifications rapprochées ne donnent qu'une écriture. */
    private const val DELAY_SECONDS = 30L

    private val _status = MutableStateFlow(BackupStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun init(context: Context) {
        val prefs = prefs(context)
        val folder = prefs.getString(KEY_FOLDER, null)?.toUri()
        _status.value = BackupStatus(
            folder = folder,
            folderName = folder?.let { folderName(it) },
            lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0),
        )
    }

    /** Dossier choisi : on garde l'accès après redémarrage, puis on sauvegarde tout de suite. */
    fun enable(context: Context, folder: Uri) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(folder, flags)
        _status.value.folder?.takeIf { it != folder }?.let { old -> releaseAccess(context, old) }
        prefs(context).edit { putString(KEY_FOLDER, folder.toString()) }
        _status.value = BackupStatus(folder = folder, folderName = folderName(folder))
        backupNow(context)
    }

    fun disable(context: Context) {
        _status.value.folder?.let { releaseAccess(context, it) }
        prefs(context).edit { remove(KEY_FOLDER).remove(KEY_LAST_SUCCESS) }
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        _status.value = BackupStatus()
    }

    /** Après une modification de la collection : sauvegarde un peu plus tard, si elle est activée. */
    fun scheduleAfterChange(context: Context) {
        if (_status.value.folder == null) return
        enqueue(context, DELAY_SECONDS)
    }

    fun backupNow(context: Context) = enqueue(context, 0)

    private fun enqueue(context: Context, delaySeconds: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
        )
    }

    /**
     * Écrit la sauvegarde du jour puis supprime les plus anciennes.
     * Une collection vide ou illisible n'est jamais sauvegardée : elle écraserait de bonnes versions.
     */
    internal suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        val folder = _status.value.folder ?: return@withContext true
        val json = try {
            val series = SeriesRepository.fromJson(SeriesRepository(context).readRaw() ?: return@withContext true)
            if (series.isEmpty()) return@withContext true
            SeriesRepository.toJson(series)
        } catch (e: JSONException) {
            return@withContext true
        }
        try {
            val folderDoc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val children = listChildren(context, folder)
            val name = backupFileName(LocalDate.now())
            val target = children[name]
                ?: DocumentsContract.createDocument(context.contentResolver, folderDoc, "application/json", name)
                ?: throw IOException("Création impossible")
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toByteArray()) }
                ?: throw IOException("Écriture impossible")
            backupsToDelete(children.keys + name).forEach { old ->
                children[old]?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            }
            val now = System.currentTimeMillis()
            prefs(context).edit { putLong(KEY_LAST_SUCCESS, now) }
            _status.value = _status.value.copy(lastSuccess = now, lastError = null)
            true
        } catch (e: SecurityException) {
            // Accès au dossier retiré (dossier supprimé, compte déconnecté…) : il faut en choisir un autre.
            _status.value = _status.value.copy(lastError = UiMessage(R.string.backup_error_access))
            true
        } catch (e: Exception) {
            _status.value = _status.value.copy(lastError = UiMessage(R.string.backup_error_write))
            false
        }
    }

    /**
     * Contenu de la sauvegarde automatique la plus récente : (nom du fichier, texte), ou null
     * (pas de dossier, dossier vide ou inaccessible). Lecture seule.
     */
    suspend fun readLatest(context: Context): Pair<String, String>? = withContext(Dispatchers.IO) {
        val folder = _status.value.folder ?: return@withContext null
        runCatching {
            val children = listChildren(context, folder)
            val name = latestBackupName(children.keys) ?: return@runCatching null
            val text = context.contentResolver.openInputStream(children.getValue(name))?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > SeriesRepository.MAX_BACKUP_BYTES) null else bytes.toString(Charsets.UTF_8)
            } ?: return@runCatching null
            name to text
        }.getOrNull()
    }

    /** Fichiers du dossier : nom -> adresse du document. */
    private fun listChildren(context: Context, folder: Uri): Map<String, Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val result = mutableMapOf<String, Uri>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                result[name] = DocumentsContract.buildDocumentUriUsingTree(folder, id)
            }
        }
        return result
    }

    private fun releaseAccess(context: Context, folder: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** « Download », « Mon Drive/Mangas »… : la fin de l'identifiant du dossier, lisible par l'utilisateur. */
    private fun folderName(folder: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(folder).substringAfter(':').ifBlank { null } }
            .getOrNull() ?: folder.lastPathSegment.orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AutoBackup.init(applicationContext)
        // Une erreur d'écriture passagère (réseau de Drive…) est retentée ; un accès retiré non.
        return if (AutoBackup.run(applicationContext) || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
