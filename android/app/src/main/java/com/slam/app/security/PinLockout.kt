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
    /** Fallbacks when product config has never synced. */
    const val DEFAULT_WINDOW_MS = 15L * 60L * 1000L
    const val DEFAULT_ATTEMPT_CAP = 3

    @Deprecated("Use DEFAULT_WINDOW_MS or windowMs(context)", ReplaceWith("DEFAULT_WINDOW_MS"))
    const val WINDOW_MS = DEFAULT_WINDOW_MS

    @Deprecated("Use DEFAULT_ATTEMPT_CAP or attemptCap(context)", ReplaceWith("DEFAULT_ATTEMPT_CAP"))
    const val ATTEMPT_CAP = DEFAULT_ATTEMPT_CAP

    suspend fun windowMs(context: Context): Long {
        val cached = runCatching { SessionStore(context).cachedPinWindowMs() }.getOrNull()
        return (cached ?: DEFAULT_WINDOW_MS).coerceAtLeast(60_000L)
    }

    suspend fun attemptCap(context: Context): Int {
        val cached = runCatching { SessionStore(context).cachedPinAttemptCap() }.getOrNull()
        return (cached ?: DEFAULT_ATTEMPT_CAP).coerceIn(1, 5)
    }

    suspend fun lockedSenders(context: Context): List<PinSenderLock> {
        val accountId = AccountIdentity.current(context)
        val now = System.currentTimeMillis()
        val window = windowMs(context)
        val cap = attemptCap(context)
        val since = now - window
        val db = SlamDatabase.get(context).failedPins()
        return db.lockedSenders(accountId, since, cap).mapNotNull { sender ->
            val latest = db.latestFailAt(accountId, sender, since) ?: return@mapNotNull null
            val remaining = remainingLockMs(latest, window, now)
            if (remaining <= 0L) null else PinSenderLock(sender, remaining)
        }
    }

    suspend fun isSenderLocked(context: Context, sender: String): Boolean {
        val accountId = AccountIdentity.current(context)
        val window = windowMs(context)
        val cap = attemptCap(context)
        val since = System.currentTimeMillis() - window
        val fails = SlamDatabase.get(context).failedPins()
            .countSinceSender(accountId, sender, since)
        return isLocked(fails, cap)
    }

    /** True once a sender's failures within the window reach the cap. Pure, so it's directly testable. */
    fun isLocked(failCountInWindow: Int, cap: Int): Boolean = failCountInWindow >= cap

    /** Milliseconds left until a sender's lock expires; non-positive once it has. Pure, directly testable. */
    fun remainingLockMs(latestFailAt: Long, window: Long, now: Long): Long = (latestFailAt + window) - now
}
