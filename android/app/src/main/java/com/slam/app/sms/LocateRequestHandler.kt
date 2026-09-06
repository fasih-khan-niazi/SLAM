package com.slam.app.sms

import android.content.Context
import com.slam.app.data.SessionStore
import com.slam.app.data.local.FailedPinEntity
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
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

        if (!pinStore.hasPin() || !pinStore.verify(command.pin)) {
            db.failedPins().insert(FailedPinEntity(requestedBy = from))
            return@withContext
        }

        val trusted = db.trustedNumbers().all()
        if (trusted.isNotEmpty() && trusted.none { PhoneNumbers.matches(it.normalized.ifBlank { it.number }, from) }) {
            return@withContext
        }

        val preferBattery = SessionStore(context).preferBattery.first()
        val sender = SmsReplySender(context)
        val fix = LocationClient(context).acquire(preferBattery)
        if (fix == null) {
            sender.send(from, "SLAM location unavailable")
            return@withContext
        }

        sender.send(from, fix.smsBody())
        db.locationHistory().insert(
            LocationHistoryEntity(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                requestedBy = from,
            )
        )
        db.locationHistory().trim()
    }
}
