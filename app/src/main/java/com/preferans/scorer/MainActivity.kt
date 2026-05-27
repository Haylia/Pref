package com.preferans.scorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.preferans.scorer.domain.ThemePref
import com.preferans.scorer.ui.PreferansApp
import com.preferans.scorer.ui.theme.PreferansTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePref by PreferansApp.instance.settingsRepository.theme.collectAsState()
            val isDark = when (themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            PreferansTheme(darkTheme = isDark) {
                PreferansApp()
            }
        }
    }
}
