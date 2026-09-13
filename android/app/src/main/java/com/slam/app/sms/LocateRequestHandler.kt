package com.slam.app.sms

import android.content.Context
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EventRepository
import com.slam.app.data.OutboxDispatcher
import com.slam.app.data.SessionStore
import com.slam.app.data.local.FailedPinEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.SmsReceiptEntity
import com.slam.app.location.LocationClient
import com.slam.app.location.smsBody
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.PinLockout
import com.slam.app.security.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LocateRequestHandler(private val context: Context) {
    suspend fun handle(from: String, body: String, messageId: String = "$from:${body.hashCode()}") =
        withContext(Dispatchers.IO) {
        val session = SessionStore(context)
        val command = SmsCommandParser.parse(
            body,
            session.cachedSmsPrefix(),
            session.cachedPinMinLength(),
            session.cachedPinMaxLength(),
        ) ?: return@withContext
        val db = SlamDatabase.get(context)
        val pinStore = PinStore.get(context)
        val accountId = AccountIdentity.current(context)

        if (!session.consentAccepted.first()) {
            return@withContext
        }
        if (!CorePrerequisites.status(context).listenerReady) return@withContext
        if (!db.smsReceipts().accept(SmsReceiptEntity(messageId, accountId, System.currentTimeMillis()))) {
            return@withContext
        }

        val windowMs = PinLockout.windowMs(context)
        val attemptCap = PinLockout.attemptCap(context)
        val windowStart = System.currentTimeMillis() - windowMs
        db.failedPins().deleteOlderThan(accountId, windowStart)

        // Per-sender only: other trusted numbers stay usable during a lockout.
        val senderFails = db.failedPins().countSinceSender(accountId, from, windowStart)
        if (senderFails >= attemptCap) {
            return@withContext
        }

        val trusted = db.trustedNumbers().all(accountId)
        if (trusted.isEmpty() ||
            trusted.none { PhoneNumbers.matches(it.normalized.ifBlank { it.number }, from) }
        ) {
            return@withContext
        }

        if (!pinStore.hasPin() || !pinStore.verify(command.pin)) {
            db.failedPins().insert(FailedPinEntity(accountId = accountId, requestedBy = from))
            return@withContext
        }

        db.failedPins().clearSender(accountId, from)

        val events = EventRepository(context)
        val reservation = events.reserve("MANUAL", "sms:$messageId") ?: return@withContext

        val preferBattery = session.preferBattery.first()
        val sender = SmsReplySender(context)
        val fix = LocationClient(context).acquire(preferBattery)
        if (fix == null) {
            sender.send(from, "SLAM location unavailable: no live or saved location was found.")
            events.refund(reservation)
            return@withContext
        }

        if (!sender.send(from, fix.smsBody())) {
            events.refund(reservation)
            return@withContext
        }
        if (events.finalize(reservation, fix, from)) OutboxDispatcher(context).flush()
    }
}
