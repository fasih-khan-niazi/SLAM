package com.slam.app.location

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** What the background last-known refresh did most recently, so the app can show it. */
class QuietLocationPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun record(accountId: String, result: String, at: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong("$KEY_RUN:$accountId", at)
            .putString("$KEY_RESULT:$accountId", result)
            .apply()
    }

    fun lastRun(accountId: String): Long = prefs.getLong("$KEY_RUN:$accountId", 0L)

    fun lastResult(accountId: String): String = prefs.getString("$KEY_RESULT:$accountId", "").orEmpty()

    fun clear(accountId: String) {
        prefs.edit()
            .remove("$KEY_RUN:$accountId")
            .remove("$KEY_RESULT:$accountId")
            .remove("$KEY_SCHEDULED:$accountId")
            .apply()
    }

    /** The interval (minutes) WorkManager was last told to repeat on, so a config sync can detect a change. */
    fun scheduledMinutes(accountId: String): Int = prefs.getInt("$KEY_SCHEDULED:$accountId", 0)

    fun setScheduledMinutes(accountId: String, minutes: Int) {
        prefs.edit().putInt("$KEY_SCHEDULED:$accountId", minutes).apply()
    }

    fun changes(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        trySend(Unit)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val PREFS = "slam_quiet_location"
        const val KEY_RUN = "last_run"
        const val KEY_RESULT = "last_result"
        const val KEY_SCHEDULED = "scheduled_minutes"
    }
}
