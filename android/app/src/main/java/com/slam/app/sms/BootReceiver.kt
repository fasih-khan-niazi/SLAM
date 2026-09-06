package com.slam.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        val pending = goAsync()
        Thread {
            try {
                val session = SessionStore(context)
                val consented = runBlocking { session.consentAccepted.first() }
                val listening = ListenerPrefs(context).isListening()
                val emergency = EmergencyPrefs(context).isOn()
                if (consented && PinStore(context).hasPin() && (listening || emergency)) {
                    SlamListenerService.start(context.applicationContext)
                }
                if (consented && emergency) {
                    val hours = runBlocking { session.cachedEmergencyHours() }
                    EmergencyScheduler.start(context.applicationContext, hours)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
