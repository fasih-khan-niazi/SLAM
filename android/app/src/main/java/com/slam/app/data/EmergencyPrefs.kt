package com.slam.app.data

import android.content.Context

class EmergencyPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(): Boolean = prefs.getBoolean(KEY, false)

    fun setOn(on: Boolean) {
        prefs.edit().putBoolean(KEY, on).apply()
    }

    private companion object {
        const val PREFS = "slam_emergency"
        const val KEY = "on"
    }
}
