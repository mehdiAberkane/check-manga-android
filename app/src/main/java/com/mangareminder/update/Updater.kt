package com.mangareminder.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.mangareminder.BuildConfig
import com.mangareminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Mise à jour de l'app depuis la dernière release GitHub : recherche, téléchargement, vérifications
 * (taille, SHA-256, paquet, versionCode, certificat), puis installation par PackageInstaller.
 */
class Updater(context: Context) {
    private val context = context.applicationContext
    private val apkFile = File(context.cacheDir, "update/manga-reminder-update.apk")

    /** Dernière version publiée si elle est plus récente que celle installée, sinon null. */
    suspend fun findUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val release = try {
            parseLatestRelease(readText(LATEST_RELEASE_URL, 1_000_000))
        } catch (e: HttpStatusException) {
            if (e.code == 404) throw UpdateException(R.string.update_err_no_release) else throw e
        }
        val versionJson = release.assets.find { it.name == "version.json" }
            ?: throw UpdateException(R.string.update_err_no_version_json, release.tag)
        val info = parseUpdate(release, readText(versionJson.url, 64 * 1024))
        info.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Android demande une autorisation unique pour installer des APK depuis l'app. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Télécharge l'APK dans le cache et le vérifie ; le fichier est supprimé s'il est refusé. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        clean()
        apkFile.parentFile!!.mkdirs()
        try {
            val sha256 = downloadTo(info.apk.url, apkFile, info.apk.size, onProgress)
            if (apkFile.length() != info.apk.size) throw UpdateException(R.string.update_err_incomplete)
            if (sha256 != info.sha256) {
                throw UpdateException(R.string.update_err_corrupt)
            }
            verifyPackage(apkFile, info)
            apkFile
        } catch (e: Throwable) {
            clean()
            throw e
        }
    }

    /** Confie l'APK à Android ; le résultat arrive dans [UpdateInstallReceiver]. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Sans effet la première fois : Android demande confirmation tant que l'app n'est pas
            // elle-même l'installateur enregistré.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    // PackageInstaller complète l'intent avec le statut : il doit être modifiable.
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(
                    context, sessionId, Intent(context, UpdateInstallReceiver::class.java), flags,
                )
                session.commit(callback.intentSender)
            }
        } catch (e: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        } finally {
            // La session a sa propre copie : l'APK du cache ne sert plus.
            clean()
        }
    }

    fun clean() {
        apkFile.parentFile?.deleteRecursively()
    }

    /**
     * Android refuse de lui-même un APK signé par une autre clé ; on le vérifie avant, pour un
     * message clair et pour ne jamais lancer l'installation d'un fichier inattendu.
     */
    private fun verifyPackage(apk: File, info: UpdateInfo) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, signingFlags())
            ?: throw UpdateException(R.string.update_err_not_apk)
        if (archive.packageName != context.packageName) {
            throw UpdateException(R.string.update_err_wrong_package)
        }
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        if (archiveCode != info.versionCode.toLong() || archiveCode <= BuildConfig.VERSION_CODE) {
            throw UpdateException(R.string.update_err_version_mismatch)
        }
        val installed = signerDigests(pm.getPackageInfo(context.packageName, signingFlags()))
        val downloaded = signerDigests(archive)
        if (downloaded.isEmpty()) throw UpdateException(R.string.update_err_signature_unreadable)
        if (downloaded != installed) {
            throw UpdateException(R.string.update_err_wrong_signature)
        }
    }

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun readText(url: String, maxBytes: Int): String = open(url).let { connection ->
        try {
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("Réponse trop volumineuse")
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Écrit le fichier en calculant son SHA-256 au passage ; s'arrête au-delà de la taille annoncée. */
    private suspend fun downloadTo(url: String, dest: File, expectedSize: Long, onProgress: (Float) -> Unit): String {
        val connection = open(url)
        try {
            val length = connection.contentLengthLong
            if (length > MAX_APK_BYTES || (length >= 0 && length != expectedSize)) {
                throw UpdateException(R.string.update_err_unexpected_size)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n == -1) break
                        total += n
                        if (total > expectedSize) throw UpdateException(R.string.update_err_unexpected_size)
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        onProgress(total.toFloat() / expectedSize)
                    }
                }
            }
            return digest.digest().toHex()
        } finally {
            connection.disconnect()
        }
    }

    /** Suit les redirections à la main pour vérifier chaque hôte (HTTPS, GitHub uniquement). */
    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            if (!isAllowedUrl(current)) throw UpdateException(R.string.update_err_url_refused)
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "MangaReminder (Android)")
            if (current.startsWith("https://api.github.com/")) {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    current = URL(URL(current), location ?: throw IOException("Redirection sans adresse")).toString()
                }
                else -> {
                    connection.disconnect()
                    throw HttpStatusException(code)
                }
            }
        }
        throw IOException("Trop de redirections")
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private companion object {
        const val MAX_REDIRECTS = 5
    }
}

private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
