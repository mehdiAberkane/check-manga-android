package com.mangareminder.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.URL

class HttpTest {

    private val page = URL("https://www.googleapis.com/books/v1/volumes?q=x")
    private val api = GOOGLE_BOOKS_HOSTS
    private val both = GOOGLE_BOOKS_HOSTS + COVER_HOSTS

    @Test
    fun `adresse HTTP ou hôte hors liste refusés sans connexion`() {
        assertThrows(IOException::class.java) { Http.get(URL("http://www.googleapis.com/books/v1/volumes"), api) }
        assertThrows(IOException::class.java) { Http.get(URL("https://evil.example/books"), api) }
        assertThrows(IOException::class.java) { Http.getBytes(URL("https://www.googleapis.com/x"), 10, COVER_HOSTS) }
        assertThrows(IOException::class.java) { Http.requireHttps(URL("ftp://example.com/x")) }
    }

    @Test
    fun `hôte autorisé, sans identifiants ni port exotique`() {
        assertEquals(page, Http.requireAllowed(page, api))
        assertEquals(URL("https://WWW.googleapis.com/x"), Http.requireAllowed(URL("https://WWW.googleapis.com/x"), api))
        assertThrows(IOException::class.java) { Http.requireAllowed(URL("https://www.googleapis.com.evil.example/x"), api) }
        assertThrows(IOException::class.java) { Http.requireAllowed(URL("https://user@www.googleapis.com/x"), api) }
        assertThrows(IOException::class.java) { Http.requireAllowed(URL("https://www.googleapis.com:8443/x"), api) }
    }

    @Test
    fun `redirection HTTPS vers un hôte autorisé suivie, absolue ou relative`() {
        assertEquals(URL("https://books.google.com/x"), Http.redirectTarget(page, "https://books.google.com/x", both))
        assertEquals(URL("https://www.googleapis.com/books/v2"), Http.redirectTarget(page, "/books/v2", api))
    }

    @Test
    fun `redirection refusée vers HTTP, vers un hôte hors liste ou sans adresse`() {
        assertThrows(IOException::class.java) { Http.redirectTarget(page, "http://www.googleapis.com/books", api) }
        assertThrows(IOException::class.java) { Http.redirectTarget(page, "https://books.google.com/x", api) }
        assertThrows(IOException::class.java) { Http.redirectTarget(page, "https://evil.example/x", both) }
        assertThrows(IOException::class.java) { Http.redirectTarget(page, null, api) }
        assertThrows(IOException::class.java) { Http.redirectTarget(page, " ", api) }
    }
}
