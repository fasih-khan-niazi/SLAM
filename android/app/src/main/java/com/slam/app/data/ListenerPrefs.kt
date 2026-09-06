package com.slam.app.data

import android.content.Context

class ListenerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isListening(): Boolean = prefs.getBoolean(KEY, false)

    fun setListening(on: Boolean) {
        prefs.edit().putBoolean(KEY, on).apply()
    }

    private companion object {
        const val PREFS = "slam_listener"
        const val KEY = "listening"
    }
}
