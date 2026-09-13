package com.slam.app

import android.app.Application
import com.slam.app.data.ListenerPrefs

class SlamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // A new process cannot inherit a running service from the old process.
        // The service sets this back to true from its own onCreate.
        ListenerPrefs(this).setServiceActive(false)
    }
}
