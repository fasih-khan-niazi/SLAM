package com.slam.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.ceil

private val Context.loginLockStore by preferencesDataStore("slam_login_lock")

class LoginLockoutStore(private val context: Context) {
    suspend fun remainingLockSeconds(now: Long = System.currentTimeMillis()): Long {
        val until = context.loginLockStore.data.map { it[KEY_UNTIL] ?: 0L }.first()
        return ((until - now).coerceAtLeast(0L) + 999L) / 1000L
    }

    suspend fun isLocked(now: Long = System.currentTimeMillis()): Boolean =
        remainingLockSeconds(now) > 0

    suspend fun failures(): Int =
        context.loginLockStore.data.map { it[KEY_FAILS] ?: 0 }.first()

    /** Applies the admin's sign-in limits (synced from /api/config). */
    suspend fun applyConfig(attemptCap: Int?, windowMinutes: Int?) {
        context.loginLockStore.edit { prefs ->
            prefs[KEY_CAP] = (attemptCap ?: DEFAULT_CAP).coerceIn(1, 30)
            prefs[KEY_WINDOW_MIN] = (windowMinutes ?: DEFAULT_WINDOW_MINUTES).coerceIn(1, 1440)
        }
    }

    suspend fun recordFailure(now: Long = System.currentTimeMillis()): LockoutStatus {
        if (isLocked(now)) {
            val minutes = ceil(remainingLockSeconds(now) / 60.0).toInt().coerceAtLeast(1)
            return LockoutStatus.Locked(minutes)
        }
        var fails = 0
        var locked = false
        var cap = DEFAULT_CAP
        var windowMinutes = DEFAULT_WINDOW_MINUTES
        context.loginLockStore.edit { prefs ->
            cap = prefs[KEY_CAP] ?: DEFAULT_CAP
            windowMinutes = prefs[KEY_WINDOW_MIN] ?: DEFAULT_WINDOW_MINUTES
            fails = (prefs[KEY_FAILS] ?: 0) + 1
            if (fails >= cap) {
                prefs[KEY_FAILS] = cap
                prefs[KEY_UNTIL] = now + windowMinutes * 60_000L
                locked = true
            } else {
                prefs[KEY_FAILS] = fails
            }
        }
        return if (locked) {
            LockoutStatus.Locked(minutesRemaining = windowMinutes)
        } else {
            LockoutStatus.Failed(triesLeft = cap - fails)
        }
    }

    suspend fun clear() {
        context.loginLockStore.edit {
            it.remove(KEY_FAILS)
            it.remove(KEY_UNTIL)
        }
    }

    sealed class LockoutStatus {
        data class Failed(val triesLeft: Int) : LockoutStatus()
        data class Locked(val minutesRemaining: Int) : LockoutStatus()
    }

    private companion object {
        val KEY_FAILS = intPreferencesKey("fails")
        val KEY_UNTIL = longPreferencesKey("until")
        val KEY_CAP = intPreferencesKey("cap")
        val KEY_WINDOW_MIN = intPreferencesKey("window_minutes")
        const val DEFAULT_CAP = 3
        const val DEFAULT_WINDOW_MINUTES = 15
    }
}
