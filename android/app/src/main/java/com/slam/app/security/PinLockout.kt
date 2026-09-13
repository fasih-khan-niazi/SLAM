package com.slam.app.security

import android.content.Context
import com.slam.app.data.AccountIdentity
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase

data class PinSenderLock(
    val sender: String,
    val remainingMs: Long,
)

object PinLockout {
    const val WINDOW_MS = 15L * 60L * 1000L
    const val ATTEMPT_CAP = 3

    suspend fun lockedSenders(context: Context): List<PinSenderLock> {
        val accountId = AccountIdentity.current(context)
        val now = System.currentTimeMillis()
        val since = now - WINDOW_MS
        val db = SlamDatabase.get(context).failedPins()
        return db.lockedSenders(accountId, since, ATTEMPT_CAP).mapNotNull { sender ->
            val latest = db.latestFailAt(accountId, sender, since) ?: return@mapNotNull null
            val unlockAt = latest + WINDOW_MS
            val remaining = unlockAt - now
            if (remaining <= 0L) null else PinSenderLock(sender, remaining)
        }
    }

    suspend fun isSenderLocked(context: Context, sender: String): Boolean {
        val accountId = AccountIdentity.current(context)
        val since = System.currentTimeMillis() - WINDOW_MS
        val fails = SlamDatabase.get(context).failedPins()
            .countSinceSender(accountId, sender, since)
        return fails >= ATTEMPT_CAP
    }
}
