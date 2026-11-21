package com.mangareminder.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private val sha = "a".repeat(64)
    private val base = "https://github.com/$UPDATE_REPO/releases/download/v1.4"

    private fun releaseJson(
        tag: String = "v1.4",
        apkUrl: String = "$base/manga-reminder-1.4.apk",
        digest: String = "sha256:$sha",
        prerelease: Boolean = false,
    ) = """
        {"tag_name": "$tag", "prerelease": $prerelease, "draft": false, "body": "Nouveautés",
         "assets": [
           {"name": "manga-reminder-1.4.apk", "browser_download_url": "$apkUrl", "size": 1234, "digest": "$digest"},
           {"name": "version.json", "browser_download_url": "$base/version.json", "size": 200}
         ]}
    """.trimIndent()

    private fun versionJson(
        code: Int = 5,
        name: String = "1.4",
        apk: String = "manga-reminder-1.4.apk",
        sha256: String = sha,
    ) = """{"versionCode": $code, "versionName": "$name", "apk": "$apk", "sha256": "$sha256", "commit": "abc"}"""

    @Test
    fun `hôtes autorisés`() {
        assertTrue(isAllowedUrl(LATEST_RELEASE_URL))
        assertTrue(isAllowedUrl("$base/version.json"))
        assertTrue(isAllowedUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sig=x"))
        assertFalse(isAllowedUrl("http://github.com/$UPDATE_REPO"))
        assertFalse(isAllowedUrl("https://github.com.evil.example/x.apk"))
        assertFalse(isAllowedUrl("https://user@github.com/x.apk"))
        assertFalse(isAllowedUrl("https://github.com:8443/x.apk"))
        assertFalse(isAllowedUrl("pas une url"))
    }

    @Test
    fun `release lue avec ses fichiers et leurs empreintes`() {
        val release = parseLatestRelease(releaseJson())
        assertEquals("v1.4", release.tag)
        assertEquals("Nouveautés", release.notes)
        assertEquals(listOf("manga-reminder-1.4.apk", "version.json"), release.assets.map { it.name })
        assertEquals(sha, release.assets[0].sha256)
        assertNull(release.assets[1].sha256)
    }

    @Test
    fun `fichiers hors du dépôt ignorés`() {
        val release = parseLatestRelease(releaseJson(apkUrl = "https://github.com/autre/depot/releases/download/v1.4/x.apk"))
        assertEquals(listOf("version.json"), release.assets.map { it.name })
    }

    @Test
    fun `pre-release et JSON invalide refusés`() {
        assertThrows(UpdateException::class.java) { parseLatestRelease(releaseJson(prerelease = true)) }
        assertThrows(UpdateException::class.java) { parseLatestRelease("<html>") }
    }

    @Test
    fun `mise à jour cohérente`() {
        val info = parseUpdate(parseLatestRelease(releaseJson()), versionJson())
        assertEquals(5, info.versionCode)
        assertEquals("1.4", info.versionName)
        assertEquals("manga-reminder-1.4.apk", info.apk.name)
        assertEquals(sha, info.sha256)
    }

    @Test
    fun `empreinte en majuscules acceptée`() {
        val info = parseUpdate(parseLatestRelease(releaseJson()), versionJson(sha256 = sha.uppercase()))
        assertEquals(sha, info.sha256)
    }

    @Test
    fun `incohérences refusées`() {
        val release = parseLatestRelease(releaseJson())
        // Empreinte de version.json différente de celle calculée par GitHub.
        assertThrows(UpdateException::class.java) { parseUpdate(release, versionJson(sha256 = "b".repeat(64))) }
        // Tag et versionName qui ne correspondent pas.
        assertThrows(UpdateException::class.java) { parseUpdate(release, versionJson(name = "1.5")) }
        // APK absent de la release.
        assertThrows(UpdateException::class.java) { parseUpdate(release, versionJson(apk = "autre.apk")) }
        // Pas un APK.
        assertThrows(UpdateException::class.java) { parseUpdate(release, versionJson(apk = "version.json")) }
        // Champs manquants ou invalides.
        assertThrows(UpdateException::class.java) { parseUpdate(release, versionJson(code = 0)) }
        assertThrows(UpdateException::class.java) { parseUpdate(release, versionJson(sha256 = "123")) }
        assertThrows(UpdateException::class.java) { parseUpdate(release, "{}") }
        assertThrows(UpdateException::class.java) { parseUpdate(release, "pas du json") }
    }

    @Test
    fun `sans empreinte GitHub, version json fait foi`() {
        val info = parseUpdate(parseLatestRelease(releaseJson(digest = "")), versionJson())
        assertEquals(sha, info.sha256)
    }
}
