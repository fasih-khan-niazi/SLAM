package com.slam.app.data

import android.content.Context

class AccountLifecycleManager(private val context: Context) {
    private val session = SessionStore(context)

    suspend fun establishSession(token: String, name: String, userId: Int) {
        val incoming = userId.toString()
        val previous = AccountIdentity.current(context)
        if (previous != AccountIdentity.LEGACY_ACCOUNT_ID && previous != incoming) {
            AccountStateWiper(context).wipe(previous)
        }
        session.setSession(token, name, userId)
    }

    suspend fun signOutAndWipe() {
        val accountId = AccountIdentity.current(context)
        AccountStateWiper(context).wipe(accountId)
    }
}
