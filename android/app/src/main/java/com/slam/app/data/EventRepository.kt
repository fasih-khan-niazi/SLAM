package com.slam.app.data

import android.content.Context
import com.slam.app.data.local.EventState
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.OutboxEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.SlamEventEntity
import com.slam.app.location.SlamFix
import java.util.UUID
import kotlinx.coroutines.flow.first

data class EventReservation(val eventId: String)

class EventRepository(private val context: Context) {
    private val db get() = SlamDatabase.get(context)

    suspend fun reserve(kind: String, dedupeKey: String): EventReservation? {
        val session = SessionStore(context)
        val accountId = session.accountId()
        val now = System.currentTimeMillis()
        val event = SlamEventEntity(
            eventId = UUID.randomUUID().toString(),
            accountId = accountId,
            kind = kind,
            dedupeKey = dedupeKey,
            periodStart = session.currentPeriodStart(now),
            state = EventState.RESERVED,
            createdAt = now,
        )
        val remaining = session.cachedRemaining.first().let {
            if (it == Int.MAX_VALUE) -1 else it
        }
        return if (db.eventLedger().reserve(event, remaining)) {
            EventReservation(event.eventId)
        } else {
            null
        }
    }

    suspend fun finalize(reservation: EventReservation, fix: SlamFix, requestedBy: String): Boolean {
        val accountId = AccountIdentity.current(context)
        val now = System.currentTimeMillis()
        val finalized = db.eventLedger().finalize(
            reservation.eventId,
            OutboxEntity(
                eventId = reservation.eventId,
                accountId = accountId,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                accuracyMeters = fix.accuracyMeters,
                provider = fix.provider,
                locationTimestamp = fix.timestamp,
                isLastKnownFallback = fix.isLastKnownFallback,
                requestedBy = requestedBy,
                createdAt = now,
            ),
        )
        if (!finalized) return false
        db.locationHistory().insert(
            LocationHistoryEntity(
                accountId = accountId,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                accuracyMeters = fix.accuracyMeters,
                provider = fix.provider,
                locationTimestamp = fix.timestamp,
                isLastKnownFallback = fix.isLastKnownFallback,
                requestedBy = requestedBy,
                createdAt = now,
            )
        )
        db.locationHistory().trim(accountId)
        SessionStore(context).consumeLocate()
        OutboxScheduler.schedule(context, accountId)
        return true
    }

    suspend fun refund(reservation: EventReservation) {
        db.eventLedger().refund(reservation.eventId)
    }
}
