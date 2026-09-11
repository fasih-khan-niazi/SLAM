package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiDataStore by preferencesDataStore("slam_ui_preferences")

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class UiPreferences(private val context: Context) {
    val appearance: Flow<AppearanceMode> = context.uiDataStore.data.map { prefs ->
        when (runCatching { AppearanceMode.valueOf(prefs[KEY_APPEARANCE].orEmpty()) }.getOrNull()) {
            AppearanceMode.LIGHT -> AppearanceMode.LIGHT
            AppearanceMode.DARK, AppearanceMode.SYSTEM, null -> AppearanceMode.DARK
        }
    }

    val hapticsEnabled: Flow<Boolean> = context.uiDataStore.data.map {
        it[KEY_HAPTICS] ?: true
    }

    val lastKnownFallbackEnabled: Flow<Boolean> = context.uiDataStore.data.map {
        it[KEY_LAST_KNOWN] ?: true
    }

    suspend fun migrateAppearanceIfNeeded() {
        context.uiDataStore.edit { prefs ->
            val raw = prefs[KEY_APPEARANCE].orEmpty()
            if (raw == AppearanceMode.SYSTEM.name || raw.isBlank()) {
                prefs[KEY_APPEARANCE] = AppearanceMode.DARK.name
            }
        }
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        val resolved = if (mode == AppearanceMode.SYSTEM) AppearanceMode.DARK else mode
        context.uiDataStore.edit { it[KEY_APPEARANCE] = resolved.name }
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
