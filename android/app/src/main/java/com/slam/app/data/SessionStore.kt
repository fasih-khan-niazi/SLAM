package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slam.app.data.remote.SubscriptionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.dataStore by preferencesDataStore("slam_prefs")

class SessionStore(private val context: Context) {
    val consentAccepted: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONSENT] ?: false }
    val token: Flow<String> = context.dataStore.data.map { it[KEY_TOKEN] ?: "" }
    val displayName: Flow<String> = context.dataStore.data.map { it[KEY_NAME] ?: "" }
    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_API] ?: "" }
    val preferBattery: Flow<Boolean> = context.dataStore.data.map { it[KEY_BATTERY] ?: false }
    val cachedRemaining: Flow<Int> = context.dataStore.data.map { remainingAfterMonthRoll(it) }
    val cachedUnlimited: Flow<Boolean> = context.dataStore.data.map { (it[KEY_LIMIT] ?: 5) < 0 }
    val pinAttemptCap: Flow<Int> = context.dataStore.data.map { it[KEY_PIN_CAP] ?: 3 }
    val pinWindowMinutes: Flow<Int> = context.dataStore.data.map { it[KEY_PIN_WINDOW] ?: 15 }

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

    suspend fun setPreferBattery(value: Boolean) {
        context.dataStore.edit { it[KEY_BATTERY] = value }
    }

    suspend fun cacheProductConfig(pinCap: Int?, windowMinutes: Int?) {
        val cap = pinCap?.takeIf { it in 1..30 } ?: 3
        val window = windowMinutes?.takeIf { it in 1..1440 } ?: 15
        context.dataStore.edit {
            it[KEY_PIN_CAP] = cap
            it[KEY_PIN_WINDOW] = window
        }
    }

    suspend fun cachedPinAttemptCap(): Int = pinAttemptCap.first()

    suspend fun cachedPinWindowMs(): Long = pinWindowMinutes.first() * 60_000L

    suspend fun cachedPinWindowMinutes(): Int = pinWindowMinutes.first()

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_NAME)
        }
    }

    suspend fun cacheUsage(subscription: SubscriptionInfo?) {
        val limit = subscription?.monthlyLimit
        context.dataStore.edit {
            it[KEY_MONTH] = currentMonth()
            it[KEY_PERIOD_END] = subscription?.endDate ?: ""
            if (limit == null && subscription?.planName != "Free") {
                it[KEY_LIMIT] = -1
                it[KEY_REMAINING] = -1
            } else {
                val cap = limit ?: 5
                it[KEY_LIMIT] = cap
                it[KEY_REMAINING] = subscription?.requestsRemaining ?: cap
            }
        }
    }

    suspend fun canLocate(): Boolean {
        val prefs = context.dataStore.data.first()
        val remaining = remainingAfterMonthRoll(prefs)
        val limit = prefs[KEY_LIMIT] ?: 5
        return limit < 0 || remaining > 0
    }

    suspend fun consumeLocate() {
        context.dataStore.edit { prefs ->
            val limit = prefs[KEY_LIMIT] ?: 5
            if (limit < 0) return@edit
            val month = prefs[KEY_MONTH]
            if (month != currentMonth()) {
                prefs[KEY_MONTH] = currentMonth()
                prefs[KEY_REMAINING] = (limit - 1).coerceAtLeast(0)
                return@edit
            }
            val remaining = prefs[KEY_REMAINING] ?: limit
            prefs[KEY_REMAINING] = (remaining - 1).coerceAtLeast(0)
        }
    }

    suspend fun applyServerRemaining(remaining: Int?) {
        if (remaining == null) {
            context.dataStore.edit {
                it[KEY_LIMIT] = -1
                it[KEY_REMAINING] = -1
                it[KEY_MONTH] = currentMonth()
            }
            return
        }
        context.dataStore.edit {
            it[KEY_REMAINING] = remaining
            it[KEY_MONTH] = currentMonth()
        }
    }

    private fun remainingAfterMonthRoll(prefs: Preferences): Int {
        val limit = prefs[KEY_LIMIT] ?: 5
        if (limit < 0) return Int.MAX_VALUE
        val periodEnd = prefs[KEY_PERIOD_END].orEmpty()
        if (periodEnd.isNotBlank()) {
            val end = runCatching { java.time.Instant.parse(periodEnd) }.getOrNull()
                ?: runCatching { java.time.LocalDate.parse(periodEnd.take(10)).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant() }.getOrNull()
            if (end != null && java.time.Instant.now().isAfter(end)) return limit
        }
        val month = prefs[KEY_MONTH]
        if (month != null && month != currentMonth()) return limit
        return prefs[KEY_REMAINING] ?: limit
    }

    private fun currentMonth(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}"
    }

    private companion object {
        val KEY_CONSENT = booleanPreferencesKey("consent")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_API = stringPreferencesKey("api_base")
        val KEY_BATTERY = booleanPreferencesKey("prefer_battery")
        val KEY_REMAINING = intPreferencesKey("requests_remaining")
        val KEY_LIMIT = intPreferencesKey("monthly_limit")
        val KEY_MONTH = stringPreferencesKey("usage_month")
        val KEY_PERIOD_END = stringPreferencesKey("usage_period_end")
        val KEY_PIN_CAP = intPreferencesKey("pin_attempt_cap")
        val KEY_PIN_WINDOW = intPreferencesKey("pin_window_minutes")
    }
}
