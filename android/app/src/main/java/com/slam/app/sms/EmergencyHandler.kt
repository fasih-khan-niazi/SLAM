package com.slam.app.sms

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
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

class EmergencyHandler(private val context: Context) {
    suspend fun runOnce() = withContext(Dispatchers.IO) {
        val session = SessionStore(context)
        if (!session.consentAccepted.first()) return@withContext
        if (!session.cachedEmergencyEnabled()) {
            EmergencyPrefs(context).setOn(false)
            EmergencyScheduler.stop(context)
            return@withContext
        }
        if (!EmergencyPrefs(context).isOn()) return@withContext
        if (!ListenerPrefs(context).isListening()) return@withContext
        if (!PinStore(context).hasPin()) return@withContext
        if (!session.canLocate()) return@withContext

        val db = SlamDatabase.get(context)
        val trusted = db.trustedNumbers().all()
        if (trusted.isEmpty()) {
            EmergencyPrefs(context).setOn(false)
            EmergencyScheduler.stop(context)
            return@withContext
        }

        val preferBattery = session.preferBattery.first()
        val fix = LocationClient(context).acquire(preferBattery) ?: return@withContext
        val sender = SmsReplySender(context)
        val body = fix.smsBody()
        trusted.forEach { contact ->
            sender.send(contact.number.ifBlank { contact.normalized }, body)
        }
        session.consumeLocate()
        db.locationHistory().insert(
            LocationHistoryEntity(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                requestedBy = "emergency",
            )
        )
        db.locationHistory().trim()

        val token = session.token.first()
        if (token.isBlank()) return@withContext
        try {
            val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
            val response = api.logLocation(
                "Bearer $token",
                LocationLogBody(fix.latitude, fix.longitude, fix.accuracy, "emergency"),
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
