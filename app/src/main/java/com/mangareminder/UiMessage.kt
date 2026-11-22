package com.mangareminder

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Texte à afficher, produit loin de l'écran (mise à jour, sorties) sans accès aux ressources :
 * il ne garde que l'identifiant du texte de strings.xml et ses arguments, résolus à l'affichage.
 */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes res: Int, vararg args: Any) : this(res, args.toList())

    @Composable
    fun text(): String = stringResource(res, *args.toTypedArray())

    fun text(context: Context): String = context.getString(res, *args.toTypedArray())
}
