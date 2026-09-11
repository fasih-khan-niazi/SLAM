package com.slam.app.data

import android.content.Context
import com.slam.app.data.local.SlamDatabase
import com.slam.app.location.QuietLocationWorker
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.sms.EmergencyScheduler

class AccountStateWiper(private val context: Context) {
    suspend fun wipe(userId: String = AccountIdentity.current(context)) {
        SlamListenerService.stop(context)
        EmergencyScheduler.stop(context, userId)
        QuietLocationWorker.cancel(context, userId)
        OutboxScheduler.cancel(context, userId)
        ListenerPrefs(context).clear(userId)
        EmergencyPrefs(context).clear(userId)
        PinStore(context).clear(userId)
        LegacyAccountMigrator.markWiped(context, userId)
        SessionStore(context).wipeAccount(userId)
        SlamDatabase.close(context, userId)
    }
}
