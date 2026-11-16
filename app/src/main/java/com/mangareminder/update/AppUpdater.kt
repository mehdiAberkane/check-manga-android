package com.mangareminder.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.mangareminder.data.Settings
import com.mangareminder.releases.Http
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Version publiée par la CI dans la release GitHub « latest ». */
data class UpdateInfo(val versionCode: Long, val versionName: String)

/**
 * Mises à jour depuis GitHub : la CI publie manga-reminder.apk et version.json dans la release
 * « latest ». Seuls les APK signés avec la clé du projet sont proposés : Android refuserait
 * d'installer les autres par-dessus l'app.
 */
object AppUpdater {
    private const val BASE_URL =
        "https://github.com/mehdiAberkane/check-manga-android/releases/download/latest/"
    private const val MAX_APK_BYTES = 100_000_000
    private const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun currentVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Vérification automatique au plus une fois par jour. */
    fun autoCheckDue(context: Context): Boolean =
        System.currentTimeMillis() - Settings(context).lastUpdateCheck > AUTO_CHECK_INTERVAL_MS

    /**
     * La version publiée si elle est plus récente que celle installée, sinon null.
     * @throws IOException si GitHub ne répond pas ou si version.json est invalide.
     */
    fun fetchNewerVersion(context: Context): UpdateInfo? {
        val text = Http.get(URL(BASE_URL + "version.json"))
        Settings(context).lastUpdateCheck = System.currentTimeMillis()
        val info = try {
            val json = JSONObject(text)
            if (!json.optBoolean("signed", false)) return null
            UpdateInfo(json.getLong("versionCode"), json.getString("versionName"))
        } catch (e: JSONException) {
            throw IOException("version.json invalide", e)
        }
        return info.takeIf { it.versionCode > currentVersionCode(context) }
    }

    /**
     * Télécharge l'APK dans le cache et vérifie qu'il s'agit bien d'une version plus récente
     * de cette app. @throws IOException sinon.
     */
    fun download(context: Context, onProgress: (Float?) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "manga-reminder.apk")
        val connection = URL(BASE_URL + "manga-reminder.apk").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        done += n
                        if (done > MAX_APK_BYTES) throw IOException("APK trop volumineux")
                        onProgress(total?.let { done.toFloat() / it })
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw e
        } finally {
            connection.disconnect()
        }

        @Suppress("DEPRECATION") // La variante avec flags n'existe qu'à partir d'Android 13.
        val archive = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (archive == null || archive.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(archive) <= currentVersionCode(context)
        ) {
            file.delete()
            throw IOException("Le fichier téléchargé n'est pas une mise à jour de Manga Reminder")
        }
        return file
    }

    /**
     * Ouvre l'installeur Android. Renvoie false si l'utilisateur doit d'abord autoriser
     * l'app à installer des applications (l'écran de réglage est alors ouvert).
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
