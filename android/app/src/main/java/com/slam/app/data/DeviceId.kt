package com.slam.app.data

import android.content.Context
import java.util.UUID

/** A random id for this install, sent with API calls so sign-in limits count per device. */
object DeviceId {
    private const val PREFS = "slam_device"
    private const val KEY = "device_id"

    @Volatile
    private var cached: String? = null

    fun get(context: Context): String {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val id = prefs.getString(KEY, null)
                    ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
                cached = id
                id
            }
        }
    }
}
