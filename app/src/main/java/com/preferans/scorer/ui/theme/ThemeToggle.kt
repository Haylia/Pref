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
import com.preferans.scorer.PreferansApp
import com.preferans.scorer.domain.ThemePref

/**
 * Top-bar icon button. Tap cycles SYSTEM → LIGHT → DARK → SYSTEM.
 * The icon reflects the *current* preference (sun for LIGHT, moon for DARK,
 * auto for SYSTEM).
 */
@Composable
fun ThemeToggleButton() {
    val repo = PreferansApp.instance.settingsRepository
    val pref by repo.theme.collectAsState()
    IconButton(onClick = { repo.cycleTheme() }) {
        val (icon, label) = when (pref) {
            ThemePref.SYSTEM -> Icons.Default.BrightnessAuto to "Theme: System"
            ThemePref.LIGHT -> Icons.Default.LightMode to "Theme: Light"
            ThemePref.DARK -> Icons.Default.DarkMode to "Theme: Dark"
        }
        Icon(imageVector = icon, contentDescription = label)
    }
}
