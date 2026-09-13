package com.slam.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.AccountIdentity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                val session = SessionStore(context)
                val consented = runBlocking { session.consentAccepted.first() }
                val listening = ListenerPrefs(context).isListening()
                val emergency = EmergencyPrefs(context).isOn()
                val status = CorePrerequisites.status(context)
                val pinReady = PinStore.get(context).hasPin()
                if (consented && pinReady && listening && status.listenerReady) {
                    SlamListenerService.start(context.applicationContext)
                }
                val accountId = AccountIdentity.current(context)
                val hasContacts = runBlocking { SlamDatabase.get(context).trustedNumbers().count(accountId) > 0 }
                if (consented && pinReady && listening && emergency && status.emergencyReady && hasContacts &&
                    runBlocking { session.cachedEmergencyEnabled() }
                ) {
                    val hours = runBlocking { session.cachedEmergencyHours() }
                    EmergencyScheduler.start(context.applicationContext, hours)
                } else if (emergency) {
                    EmergencyPrefs(context).setOn(false)
                    EmergencyScheduler.stop(context)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
