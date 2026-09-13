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

    suspend fun recordFailure(now: Long = System.currentTimeMillis()): LockoutStatus {
        if (isLocked(now)) {
            val minutes = ceil(remainingLockSeconds(now) / 60.0).toInt().coerceAtLeast(1)
            return LockoutStatus.Locked(minutes)
        }
        var fails = 0
        var locked = false
        context.loginLockStore.edit { prefs ->
            fails = (prefs[KEY_FAILS] ?: 0) + 1
            if (fails >= MAX_FAILURES) {
                prefs[KEY_FAILS] = MAX_FAILURES
                prefs[KEY_UNTIL] = now + LOCK_MS
                locked = true
            } else {
                prefs[KEY_FAILS] = fails
            }
        }
        return if (locked) {
            LockoutStatus.Locked(minutesRemaining = 15)
        } else {
            LockoutStatus.Failed(triesLeft = MAX_FAILURES - fails)
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
        const val MAX_FAILURES = 3
        const val LOCK_MS = 15L * 60L * 1_000L
    }
}
