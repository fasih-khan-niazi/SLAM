package com.slam.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

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

    fun listeningActiveFlow(): Flow<Boolean> = callbackFlow {
        fun emitCurrent() {
            trySend(isListening() && isServiceActive())
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            emitCurrent()
        }
        emitCurrent()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

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
