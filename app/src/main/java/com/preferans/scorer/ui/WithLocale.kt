package com.preferans.scorer.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Overrides the locale of every composable inside [content] without recreating
 * the Activity. `stringResource` reads from `LocalContext.current.resources`,
 * which respects the overridden configuration we provide here.
 *
 * Pass null to use the system default locale (no override).
 */
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
