package com.slam.app.data

import android.content.Context

class EmergencyPrefs(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(): Boolean = prefs.getBoolean(key(), false)

    fun setOn(on: Boolean) {
        prefs.edit().putBoolean(key(), on).commit()
    }

    fun setNextRun(at: Long) {
        prefs.edit().putLong(scoped(KEY_NEXT), at).commit()
    }

    fun nextRun(): Long = prefs.getLong(scoped(KEY_NEXT), 0L)

    fun recordRun(message: String, at: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(scoped(KEY_LAST), at)
            .putString(scoped(KEY_RESULT), message)
            .commit()
    }

    fun lastRun(): Long = prefs.getLong(scoped(KEY_LAST), 0L)
    fun lastResult(): String = prefs.getString(scoped(KEY_RESULT), "").orEmpty()

    fun clear(userId: String) {
        prefs.edit()
            .remove("$KEY:$userId")
            .remove("$KEY_NEXT:$userId")
            .remove("$KEY_LAST:$userId")
            .remove("$KEY_RESULT:$userId")
            .commit()
    }

    private fun key() = "$KEY:${AccountIdentity.current(context)}"
    private fun scoped(name: String) = "$name:${AccountIdentity.current(context)}"

    private companion object {
        const val PREFS = "slam_emergency"
        const val KEY = "on"
        const val KEY_NEXT = "next_run"
        const val KEY_LAST = "last_run"
        const val KEY_RESULT = "last_result"
    }
}
