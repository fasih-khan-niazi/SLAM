package com.slam.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.slam.app.data.ListenerPrefs
import com.slam.app.service.SlamListenerService

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (SmsCommandParser.parse(body) == null) return
        if (!ListenerPrefs(context).isListening()) return
        SlamListenerService.locate(context.applicationContext, from, body)
    }
}
