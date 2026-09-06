package com.slam.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                val consented = runBlocking { SessionStore(context).consentAccepted.first() }
                if (consented && PinStore(context).hasPin() && ListenerPrefs(context).isListening()) {
                    SlamListenerService.start(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
