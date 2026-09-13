package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore("slam_onboarding")

class OnboardingStore(private val context: Context) {
    fun completed(accountId: String): Flow<Boolean> =
        context.onboardingDataStore.data.map { it[key(accountId)] ?: false }

    suspend fun isCompleted(accountId: String): Boolean = completed(accountId).first()

    suspend fun markCompleted(accountId: String) {
        context.onboardingDataStore.edit { it[key(accountId)] = true }
    }

    private fun key(accountId: String) = booleanPreferencesKey("onboarding_done:$accountId")
}
