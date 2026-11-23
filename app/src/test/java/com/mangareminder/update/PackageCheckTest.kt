package com.mangareminder.update

import com.mangareminder.R
import com.mangareminder.UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageCheckTest {

    private val key = setOf("df049f29")

    private fun check(
        packageName: String = "com.mangareminder",
        versionCode: Long = 6,
        releaseCode: Int = 6,
        installedCode: Long = 5,
        downloaded: Set<String> = key,
        installed: Set<String> = key,
    ) = checkDownloadedPackage(packageName, "com.mangareminder", versionCode, releaseCode, installedCode, downloaded, installed)

    @Test
    fun `apk attendu accepté`() {
        assertNull(check())
    }

    @Test
    fun `autre application refusée`() {
        assertEquals(UiMessage(R.string.update_err_wrong_package), check(packageName = "com.evil"))
    }

    @Test
    fun `version différente de la release ou retour en arrière refusés`() {
        val mismatch = UiMessage(R.string.update_err_version_mismatch)
        assertEquals(mismatch, check(versionCode = 7))
        assertEquals(mismatch, check(versionCode = 5, releaseCode = 5))
        assertEquals(mismatch, check(versionCode = 4, releaseCode = 4))
    }

    @Test
    fun `signature illisible ou autre clé refusées`() {
        assertEquals(UiMessage(R.string.update_err_signature_unreadable), check(downloaded = emptySet()))
        assertEquals(UiMessage(R.string.update_err_wrong_signature), check(downloaded = setOf("autre")))
        assertEquals(UiMessage(R.string.update_err_wrong_signature), check(downloaded = key + "autre"))
    }
}
