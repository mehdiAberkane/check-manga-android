package com.mangareminder.update

import androidx.annotation.StringRes
import com.mangareminder.R
import com.mangareminder.UiMessage
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/** Dépôt public dont les releases taguées `vX.Y` sont les versions de l'app. */
const val UPDATE_REPO = "mehdiAberkane/check-manga-android"

const val LATEST_RELEASE_URL = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"

/** Un APK de l'app fait quelques Mo : au-delà, la release est suspecte. */
const val MAX_APK_BYTES = 50L * 1024 * 1024

const val MAX_NOTES_CHARS = 2_000

private const val ASSET_URL_PREFIX = "https://github.com/$UPDATE_REPO/releases/download/"

/**
 * Seuls hôtes contactés : l'API GitHub, github.com (liens des fichiers de release) et les
 * serveurs de fichiers vers lesquels github.com redirige les téléchargements.
 */
private val ALLOWED_HOSTS = setOf(
    "api.github.com",
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
)

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/** Erreur à afficher à l'utilisateur ; le texte est dans strings.xml. */
class UpdateException(val uiMessage: UiMessage) : Exception("Mise à jour refusée : $uiMessage") {
    constructor(@StringRes res: Int, vararg args: Any) : this(UiMessage(res, args.toList()))
}

data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String?)

data class LatestRelease(val tag: String, val notes: String, val assets: List<ReleaseAsset>)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    val notes: String,
    val apk: ReleaseAsset,
    val sha256: String,
)

/** HTTPS, sans identifiants ni port exotique, vers un hôte GitHub connu. */
fun isAllowedUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host in ALLOWED_HOSTS && uri.userInfo == null && uri.port in setOf(-1, 443)
}.getOrDefault(false)

/**
 * Lit la réponse de `GET /repos/{repo}/releases/latest`. Les fichiers dont le lien ne pointe pas
 * vers les releases du dépôt sont ignorés.
 */
fun parseLatestRelease(json: String): LatestRelease {
    try {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) {
            throw UpdateException(R.string.update_err_not_stable)
        }
        val tag = root.getString("tag_name")
        val notes = root.optString("body").take(MAX_NOTES_CHARS)
        val array = root.optJSONArray("assets")
        val assets = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val asset = array!!.getJSONObject(i)
            val url = asset.optString("browser_download_url")
            if (!url.startsWith(ASSET_URL_PREFIX) || !isAllowedUrl(url)) return@mapNotNull null
            ReleaseAsset(
                name = asset.getString("name"),
                url = url,
                size = asset.optLong("size", -1),
                // GitHub calcule l'empreinte de chaque fichier ("sha256:<hex>") : second avis sur version.json.
                sha256 = asset.optString("digest").removePrefix("sha256:").lowercase().takeIf { it.matches(SHA256_HEX) },
            )
        }
        return LatestRelease(tag, notes, assets)
    } catch (e: JSONException) {
        throw UpdateException(R.string.update_err_github_unreadable)
    }
}

/**
 * Croise la release et son `version.json` ; refuse toute incohérence plutôt que de deviner.
 */
fun parseUpdate(release: LatestRelease, versionJson: String): UpdateInfo {
    val json = try {
        JSONObject(versionJson)
    } catch (e: JSONException) {
        throw UpdateException(R.string.update_err_version_json_unreadable)
    }
    val versionCode = json.optInt("versionCode", -1)
    val versionName = json.optString("versionName")
    val apkName = json.optString("apk")
    val sha256 = json.optString("sha256").lowercase()

    if (versionCode <= 0 || versionName.isBlank() || versionName.length > 30) {
        throw UpdateException(R.string.update_err_version_json_incomplete)
    }
    if (release.tag != "v$versionName") {
        throw UpdateException(R.string.update_err_tag_mismatch, release.tag, versionName)
    }
    if (!sha256.matches(SHA256_HEX)) throw UpdateException(R.string.update_err_no_sha)
    val apk = release.assets.find { it.name == apkName && it.name.endsWith(".apk") }
        ?: throw UpdateException(R.string.update_err_apk_missing, release.tag)
    if (apk.size !in 1..MAX_APK_BYTES) throw UpdateException(R.string.update_err_apk_size)
    if (apk.sha256 != null && apk.sha256 != sha256) {
        throw UpdateException(R.string.update_err_digest_mismatch)
    }
    return UpdateInfo(versionCode, versionName, release.tag, release.notes, apk, sha256)
}
