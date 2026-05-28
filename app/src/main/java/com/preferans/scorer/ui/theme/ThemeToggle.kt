package com.preferans.scorer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.preferans.scorer.PreferansApp
import com.preferans.scorer.R
import com.preferans.scorer.domain.ThemePref

/**
 * Top-bar icon button. Tap cycles SYSTEM → LIGHT → DARK → SYSTEM.
 */
@Composable
fun ThemeToggleButton() {
    val repo = PreferansApp.instance.settingsRepository
    val pref by repo.theme.collectAsState()
    val (icon, labelRes) = when (pref) {
        ThemePref.SYSTEM -> Icons.Default.BrightnessAuto to R.string.theme_system
        ThemePref.LIGHT -> Icons.Default.LightMode to R.string.theme_light
        ThemePref.DARK -> Icons.Default.DarkMode to R.string.theme_dark
    }
    IconButton(onClick = { repo.cycleTheme() }) {
        Icon(imageVector = icon, contentDescription = stringResource(labelRes))
    }
}
