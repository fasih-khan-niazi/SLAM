package com.slam.app.data

import android.content.Context

class ListenerPrefs(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isListening(): Boolean = prefs.getBoolean(key(), false)

    fun setListening(on: Boolean) {
        prefs.edit().putBoolean(key(), on).commit()
    }

    fun isServiceActive(): Boolean = prefs.getBoolean(activeKey(), false)

    fun setServiceActive(on: Boolean) {
        prefs.edit().putBoolean(activeKey(), on).commit()
    }

    fun clear(userId: String) {
        prefs.edit()
            .remove("$KEY:$userId")
            .remove("$KEY_ACTIVE:$userId")
            .commit()
    }

    private fun key() = "$KEY:${AccountIdentity.current(context)}"
    private fun activeKey() = "$KEY_ACTIVE:${AccountIdentity.current(context)}"

    private companion object {
        const val PREFS = "slam_listener"
        const val KEY = "listening"
        const val KEY_ACTIVE = "service_active"
    }
}
