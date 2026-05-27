package com.preferans.scorer.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single app-wide DataStore. Multiple repositories can read/write distinct keys.
 */
internal val Context.appDataStore by preferencesDataStore(name = "preferans_app")

/**
 * Legacy DataStore from an earlier build (pre-settings split). Read once on
 * startup and migrated into [appDataStore]. Do not write to it.
 */
internal val Context.legacyGameDataStore by preferencesDataStore(name = "preferans_game")
