package com.mangareminder.releases

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class CheckFailureTest {

    private val quota = CheckFailure.QUOTA
    private val network = CheckFailure.NETWORK

    @Test
    fun `type d'échec selon l'erreur`() {
        assertEquals(quota, failureOf(HttpStatusException(429)))
        assertEquals(network, failureOf(HttpStatusException(503)))
        assertEquals(network, failureOf(SocketTimeoutException()))
        assertEquals(network, failureOf(IOException("pas de réseau")))
        assertEquals(CheckFailure.INVALID, failureOf(JSONException("illisible")))
    }

    @Test
    fun `jamais de nouvel essai sur un quota épuisé`() {
        assertFalse(shouldRetry(listOf(quota), checked = 1, attempt = 0))
        assertFalse(shouldRetry(listOf(quota, network), checked = 2, attempt = 0))
    }

    @Test
    fun `nouvel essai limité pour une panne réseau qui touche toutes les séries`() {
        assertTrue(shouldRetry(listOf(network, network), checked = 2, attempt = 0))
        assertTrue(shouldRetry(listOf(network), checked = 1, attempt = MAX_CHECK_RETRIES - 1))
        assertFalse(shouldRetry(listOf(network), checked = 1, attempt = MAX_CHECK_RETRIES))
        // Une partie des séries a réussi : rien à réessayer.
        assertFalse(shouldRetry(listOf(network), checked = 2, attempt = 0))
        assertFalse(shouldRetry(emptyList(), checked = 0, attempt = 0))
        assertFalse(shouldRetry(listOf(CheckFailure.INVALID), checked = 1, attempt = 0))
    }

    @Test
    fun `erreur retenue, la plus grave et datée`() {
        assertNull(checkError(emptyList(), 10))
        assertEquals(CheckError(quota, 3, 10), checkError(listOf(network, quota, CheckFailure.INVALID), 10))
        assertEquals(CheckError(network, 2, 20), checkError(listOf(CheckFailure.INVALID, network), 20))
        assertEquals(CheckError(CheckFailure.INVALID, 1, 30), checkError(listOf(CheckFailure.INVALID), 30))
    }
}
