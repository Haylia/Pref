package com.preferans.scorer.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preferans.scorer.PreferansApp
import com.preferans.scorer.R
import com.preferans.scorer.domain.LanguagePref

/**
 * Cycles SYSTEM → ENGLISH → RUSSIAN → SYSTEM. The current choice is shown as
 * a two-letter tag overlaid on the language icon.
 */
@Composable
fun LanguageToggleButton() {
    val repo = PreferansApp.instance.settingsRepository
    val pref by repo.language.collectAsState()
    val label = when (pref) {
        LanguagePref.SYSTEM -> stringResource(R.string.lang_system)
        LanguagePref.ENGLISH -> stringResource(R.string.lang_english)
        LanguagePref.RUSSIAN -> stringResource(R.string.lang_russian)
    }
    val tag = when (pref) {
        LanguagePref.SYSTEM -> "Auto"
        LanguagePref.ENGLISH -> "EN"
        LanguagePref.RUSSIAN -> "RU"
    }
    IconButton(onClick = { repo.cycleLanguage() }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.Language, contentDescription = label)
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
    }
}
