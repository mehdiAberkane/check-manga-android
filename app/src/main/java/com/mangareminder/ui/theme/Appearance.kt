package com.mangareminder.ui.theme

// --- Réglages d'apparence, sans dépendance Android (testés en unitaire). ---

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Couleur principale de l'app quand les couleurs du téléphone ne sont pas utilisées. */
enum class Accent { RED, BLUE, GREEN, PURPLE, ORANGE }

/** Taille des cases de la grille des tomes (largeur minimale d'une case, en dp). */
enum class GridSize(val minDp: Int) { SMALL(48), MEDIUM(60), LARGE(76) }

/** Atténuation maximale du fond d'écran : au-delà, autant ne pas mettre d'image. */
const val MAX_BACKGROUND_DIM = 0.9f
const val DEFAULT_BACKGROUND_DIM = 0.6f

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Couleurs tirées du fond d'écran Android (Material You, Android 12+). */
    val dynamicColor: Boolean = false,
    val accent: Accent = Accent.RED,
    /** En thème sombre, fond noir pur (écrans OLED). */
    val pureBlack: Boolean = false,
    val gridSize: GridSize = GridSize.MEDIUM,
    /** Une image de fond a été choisie (copiée dans les fichiers de l'app). */
    val hasBackground: Boolean = false,
    /** Change à chaque nouvelle image : force son rechargement. */
    val backgroundVersion: Long = 0,
    /** Part du fond uni posée sur l'image, de 0 (image nette) à [MAX_BACKGROUND_DIM]. */
    val backgroundDim: Float = DEFAULT_BACKGROUND_DIM,
)

/** Lit les réglages enregistrés ; une valeur inconnue ou hors bornes reprend sa valeur par défaut. */
fun appearanceFrom(
    getString: (String) -> String?,
    getBoolean: (String) -> Boolean?,
    getLong: (String) -> Long?,
    getFloat: (String) -> Float?,
): AppearanceSettings {
    val defaults = AppearanceSettings()
    return AppearanceSettings(
        themeMode = enumOr(getString("theme"), defaults.themeMode),
        dynamicColor = getBoolean("dynamic") ?: defaults.dynamicColor,
        accent = enumOr(getString("accent"), defaults.accent),
        pureBlack = getBoolean("pure_black") ?: defaults.pureBlack,
        gridSize = enumOr(getString("grid"), defaults.gridSize),
        hasBackground = getBoolean("background") ?: defaults.hasBackground,
        backgroundVersion = getLong("background_version") ?: defaults.backgroundVersion,
        backgroundDim = (getFloat("background_dim") ?: defaults.backgroundDim)
            .takeIf { !it.isNaN() }?.coerceIn(0f, MAX_BACKGROUND_DIM) ?: defaults.backgroundDim,
    )
}

private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/** Le thème sombre s'applique-t-il ? */
fun AppearanceSettings.isDark(systemDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
