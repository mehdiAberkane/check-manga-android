package com.mangareminder.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.mangareminder.ui.theme.Accent
import com.mangareminder.ui.theme.AppearanceSettings
import com.mangareminder.ui.theme.GridSize
import com.mangareminder.ui.theme.MAX_BACKGROUND_DIM
import com.mangareminder.ui.theme.ThemeMode
import com.mangareminder.ui.theme.appearanceFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Réglages d'apparence, dans des préférences privées (hors collection et hors sauvegarde Android).
 * L'image de fond est copiée et réduite dans les fichiers de l'app : l'original peut être supprimé
 * de la galerie sans conséquence.
 */
class AppearanceViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("appearance", 0)
    private val backgroundFile = File(app.filesDir, "appearance/background.jpg")

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    private fun read() = appearanceFrom(
        getString = { prefs.getString(it, null) },
        getBoolean = { key -> if (prefs.contains(key)) prefs.getBoolean(key, false) else null },
        getLong = { key -> if (prefs.contains(key)) prefs.getLong(key, 0) else null },
        getFloat = { key -> if (prefs.contains(key)) prefs.getFloat(key, 0f) else null },
    ).let { it.copy(hasBackground = it.hasBackground && backgroundFile.exists()) }

    private fun change(transform: (AppearanceSettings) -> AppearanceSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit {
            putString("theme", s.themeMode.name)
            putBoolean("dynamic", s.dynamicColor)
            putString("accent", s.accent.name)
            putBoolean("pure_black", s.pureBlack)
            putString("grid", s.gridSize.name)
            putBoolean("background", s.hasBackground)
            putLong("background_version", s.backgroundVersion)
            putFloat("background_dim", s.backgroundDim)
        }
    }

    fun setThemeMode(mode: ThemeMode) = change { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = change { it.copy(dynamicColor = enabled) }
    fun setAccent(accent: Accent) = change { it.copy(accent = accent) }
    fun setPureBlack(enabled: Boolean) = change { it.copy(pureBlack = enabled) }
    fun setGridSize(size: GridSize) = change { it.copy(gridSize = size) }
    fun setBackgroundDim(dim: Float) = change { it.copy(backgroundDim = dim.coerceIn(0f, MAX_BACKGROUND_DIM)) }

    /**
     * Copie l'image choisie, réduite (côté le plus long : [MAX_BACKGROUND_SIDE] px) et en JPEG.
     * @return false si l'image n'a pas pu être lue.
     */
    suspend fun setBackground(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            var sample = 1
            while (longest / (sample * 2) >= MAX_BACKGROUND_SIDE) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull() ?: return@withContext false
        val saved = runCatching {
            backgroundFile.parentFile?.mkdirs()
            val tmp = File(backgroundFile.parentFile, "background.jpg.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(backgroundFile) || (backgroundFile.delete() && tmp.renameTo(backgroundFile))
        }.getOrDefault(false)
        bitmap.recycle()
        if (saved) change { it.copy(hasBackground = true, backgroundVersion = System.currentTimeMillis()) }
        saved
    }

    fun removeBackground() {
        backgroundFile.delete()
        change { it.copy(hasBackground = false) }
    }

    /** Image de fond prête à afficher, ou null. */
    suspend fun loadBackground(): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!backgroundFile.exists()) return@withContext null
        runCatching { BitmapFactory.decodeFile(backgroundFile.path)?.asImageBitmap() }.getOrNull()
    }

    private companion object {
        const val MAX_BACKGROUND_SIDE = 1_600
    }
}
