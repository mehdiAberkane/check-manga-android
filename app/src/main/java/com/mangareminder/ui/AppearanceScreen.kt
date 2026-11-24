@file:OptIn(ExperimentalMaterial3Api::class)

package com.mangareminder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.ui.theme.Accent
import com.mangareminder.ui.theme.AppearanceSettings
import com.mangareminder.ui.theme.GridSize
import com.mangareminder.ui.theme.MAX_BACKGROUND_DIM
import com.mangareminder.ui.theme.ThemeMode
import com.mangareminder.ui.theme.accentPreview
import com.mangareminder.ui.theme.dynamicColorAvailable
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Page « Apparence » : thème, couleurs, taille de la grille, fond d'écran. */
@Composable
fun AppearanceScreen(
    settings: AppearanceSettings,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAccent: (Accent) -> Unit,
    onGridSize: (GridSize) -> Unit,
    onPickBackground: suspend (Uri) -> Boolean,
    onRemoveBackground: () -> Unit,
    onBackgroundDim: (Float) -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.appearance_background_failed)
    // Sélecteur de photos d'Android : aucune permission, seule l'image choisie est accessible.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { if (!onPickBackground(uri)) onError(failed) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(R.string.appearance_theme)
            listOf(
                ThemeMode.SYSTEM to R.string.appearance_theme_system,
                ThemeMode.LIGHT to R.string.appearance_theme_light,
                ThemeMode.DARK to R.string.appearance_theme_dark,
            ).forEach { (mode, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.themeMode == mode, role = Role.RadioButton) { onThemeMode(mode) },
                ) {
                    RadioButton(selected = settings.themeMode == mode, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                }
            }
            SwitchRow(R.string.appearance_pure_black, R.string.appearance_pure_black_hint, settings.pureBlack, onPureBlack)

            SectionTitle(R.string.appearance_colors)
            if (dynamicColorAvailable) {
                SwitchRow(R.string.appearance_dynamic, R.string.appearance_dynamic_hint, settings.dynamicColor, onDynamicColor)
            }
            val accentEnabled = !(settings.dynamicColor && dynamicColorAvailable)
            Text(
                stringResource(R.string.appearance_accent),
                style = MaterialTheme.typography.bodyLarge,
                color = if (accentEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accent.entries.forEach { accent ->
                    AccentDot(accent, selected = settings.accent == accent, enabled = accentEnabled) { onAccent(accent) }
                }
            }

            SectionTitle(R.string.appearance_grid)
            val sizes = listOf(
                GridSize.SMALL to R.string.appearance_grid_small,
                GridSize.MEDIUM to R.string.appearance_grid_medium,
                GridSize.LARGE to R.string.appearance_grid_large,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                sizes.forEachIndexed { index, (size, label) ->
                    SegmentedButton(
                        selected = settings.gridSize == size,
                        onClick = { onGridSize(size) },
                        shape = SegmentedButtonDefaults.itemShape(index, sizes.size),
                    ) { Text(stringResource(label)) }
                }
            }

            SectionTitle(R.string.appearance_background)
            Text(
                stringResource(R.string.appearance_background_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(stringResource(if (settings.hasBackground) R.string.appearance_background_change else R.string.appearance_background_choose))
                }
                if (settings.hasBackground) {
                    TextButton(onClick = onRemoveBackground) { Text(stringResource(R.string.appearance_background_remove)) }
                }
            }
            if (settings.hasBackground) {
                Text(
                    stringResource(R.string.appearance_background_dim, (settings.backgroundDim * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = settings.backgroundDim,
                    onValueChange = onBackgroundDim,
                    valueRange = 0f..MAX_BACKGROUND_DIM,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SwitchRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AccentDot(accent: Accent, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val name = stringResource(
        when (accent) {
            Accent.RED -> R.string.accent_red
            Accent.BLUE -> R.string.accent_blue
            Accent.GREEN -> R.string.accent_green
            Accent.PURPLE -> R.string.accent_purple
            Accent.ORANGE -> R.string.accent_orange
        },
    )
    val color = accentPreview(accent).let { if (enabled) it else it.copy(alpha = 0.35f) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            // Nom de la couleur lu par TalkBack, que la pastille soit choisie ou non.
            .semantics { contentDescription = name },
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
    }
}
