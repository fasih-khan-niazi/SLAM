package com.slam.app.data

import android.content.Context
import com.slam.app.location.QuietLocationWorker
import com.slam.app.service.SlamListenerService
import com.slam.app.sms.EmergencyScheduler

class AccountLifecycleManager(private val context: Context) {
    private val session = SessionStore(context)

    suspend fun establishSession(token: String, name: String, userId: Int) {
        val incoming = userId.toString()
        val previous = AccountIdentity.current(context)
        if (previous != AccountIdentity.LEGACY_ACCOUNT_ID && previous != incoming) {
            AccountStateWiper(context).wipe(previous)
        }
        session.setSession(token, name, userId)
        OutboxScheduler.schedule(context, incoming)
    }

    suspend fun signOut() {
        val accountId = AccountIdentity.current(context)
        SlamListenerService.stop(context)
        EmergencyScheduler.stop(context, accountId)
        QuietLocationWorker.cancel(context, accountId)
        OutboxScheduler.cancel(context, accountId)
        ListenerPrefs(context).clear(accountId)
        EmergencyPrefs(context).clear(accountId)
        session.clearAuthentication()
    }
}
