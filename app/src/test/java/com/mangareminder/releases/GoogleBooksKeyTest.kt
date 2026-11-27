package com.mangareminder.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class GoogleBooksKeyTest {

    private val key = "AIzaSyA1b2C3d4E5f6G7h8I9j0K-L_m2N3o4P5q"

    @Test
    fun `forme de la clé`() {
        assertEquals(key, normalizeApiKey("  $key \n"))
        assertNull(normalizeApiKey(""))
        assertNull(normalizeApiKey("trop-courte"))
        assertNull(normalizeApiKey("x".repeat(101)))
        assertNull(normalizeApiKey("AIzaSy avec des espaces dedans 1234"))
        assertNull(normalizeApiKey("AIzaSyA1b2C3d4E5f6G7h8&q=autre"))
        assertNull(normalizeApiKey("AIzaSyA1b2C3d4E5f6G7h8I9j0é"))
    }

    @Test
    fun `adresse avec ou sans clé`() {
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn%3A9782723489775"
        assertEquals(url, withApiKey(url, null))
        assertEquals("$url&key=$key", withApiKey(url, key))
        // Clé abîmée : requête anonyme plutôt qu'un paramètre injecté.
        assertEquals(url, withApiKey(url, "abc&maxResults=1"))
    }

    @Test
    fun `clé masquée à l'affichage`() {
        assertEquals("…4P5q", maskApiKey(key))
    }

    @Test
    fun `résultat du test de la clé`() {
        assertEquals(KeyTestResult.OK, keyTestResult(null))
        assertEquals(KeyTestResult.REFUSED, keyTestResult(HttpStatusException(400)))
        assertEquals(KeyTestResult.REFUSED, keyTestResult(HttpStatusException(403)))
        assertEquals(KeyTestResult.QUOTA, keyTestResult(HttpStatusException(429)))
        assertEquals(KeyTestResult.NETWORK, keyTestResult(HttpStatusException(503)))
        assertEquals(KeyTestResult.NETWORK, keyTestResult(SocketTimeoutException()))
        assertEquals(KeyTestResult.NETWORK, keyTestResult(IOException()))
    }
}
