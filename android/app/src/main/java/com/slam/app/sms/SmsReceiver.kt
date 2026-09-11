package com.slam.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.service.SlamListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp = messages.minOfOrNull { it.timestampMillis } ?: 0L
        val messageId = MessageIds.stable(from, timestamp, body)
        if (!ListenerPrefs(context).isListening()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SessionStore(context)
                val command = SmsCommandParser.parse(
                    body,
                    session.cachedSmsPrefix(),
                    session.cachedPinMinLength(),
                    session.cachedPinMaxLength(),
                )
                if (command != null) {
                    SlamListenerService.locate(context.applicationContext, from, body, messageId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
