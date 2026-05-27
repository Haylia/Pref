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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _theme = MutableStateFlow(ThemePref.SYSTEM)
    val theme: StateFlow<ThemePref> = _theme

    init {
        scope.launch {
            try {
                val stored = context.appDataStore.data.map { it[themeKey] }.first()
                _theme.value = stored
                    ?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() }
                    ?: ThemePref.SYSTEM
            } catch (t: Throwable) {
                android.util.Log.w("SettingsRepository", "Failed to load theme pref", t)
                _theme.value = ThemePref.SYSTEM
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
}
