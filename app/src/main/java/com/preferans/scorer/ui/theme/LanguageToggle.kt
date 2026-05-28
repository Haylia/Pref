package com.preferans.scorer.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.preferans.scorer.PreferansApp
import com.preferans.scorer.R
import com.preferans.scorer.domain.discoverAppLanguages
import com.preferans.scorer.ui.WithCurrentLocale

/**
 * Top-bar icon button. Tap opens a dialog listing every language bundled in
 * the APK; the user picks one and the preference is saved.
 *
 * The list is auto-discovered from the APK's resources, so a new language
 * only needs a `res/values-XX/strings.xml` to show up.
 */
@Composable
fun LanguageToggleButton() {
    val repo = PreferansApp.instance.settingsRepository
    val currentTag by repo.languageTag.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    IconButton(onClick = { showPicker = true }) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = stringResource(R.string.lang_picker_title),
        )
    }

    if (showPicker) {
        val context = LocalContext.current
        val systemLabel = stringResource(R.string.lang_system_short)
        // Use setup_title as the canary: a string we definitely translate in
        // any locale we actually ship — long enough to be unique English text,
        // so any locale falling back to the default trivially fails the probe.
        val languages = remember(context, systemLabel) {
            discoverAppLanguages(context, systemLabel, R.string.setup_title)
        }

        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                WithCurrentLocale {
                    TextButton(onClick = { showPicker = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            },
            title = {
                WithCurrentLocale {
                    Text(stringResource(R.string.lang_picker_title))
                }
            },
            text = {
                WithCurrentLocale {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        languages.forEach { lang ->
                            val selected = lang.tag == currentTag
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        repo.setLanguageTag(lang.tag)
                                        showPicker = false
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Spacer(Modifier.width(24.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    lang.nativeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}
