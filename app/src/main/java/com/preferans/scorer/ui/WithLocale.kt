package com.preferans.scorer.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.preferans.scorer.PreferansApp
import java.util.Locale

/**
 * Overrides the locale of every composable inside [content] without recreating
 * the Activity. `stringResource` reads from `LocalContext.current.resources`,
 * which respects the overridden configuration we provide here.
 *
 * Pass null to use the system default locale (no override).
 */
/**
 * Re-applies the current language preference. Use this inside any popup/dialog
 * content slot (DropdownMenu, AlertDialog, ModalBottomSheet, etc.) — popups
 * create their own ComposeView, which re-provides `LocalContext` from the host
 * Activity and so overwrites the override [WithLocale] set up at the root.
 *
 * Wrapping the popup's content with this restores the locale-aware context.
 */
@Composable
fun WithCurrentLocale(content: @Composable () -> Unit) {
    val tag by PreferansApp.instance.settingsRepository.languageTag.collectAsState()
    WithLocale(localeTag = tag) { content() }
}

@Composable
fun WithLocale(localeTag: String?, content: @Composable () -> Unit) {
    if (localeTag.isNullOrEmpty()) {
        content()
        return
    }
    val baseContext = LocalContext.current
    val newContext = remember(baseContext, localeTag) {
        val locale = Locale.forLanguageTag(localeTag)
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        baseContext.createConfigurationContext(config)
    }
    CompositionLocalProvider(
        LocalContext provides newContext,
        LocalConfiguration provides newContext.resources.configuration,
    ) {
        content()
    }
}
