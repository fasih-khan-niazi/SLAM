package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.remote.SubscriptionInfo
import com.slam.app.security.SecureTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val Context.dataStore by preferencesDataStore("slam_prefs")

class SessionStore(private val context: Context) {
    val consentAccepted: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONSENT] ?: false }
    val token: Flow<String> = context.dataStore.data.map { prefs ->
        SecureTokenStore.get(context).get(AccountIdentity.current(context))
            .ifBlank { prefs[KEY_LEGACY_TOKEN].orEmpty() }
    }
    val displayName: Flow<String> = accountFlow("name", "")
    val apiBaseUrl: Flow<String> = context.dataStore.data.map { it[KEY_API] ?: "" }
    val preferBattery: Flow<Boolean> = accountFlow("prefer_battery", false)
    val cachedRemaining: Flow<Int> = context.dataStore.data.map { remainingForPeriod(it) }
    val cachedUnlimited: Flow<Boolean> = accountFlow("monthly_limit", 5).map { it < 0 }
    val pinAttemptCap: Flow<Int> = accountFlow("pin_attempt_cap", 3)
    val pinWindowMinutes: Flow<Int> = accountFlow("pin_window_minutes", 15)
    val emergencyEnabled: Flow<Boolean> = accountFlow("emergency_feature", true)
    val emergencyIntervalMinutes: Flow<Int> = accountFlow("emergency_minutes", 60)
    val maxContacts: Flow<Int> = accountFlow("max_contacts", 1)
    val smsPrefix: Flow<String> = accountFlow("sms_prefix", "SLAM")
    val pinMinLength: Flow<Int> = accountFlow("pin_min_length", 4)
    val pinMaxLength: Flow<Int> = accountFlow("pin_max_length", 6)
    val maintenance: Flow<Boolean> = accountFlow("maintenance", false)
    val paymentsEnabled: Flow<Boolean> = accountFlow("payments_enabled", true)

    suspend fun setConsent(accepted: Boolean) {
        context.dataStore.edit { it[KEY_CONSENT] = accepted }
    }

    suspend fun setSession(token: String, name: String) {
        setSession(token, name, AccountIdentity.userIdFromJwt(token) ?: AccountIdentity.LEGACY_ACCOUNT_ID)
    }

    suspend fun setSession(token: String, name: String, userId: Int) =
        setSession(token, name, userId.toString())

    suspend fun setSession(token: String, name: String, userId: String) {
        val previousLegacyToken = context.dataStore.data.first()[KEY_LEGACY_TOKEN]
        AccountIdentity.setCurrent(context, userId)
        SecureTokenStore.get(context).put(userId, token)
        context.dataStore.edit {
            it[stringPreferencesKey(scoped("name", userId))] = name
            it.remove(KEY_LEGACY_TOKEN)
            if (previousLegacyToken != null && userId != AccountIdentity.LEGACY_ACCOUNT_ID) {
                migrateLegacyPreferences(it, userId)
            }
        }
        LegacyAccountMigrator.migrate(context, userId)
    }

    suspend fun setApiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_API] = url.trim().trimEnd('/') }
    }

    suspend fun setPreferBattery(value: Boolean) {
        context.dataStore.edit { it[accountBoolean("prefer_battery")] = value }
    }

    suspend fun cacheProductConfig(
        pinCap: Int?,
        windowMinutes: Int?,
        emergencyOn: Boolean?,
        emergencyMinutes: Int?,
        smsPrefix: String? = null,
        pinMinLength: Int? = null,
        pinMaxLength: Int? = null,
        maintenance: Boolean? = null,
        paymentsEnabled: Boolean? = null,
    ) {
        val cap = pinCap?.takeIf { it in 1..30 } ?: 3
        val window = windowMinutes?.takeIf { it in 1..1440 } ?: 15
        val minutes = emergencyMinutes?.takeIf { it in 5..1440 } ?: 60
        val minPin = pinMinLength?.coerceIn(4, 8) ?: 4
        val maxPin = pinMaxLength?.coerceIn(minPin, 8) ?: 6
        context.dataStore.edit {
            it[accountInt("pin_attempt_cap")] = cap
            it[accountInt("pin_window_minutes")] = window
            it[accountBoolean("emergency_feature")] = emergencyOn != false
            it[accountInt("emergency_minutes")] = minutes
            it[stringPreferencesKey(scoped("sms_prefix"))] =
                smsPrefix?.trim()?.uppercase()?.takeIf { prefix -> prefix.matches(Regex("[A-Z0-9]{2,12}")) } ?: "SLAM"
            it[accountInt("pin_min_length")] = minPin
            it[accountInt("pin_max_length")] = maxPin
            if (maintenance != null) {
                it[accountBoolean("maintenance")] = maintenance
            }
            if (paymentsEnabled != null) {
                it[accountBoolean("payments_enabled")] = paymentsEnabled
            }
        }
    }

    suspend fun cachedPinAttemptCap(): Int = pinAttemptCap.first()

    suspend fun cachedPinWindowMs(): Long = pinWindowMinutes.first() * 60_000L

    suspend fun cachedPinWindowMinutes(): Int = pinWindowMinutes.first()

    suspend fun cachedEmergencyEnabled(): Boolean = emergencyEnabled.first()

    suspend fun cachedEmergencyMinutes(): Int = emergencyIntervalMinutes.first()

    suspend fun cachedMaxContacts(): Int = maxContacts.first()
    suspend fun cachedSmsPrefix(): String = smsPrefix.first()
    suspend fun cachedPinMinLength(): Int = pinMinLength.first()
    suspend fun cachedPinMaxLength(): Int = pinMaxLength.first()
    suspend fun cachedMaintenance(): Boolean = maintenance.first()
    suspend fun cachedPaymentsEnabled(): Boolean = paymentsEnabled.first()
    suspend fun cachedPlanName(): String {
        val accountId = accountId()
        return context.dataStore.data.first()[stringPreferencesKey(scoped("plan_name", accountId))] ?: "Free"
    }

    suspend fun cachedPeriodEndLabel(): String? {
        val accountId = accountId()
        val raw = context.dataStore.data.first()[stringPreferencesKey(scoped("period_end", accountId))]
            ?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.take(10)
    }

    /**
     * Signs out without deleting account-scoped state. Keeping the active account ID lets a
     * later login distinguish "same account" from "different account", while the missing token
     * still routes the app to Login.
     */
    suspend fun clearAuthentication() {
        val userId = accountId()
        SecureTokenStore.get(context).clear(userId)
    }

    suspend fun wipeAccount(userId: String) {
        SecureTokenStore.get(context).clear(userId)
        context.dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.endsWith(":$userId") }
                .forEach { prefs.remove(it) }
        }
        AccountIdentity.clearCurrent(context, userId)
    }

    suspend fun cacheUsage(subscription: SubscriptionInfo?) {
        val live = subscription?.forLiveUsage()
        val limit = live?.monthlyLimit
        val accountId = accountId()
        val start = parseTime(live?.startDate) ?: System.currentTimeMillis()
        val pendingCount = runCatching {
            SlamDatabase.get(context).eventLedger().pending(accountId).size
        }.getOrDefault(0)
        context.dataStore.edit {
            it[longPreferencesKey(scoped("period_anchor", accountId))] = start
            it[stringPreferencesKey(scoped("period_end", accountId))] = live?.endDate ?: ""
            if (limit == null && live?.planName != "Free") {
                it[intPreferencesKey(scoped("monthly_limit", accountId))] = -1
                it[intPreferencesKey(scoped("requests_remaining", accountId))] = -1
            } else {
                val cap = limit ?: 5
                it[intPreferencesKey(scoped("monthly_limit", accountId))] = cap
                val serverRemaining = live?.requestsRemaining ?: cap
                // Server is source of truth. Only reserve local slots for unsynced outbox events.
                val adjusted = if (pendingCount > 0 && serverRemaining != Int.MAX_VALUE) {
                    (serverRemaining - pendingCount).coerceAtLeast(0)
                } else {
                    serverRemaining
                }
                it[intPreferencesKey(scoped("requests_remaining", accountId))] = adjusted
            }
            it[intPreferencesKey(scoped("max_contacts", accountId))] =
                (live?.maxContacts ?: 1).coerceAtLeast(1)
            it[stringPreferencesKey(scoped("plan_name", accountId))] =
                live?.planName ?: "Free"
            it[longPreferencesKey(scoped("period_start", accountId))] = periodStart(it)
        }
    }

    suspend fun canLocate(): Boolean {
        val prefs = context.dataStore.data.first()
        val remaining = remainingForPeriod(prefs)
        val limit = prefs[accountInt("monthly_limit")] ?: 5
        return limit < 0 || remaining > 0
    }

    suspend fun consumeLocate() {
        context.dataStore.edit { prefs ->
            val limitKey = accountInt("monthly_limit")
            val remainingKey = accountInt("requests_remaining")
            val limit = prefs[limitKey] ?: 5
            if (limit < 0) return@edit
            // Never invent a full refill locally; only decrement the cached server value.
            prefs[remainingKey] = ((prefs[remainingKey] ?: limit) - 1).coerceAtLeast(0)
            prefs[accountLong("period_start")] = periodStart(prefs)
        }
    }

    suspend fun applyServerRemaining(remaining: Int?) {
        applyServerUsage(remaining = remaining, planName = null, monthlyLimit = null)
    }

    suspend fun applyServerUsage(
        remaining: Int?,
        planName: String?,
        monthlyLimit: Int?,
    ) {
        context.dataStore.edit {
            if (planName != null) {
                it[stringPreferencesKey(scoped("plan_name"))] = planName
            }
            when {
                remaining == null && (monthlyLimit == null || monthlyLimit < 0) &&
                    planName != null && planName != "Free" -> {
                    it[accountInt("monthly_limit")] = -1
                    it[accountInt("requests_remaining")] = -1
                }
                remaining == null && monthlyLimit == null -> {
                    it[accountInt("monthly_limit")] = -1
                    it[accountInt("requests_remaining")] = -1
                }
                else -> {
                    if (monthlyLimit != null) {
                        it[accountInt("monthly_limit")] = monthlyLimit
                    }
                    if (remaining != null) {
                        it[accountInt("requests_remaining")] = remaining
                    }
                }
            }
            it[accountLong("period_start")] = periodStart(it)
        }
    }

    suspend fun accountId(): String = AccountIdentity.current(context)

    suspend fun quotaLimit(): Int = context.dataStore.data.first()[accountInt("monthly_limit")] ?: 5

    suspend fun currentPeriodStart(nowMs: Long = System.currentTimeMillis()): Long =
        periodStart(context.dataStore.data.first(), nowMs)

    private fun remainingForPeriod(prefs: Preferences): Int {
        val limit = prefs[accountInt("monthly_limit")] ?: 5
        if (limit < 0) return Int.MAX_VALUE
        val endIso = prefs[stringPreferencesKey(scoped("period_end"))]
        val endMs = parseTime(endIso)
        if (endMs != null) {
            // Trust the last server snapshot until the server period ends.
            // Never refill to the full limit from a local calendar rollover alone.
            return prefs[accountInt("requests_remaining")] ?: limit
        }
        val currentStart = periodStart(prefs)
        return if (prefs[accountLong("period_start")] == currentStart) {
            prefs[accountInt("requests_remaining")] ?: limit
        } else {
            prefs[accountInt("requests_remaining")] ?: limit
        }
    }

    private fun periodStart(prefs: Preferences, nowMs: Long = System.currentTimeMillis()): Long {
        val anchor = prefs[accountLong("period_anchor")]
            ?: prefs[accountLong("period_start")]
            ?: nowMs
        return BillingPeriod.containing(anchor, nowMs)
    }

    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDate.parse(value.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
    }

    private fun scoped(name: String, userId: String = AccountIdentity.current(context)) = "$name:$userId"
    private fun accountInt(name: String) = intPreferencesKey(scoped(name))
    private fun accountLong(name: String) = longPreferencesKey(scoped(name))
    private fun accountBoolean(name: String) = booleanPreferencesKey(scoped(name))
    private fun <T> accountFlow(name: String, default: T): Flow<T> = context.dataStore.data.map { prefs ->
        @Suppress("UNCHECKED_CAST")
        when (default) {
            is Int -> prefs[intPreferencesKey(scoped(name))] ?: default
            is Boolean -> prefs[booleanPreferencesKey(scoped(name))] ?: default
            is String -> prefs[stringPreferencesKey(scoped(name))] ?: default
            else -> default
        } as T
    }

    private fun migrateLegacyPreferences(prefs: androidx.datastore.preferences.core.MutablePreferences, userId: String) {
        prefs[KEY_LEGACY_NAME]?.let { prefs[stringPreferencesKey(scoped("name", userId))] = it }
        prefs[KEY_LEGACY_BATTERY]?.let { prefs[booleanPreferencesKey(scoped("prefer_battery", userId))] = it }
        prefs[KEY_LEGACY_REMAINING]?.let { prefs[intPreferencesKey(scoped("requests_remaining", userId))] = it }
        prefs[KEY_LEGACY_LIMIT]?.let { prefs[intPreferencesKey(scoped("monthly_limit", userId))] = it }
    }

    private companion object {
        val KEY_CONSENT = booleanPreferencesKey("consent")
        val KEY_LEGACY_TOKEN = stringPreferencesKey("token")
        val KEY_LEGACY_NAME = stringPreferencesKey("name")
        val KEY_API = stringPreferencesKey("api_base")
        val KEY_LEGACY_BATTERY = booleanPreferencesKey("prefer_battery")
        val KEY_LEGACY_REMAINING = intPreferencesKey("requests_remaining")
        val KEY_LEGACY_LIMIT = intPreferencesKey("monthly_limit")
    }
}
