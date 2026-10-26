package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiDataStore by preferencesDataStore("slam_ui_preferences")

// SLAM only ever offers Light or Dark - it does not follow the OS theme. Any older stored value
// other than DARK (including a legacy "SYSTEM" string from before this was simplified) means LIGHT.
enum class AppearanceMode {
    LIGHT,
    DARK,
}

class UiPreferences(private val context: Context) {
    val appearance: Flow<AppearanceMode> = context.uiDataStore.data.map { prefs ->
        if (prefs[KEY_APPEARANCE] == AppearanceMode.DARK.name) AppearanceMode.DARK else AppearanceMode.LIGHT
    }

    val hapticsEnabled: Flow<Boolean> = context.uiDataStore.data.map {
        it[KEY_HAPTICS] ?: true
    }

    val lastKnownFallbackEnabled: Flow<Boolean> = context.uiDataStore.data.map {
        it[KEY_LAST_KNOWN] ?: true
    }

    suspend fun migrateAppearanceIfNeeded() {
        context.uiDataStore.edit { prefs ->
            if (prefs[KEY_APPEARANCE] != AppearanceMode.DARK.name) {
                prefs[KEY_APPEARANCE] = AppearanceMode.LIGHT.name
            }
        }
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.uiDataStore.edit { it[KEY_APPEARANCE] = mode.name }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        setAppearance(if (enabled) AppearanceMode.DARK else AppearanceMode.LIGHT)
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.uiDataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun setLastKnownFallbackEnabled(enabled: Boolean) {
        context.uiDataStore.edit { it[KEY_LAST_KNOWN] = enabled }
    }

    private companion object {
        val KEY_APPEARANCE = stringPreferencesKey("appearance")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_LAST_KNOWN = booleanPreferencesKey("last_known_fallback")
    }
}
