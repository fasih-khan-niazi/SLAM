package com.slam.app.sms

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.SessionStore
import com.slam.app.data.local.FailedPinEntity
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.remote.LocationLogBody
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.location.LocationClient
import com.slam.app.location.smsBody
import com.slam.app.security.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LocateRequestHandler(private val context: Context) {
    suspend fun handle(from: String, body: String) = withContext(Dispatchers.IO) {
        val command = SmsCommandParser.parse(body) ?: return@withContext
        val db = SlamDatabase.get(context)
        val pinStore = PinStore(context)
        val session = SessionStore(context)

        if (!session.consentAccepted.first()) {
            return@withContext
        }

        val windowMs = session.cachedPinWindowMs()
        val windowStart = System.currentTimeMillis() - windowMs
        db.failedPins().deleteOlderThan(windowStart)
        val recentFails = db.failedPins().countSince(windowStart)
        val cap = session.cachedPinAttemptCap()
        if (recentFails >= cap) {
            return@withContext
        }

        if (!pinStore.hasPin() || !pinStore.verify(command.pin)) {
            db.failedPins().insert(FailedPinEntity(requestedBy = from))
            return@withContext
        }

        db.failedPins().clear()

        val trusted = db.trustedNumbers().all()
        if (trusted.isNotEmpty() && trusted.none { PhoneNumbers.matches(it.normalized.ifBlank { it.number }, from) }) {
            return@withContext
        }

        if (!session.canLocate()) {
            return@withContext
        }

        val preferBattery = session.preferBattery.first()
        val sender = SmsReplySender(context)
        val fix = LocationClient(context).acquire(preferBattery)
        if (fix == null) {
            sender.send(from, "SLAM location unavailable")
            return@withContext
        }

        sender.send(from, fix.smsBody())
        session.consumeLocate()
        db.locationHistory().insert(
            LocationHistoryEntity(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                requestedBy = from,
            )
        )
        db.locationHistory().trim()

        val token = session.token.first()
        if (token.isBlank()) return@withContext
        try {
            val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
            val response = api.logLocation(
                "Bearer $token",
                LocationLogBody(fix.latitude, fix.longitude, fix.accuracy, from),
            )
            val remaining = response.body()?.data?.requestsRemaining
            if (response.isSuccessful) {
                session.applyServerRemaining(remaining)
            }
        } catch (_: Exception) {
            // Offline: local cache already consumed.
        }
    }
}
