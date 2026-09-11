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
        runCatching { AppearanceMode.valueOf(prefs[KEY_APPEARANCE].orEmpty()) }
            .getOrDefault(AppearanceMode.DARK)
    }

    val hapticsEnabled: Flow<Boolean> = context.uiDataStore.data.map {
        it[KEY_HAPTICS] ?: true
    }

    val lastKnownFallbackEnabled: Flow<Boolean> = context.uiDataStore.data.map {
        it[KEY_LAST_KNOWN] ?: true
    }

    suspend fun setAppearance(mode: AppearanceMode) {
        context.uiDataStore.edit { it[KEY_APPEARANCE] = mode.name }
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
