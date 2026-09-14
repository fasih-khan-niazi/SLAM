package com.slam.app.sms

import android.content.Context
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.EventRepository
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.OutboxDispatcher
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.location.LocationClient
import com.slam.app.location.QuietLocationWorker
import com.slam.app.location.smsBody
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class EmergencyHandler(private val context: Context) {
    suspend fun runOnce(runId: String = UUID.randomUUID().toString()) = withContext(Dispatchers.IO) {
        val session = SessionStore(context)
        val accountId = AccountIdentity.current(context)
        val emergencyPrefs = EmergencyPrefs(context)
        if (!session.consentAccepted.first()) return@withContext
        if (!session.cachedEmergencyEnabled()) {
            EmergencyPrefs(context).setOn(false)
            EmergencyScheduler.stop(context)
            return@withContext
        }
        if (!emergencyPrefs.isOn()) return@withContext
        if (!ListenerPrefs(context).isListening()) return@withContext
        if (!PinStore.get(context).hasPin()) return@withContext
        if (!CorePrerequisites.status(context).emergencyReady) {
            emergencyPrefs.recordRun("Waiting for required permissions")
            return@withContext
        }

        val db = SlamDatabase.get(context)
        val trusted = db.trustedNumbers().all(accountId)
        if (trusted.isEmpty()) {
            EmergencyPrefs(context).setOn(false)
            emergencyPrefs.recordRun("Stopped: no trusted contacts")
            EmergencyScheduler.stop(context)
            return@withContext
        }

        val events = EventRepository(context)
        val reservation = events.reserve("EMERGENCY", "emergency:$runId") ?: run {
            EmergencyPrefs(context).setOn(false)
            emergencyPrefs.recordRun("Stopped: plan locate limit reached")
            EmergencyScheduler.stop(context, accountId)
            return@withContext
        }
        val preferBattery = session.preferBattery.first()
        val servicesOn = CorePrerequisites.status(context).locationServicesEnabled
        val fix = LocationClient(context).acquire(preferBattery)
        if (fix == null) {
            emergencyPrefs.recordRun("No live or saved location was available")
            if (!servicesOn) {
                com.slam.app.notify.SlamNotify.turnLocationOn(context)
            }
            val sender = SmsReplySender(context)
            trusted.forEach { contact ->
                sender.send(
                    contact.number.ifBlank { contact.normalized },
                    "SLAM emergency: location unavailable; no live or saved location was found.",
                )
            }
            events.refund(reservation)
            return@withContext
        }
        if (!servicesOn || fix.isLastKnownFallback) {
            com.slam.app.notify.SlamNotify.turnLocationOn(context)
        }
        val sender = SmsReplySender(context)
        val body = fix.smsBody()
        val queued = trusted.count { contact ->
            sender.send(contact.number.ifBlank { contact.normalized }, body)
        }
        if (queued == 0) {
            emergencyPrefs.recordRun("SMS could not be queued")
            events.refund(reservation)
            return@withContext
        }
        if (events.finalize(reservation, fix, "emergency")) {
            emergencyPrefs.recordRun("Sent to $queued trusted contact${if (queued == 1) "" else "s"}")
            OutboxDispatcher(context).flush()
            com.slam.app.notify.SlamNotify.emergencySent(context)
            if (!session.cachedUnlimited.first() && !session.canLocate()) {
                EmergencyPrefs(context).setOn(false)
                EmergencyScheduler.stop(context, accountId)
                ListenerPrefs(context).setListening(false)
                com.slam.app.service.SlamListenerService.stop(context)
                QuietLocationWorker.cancel(context)
                com.slam.app.notify.SlamNotify.quotaExhausted(context, session.cachedPeriodEndLabel())
            }
        }
    }
}
