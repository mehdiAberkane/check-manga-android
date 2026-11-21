package com.mangareminder.releases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.URL

class HttpTest {

    private val page = URL("https://www.googleapis.com/books/v1/volumes?q=x")

    @Test
    fun `adresse HTTP refusée sans connexion`() {
        assertThrows(IOException::class.java) { Http.get(URL("http://www.googleapis.com/books/v1/volumes")) }
        assertThrows(IOException::class.java) { Http.requireHttps(URL("ftp://example.com/x")) }
    }

    @Test
    fun `redirection HTTPS suivie, absolue ou relative`() {
        assertEquals(URL("https://books.google.com/x"), Http.redirectTarget(page, "https://books.google.com/x"))
        assertEquals(URL("https://www.googleapis.com/books/v2"), Http.redirectTarget(page, "/books/v2"))
    }

    @Test
    fun `redirection vers HTTP ou sans adresse refusée`() {
        assertThrows(IOException::class.java) { Http.redirectTarget(page, "http://www.googleapis.com/books") }
        assertThrows(IOException::class.java) { Http.redirectTarget(page, null) }
        assertThrows(IOException::class.java) { Http.redirectTarget(page, " ") }
    }
}
