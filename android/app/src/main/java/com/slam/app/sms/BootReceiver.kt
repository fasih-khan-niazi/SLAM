package com.slam.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return
        if (PinStore(context).hasPin()) {
            SlamListenerService.start(context.applicationContext)
        }
    }
}
