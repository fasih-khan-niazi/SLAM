package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("slam_prefs")

class SessionStore(private val context: Context) {
    val consentAccepted: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONSENT] ?: false }
    val token: Flow<String> = context.dataStore.data.map { it[KEY_TOKEN] ?: "" }
    val displayName: Flow<String> = context.dataStore.data.map { it[KEY_NAME] ?: "" }
    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_API] ?: "" }

    suspend fun setConsent(accepted: Boolean) {
        context.dataStore.edit { it[KEY_CONSENT] = accepted }
    }

    suspend fun setSession(token: String, name: String) {
        context.dataStore.edit {
            it[KEY_TOKEN] = token
            it[KEY_NAME] = name
        }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_API] = url.trim().trimEnd('/') }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_NAME)
        }
    }

    private companion object {
        val KEY_CONSENT = booleanPreferencesKey("consent")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_API = stringPreferencesKey("api_base")
    }
}
