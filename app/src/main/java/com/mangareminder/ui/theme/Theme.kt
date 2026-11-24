package com.mangareminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Couleurs d'une teinte : principale, texte dessus, conteneur, texte dessus, secondaire. */
private class Palette(
    val primary: Long, val onPrimary: Long, val container: Long, val onContainer: Long, val secondary: Long,
)

private fun lightPalette(accent: Accent) = when (accent) {
    Accent.RED -> Palette(0xFFC62828, 0xFFFFFFFF, 0xFFFFDAD5, 0xFF410001, 0xFF775651)
    Accent.BLUE -> Palette(0xFF1565C0, 0xFFFFFFFF, 0xFFD6E3FF, 0xFF001B3E, 0xFF555F71)
    Accent.GREEN -> Palette(0xFF2E7D32, 0xFFFFFFFF, 0xFFB7F1B0, 0xFF002204, 0xFF52634F)
    Accent.PURPLE -> Palette(0xFF6A1B9A, 0xFFFFFFFF, 0xFFF3DAFF, 0xFF2C0050, 0xFF665A6F)
    Accent.ORANGE -> Palette(0xFFE65100, 0xFFFFFFFF, 0xFFFFDBCB, 0xFF351000, 0xFF765749)
}

private fun darkPalette(accent: Accent) = when (accent) {
    Accent.RED -> Palette(0xFFFFB4A9, 0xFF690002, 0xFF930005, 0xFFFFDAD5, 0xFFE7BDB6)
    Accent.BLUE -> Palette(0xFFAAC7FF, 0xFF002F64, 0xFF00468C, 0xFFD6E3FF, 0xFFBDC7DC)
    Accent.GREEN -> Palette(0xFF9CD596, 0xFF00390A, 0xFF0E5216, 0xFFB7F1B0, 0xFFB9CCB4)
    Accent.PURPLE -> Palette(0xFFE1B6FF, 0xFF4A0080, 0xFF63289B, 0xFFF3DAFF, 0xFFD0C1DA)
    Accent.ORANGE -> Palette(0xFFFFB692, 0xFF562000, 0xFF7A3000, 0xFFFFDBCB, 0xFFE6BEAD)
}

/** Couleur d'aperçu d'une teinte (pastilles de la page Apparence). */
fun accentPreview(accent: Accent): Color = Color(lightPalette(accent).primary)

private fun lightScheme(accent: Accent): ColorScheme = lightPalette(accent).let {
    lightColorScheme(
        primary = Color(it.primary), onPrimary = Color(it.onPrimary),
        primaryContainer = Color(it.container), onPrimaryContainer = Color(it.onContainer),
        secondary = Color(it.secondary),
        background = Color(0xFFFFFBFF), surface = Color(0xFFFFFBFF),
    )
}

private fun darkScheme(accent: Accent): ColorScheme = darkPalette(accent).let {
    darkColorScheme(
        primary = Color(it.primary), onPrimary = Color(it.onPrimary),
        primaryContainer = Color(it.container), onPrimaryContainer = Color(it.onContainer),
        secondary = Color(it.secondary),
    )
}

/** Fond noir pur pour les écrans OLED ; les cartes restent légèrement plus claires pour se détacher. */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
)

/** Les couleurs du téléphone (Material You) n'existent qu'à partir d'Android 12. */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun MangaReminderTheme(settings: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
    val dark = settings.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && dynamicColorAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkScheme(settings.accent)
        else -> lightScheme(settings.accent)
    }
    if (dark && settings.pureBlack) scheme = scheme.pureBlack()
    MaterialTheme(colorScheme = scheme, content = content)
}
