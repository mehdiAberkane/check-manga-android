package com.mangareminder.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.mangareminder.ui.theme.AppearanceSettings

/**
 * Fond d'écran choisi par l'utilisateur, derrière toutes les pages, atténué par une couche du fond
 * uni du thème. Les pages ont alors un fond transparent ; barres du haut, cartes et dialogues
 * gardent leurs couleurs, pour rester lisibles.
 */
@Composable
fun AppBackground(
    settings: AppearanceSettings,
    loadBackground: suspend () -> ImageBitmap?,
    content: @Composable () -> Unit,
) {
    val image by produceState<ImageBitmap?>(null, settings.hasBackground, settings.backgroundVersion) {
        value = if (settings.hasBackground) loadBackground() else null
    }
    val background = MaterialTheme.colorScheme.background
    val current = image
    if (current == null) {
        content()
        return
    }
    Box(Modifier.fillMaxSize().background(background)) {
        Image(current, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(background.copy(alpha = settings.backgroundDim)))
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Color.Transparent)) {
            content()
        }
    }
}
