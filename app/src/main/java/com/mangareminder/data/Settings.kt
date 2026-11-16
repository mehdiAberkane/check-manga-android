package com.mangareminder.data

import android.content.Context

/** Petits réglages de l'app (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Adresse du flux RSS de sorties (manga-news…), vide si non configuré. */
    var rssFeedUrl: String
        get() = prefs.getString(KEY_RSS, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_RSS, value.trim()).apply()

    /** Dernière recherche automatique de mise à jour de l'app. */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK, 0)
        set(value) = prefs.edit().putLong(KEY_UPDATE_CHECK, value).apply()

    companion object {
        private const val KEY_RSS = "rss_feed_url"
        private const val KEY_UPDATE_CHECK = "last_update_check"

        /** Https uniquement : Android bloque le http en clair. */
        fun isValidFeedUrl(url: String): Boolean =
            url.isBlank() || Regex("^https://[^\\s/]+\\.[^\\s]+$").matches(url.trim())
    }
}
