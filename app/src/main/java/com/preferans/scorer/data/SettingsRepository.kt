package com.preferans.scorer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.preferans.scorer.domain.LanguagePref
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
    private val languageKey = stringPreferencesKey("language_pref_v1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _theme = MutableStateFlow(ThemePref.SYSTEM)
    val theme: StateFlow<ThemePref> = _theme

    private val _language = MutableStateFlow(LanguagePref.SYSTEM)
    val language: StateFlow<LanguagePref> = _language

    init {
        scope.launch {
            try {
                val prefs = context.appDataStore.data.first()
                _theme.value = prefs[themeKey]
                    ?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() }
                    ?: ThemePref.SYSTEM
                _language.value = prefs[languageKey]
                    ?.let { runCatching { LanguagePref.valueOf(it) }.getOrNull() }
                    ?: LanguagePref.SYSTEM
            } catch (t: Throwable) {
                android.util.Log.w("SettingsRepository", "Failed to load settings", t)
            }
        }
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

    fun setLanguage(pref: LanguagePref) {
        _language.value = pref
        scope.launch {
            context.appDataStore.edit { it[languageKey] = pref.name }
        }
    }

    fun cycleLanguage() {
        setLanguage(
            when (_language.value) {
                LanguagePref.SYSTEM -> LanguagePref.ENGLISH
                LanguagePref.ENGLISH -> LanguagePref.RUSSIAN
                LanguagePref.RUSSIAN -> LanguagePref.SYSTEM
            }
        )
    }
}
