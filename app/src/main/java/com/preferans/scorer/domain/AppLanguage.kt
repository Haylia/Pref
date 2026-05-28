package com.preferans.scorer.domain

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/**
 * One option in the language picker.
 *
 *  - [tag] is the BCP-47 locale tag (`"en"`, `"ru"`, `"de"`, …). `null` means
 *    "follow system" (no per-app override).
 *  - [nativeName] is the language's own name in its own script
 *    (`English`, `Русский`, `Deutsch`, …) so it's recognisable regardless of
 *    the current UI language.
 *
 * Adding a new language requires no Kotlin changes: drop a `res/values-XX/`
 * directory with a translated `strings.xml` and it appears automatically.
 */
data class AppLanguage(
    val tag: String?,
    val nativeName: String,
)

/**
 * Discovers the languages this app actually has translations for.
 *
 * [AssetManager.getLocales] would happily list every locale any bundled
 * resource exists for, including AndroidX libraries' internal translations
 * (Afrikaans, Burmese, etc. that we never touched). To filter those out we
 * use [canaryResId] as a probe: a resource id whose default value we know to
 * be distinctive English text. For each candidate locale we look up the same
 * id under that locale's resources — if Android falls back to the default
 * (same string), this app doesn't translate that locale and we skip it.
 *
 * English is always returned because the default `values/` directory has no
 * locale tag.
 */
fun discoverAppLanguages(
    context: Context,
    systemLabel: String,
    @StringRes canaryResId: Int,
): List<AppLanguage> {
    val defaultCanary = context.resources.getString(canaryResId)

    // `assets.locales` reports every tag that ANY bundled resource (including
    // AndroidX libraries) provides — that includes region variants like "ru-RU"
    // and "de-AT" alongside the bare "ru" / "de". Collapse to the language
    // subtag so each language appears once.
    val languageCodes = context.resources.assets.locales
        .asSequence()
        .filter { it.isNotBlank() && it != "und" }
        .map { Locale.forLanguageTag(it).language }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

    val translated = buildList {
        // English is the default `values/`; always include.
        add(AppLanguage("en", labelFor("en")))
        for (code in languageCodes) {
            if (code.equals("en", ignoreCase = true)) continue
            if (!hasTranslation(context, code, canaryResId, defaultCanary)) continue
            add(AppLanguage(code, labelFor(code)))
        }
    }.sortedBy { it.nativeName.lowercase() }

    return listOf(AppLanguage(null, systemLabel)) + translated
}

private fun hasTranslation(
    context: Context,
    tag: String,
    @StringRes canaryResId: Int,
    defaultCanary: String,
): Boolean {
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
    val localised = runCatching {
        context.createConfigurationContext(config).getString(canaryResId)
    }.getOrNull() ?: return false
    return localised != defaultCanary
}

private fun labelFor(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val raw = locale.getDisplayLanguage(locale)
    if (raw.isBlank()) return tag
    return raw.replaceFirstChar { it.titlecase(locale) }
}
