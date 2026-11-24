package com.mangareminder.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {

    private fun read(values: Map<String, Any?>) = appearanceFrom(
        getString = { values[it] as? String },
        getBoolean = { values[it] as? Boolean },
        getLong = { values[it] as? Long },
        getFloat = { values[it] as? Float },
    )

    @Test
    fun `aucun réglage enregistré, valeurs par défaut`() {
        assertEquals(AppearanceSettings(), read(emptyMap()))
    }

    @Test
    fun `réglages relus`() {
        val settings = read(
            mapOf(
                "theme" to "DARK", "dynamic" to true, "accent" to "GREEN", "pure_black" to true,
                "grid" to "LARGE", "background" to true, "background_version" to 42L, "background_dim" to 0.3f,
            ),
        )
        assertEquals(
            AppearanceSettings(ThemeMode.DARK, true, Accent.GREEN, true, GridSize.LARGE, true, 42L, 0.3f),
            settings,
        )
    }

    @Test
    fun `valeur inconnue ou hors bornes, valeur par défaut ou bornée`() {
        val settings = read(mapOf("theme" to "NEON", "accent" to "", "grid" to "XXL", "background_dim" to 5f))
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(Accent.RED, settings.accent)
        assertEquals(GridSize.MEDIUM, settings.gridSize)
        assertEquals(MAX_BACKGROUND_DIM, settings.backgroundDim)
        assertEquals(0f, read(mapOf("background_dim" to -1f)).backgroundDim)
        assertEquals(DEFAULT_BACKGROUND_DIM, read(mapOf("background_dim" to Float.NaN)).backgroundDim)
    }

    @Test
    fun `thème sombre selon le mode`() {
        assertTrue(AppearanceSettings(themeMode = ThemeMode.SYSTEM).isDark(systemDark = true))
        assertFalse(AppearanceSettings(themeMode = ThemeMode.SYSTEM).isDark(systemDark = false))
        assertTrue(AppearanceSettings(themeMode = ThemeMode.DARK).isDark(systemDark = false))
        assertFalse(AppearanceSettings(themeMode = ThemeMode.LIGHT).isDark(systemDark = true))
    }
}
