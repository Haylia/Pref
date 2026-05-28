package com.preferans.scorer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.preferans.scorer.domain.ThemePref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_pref_v1")
    /**
     * Stored value is either an empty string (meaning "follow system") or a
     * BCP-47 language tag (`"en"`, `"ru"`, …). Older builds stored a
     * `LanguagePref` enum name — migrated on first read.
     */
    private val languageKey = stringPreferencesKey("language_tag_v1")
    private val legacyLanguageKey = stringPreferencesKey("language_pref_v1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _theme = MutableStateFlow(ThemePref.SYSTEM)
    val theme: StateFlow<ThemePref> = _theme

    /** Active language tag, or `null` to follow the system locale. */
    private val _languageTag = MutableStateFlow<String?>(null)
    val languageTag: StateFlow<String?> = _languageTag

    init {
        scope.launch {
            try {
                val prefs = context.appDataStore.data.first()
                _theme.value = prefs[themeKey]
                    ?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() }
                    ?: ThemePref.SYSTEM
                _languageTag.value = loadLanguageTag(prefs)
            } catch (t: Throwable) {
                android.util.Log.w("SettingsRepository", "Failed to load settings", t)
            }
        }
    }

    private suspend fun loadLanguageTag(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): String? {
        // Prefer the new key.
        prefs[languageKey]?.let { return it.ifBlank { null } }
        // Migrate the old enum-named key if present.
        val legacy = prefs[legacyLanguageKey] ?: return null
        val migrated = when (legacy) {
            "SYSTEM" -> ""
            "ENGLISH" -> "en"
            "RUSSIAN" -> "ru"
            else -> legacy // forward-compat: any tag-shaped value we don't recognise
        }
        // Persist under the new key so we don't re-migrate.
        runCatching {
            context.appDataStore.edit { edit ->
                edit[languageKey] = migrated
                edit.remove(legacyLanguageKey)
            }
        }
        return migrated.ifBlank { null }
    }

    fun setTheme(pref: ThemePref) {
        _theme.value = pref
        scope.launch {
            context.appDataStore.edit { it[themeKey] = pref.name }
        }
    }

    fun cycleTheme() {
        setTheme(
            when (_theme.value) {
                ThemePref.SYSTEM -> ThemePref.LIGHT
                ThemePref.LIGHT -> ThemePref.DARK
                ThemePref.DARK -> ThemePref.SYSTEM
            }
        )
    }

    /** Pass null to follow the system locale, or a BCP-47 tag (`"en"`, `"ru"`). */
    fun setLanguageTag(tag: String?) {
        _languageTag.value = tag
        scope.launch {
            context.appDataStore.edit { it[languageKey] = tag.orEmpty() }
        }
    }
}
