package com.mangareminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690002),
    primaryContainer = Color(0xFF930005),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB6),
)

@Composable
fun MangaReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
