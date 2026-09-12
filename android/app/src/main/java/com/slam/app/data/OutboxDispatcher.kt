package com.slam.app.data

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.remote.LocationLogBody
import com.slam.app.data.remote.SlamApiFactory
import kotlinx.coroutines.flow.first

class OutboxDispatcher(private val context: Context) {
    suspend fun flush(): Boolean {
        val session = SessionStore(context)
        val accountId = session.accountId()
        val token = session.token.first()
        if (token.isBlank()) return false
        val dao = SlamDatabase.get(context).eventLedger()
        // One-time recovery for older clients that stored accuracy=LAST_KNOWN (API 400).
        runCatching { dao.requeueFailedLastKnown(accountId) }
        val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
        for (entry in dao.pending(accountId)) {
            try {
                val accuracyLabel = normalizeAccuracy(entry.accuracy, entry.isLastKnownFallback)
                val response = api.logLocation(
                    "Bearer $token",
                    LocationLogBody(
                        latitude = entry.latitude,
                        longitude = entry.longitude,
                        accuracy = accuracyLabel,
                        requestedBy = entry.requestedBy,
                        eventId = entry.eventId,
                        accuracyMeters = entry.accuracyMeters,
                        provider = entry.provider,
                        source = if (entry.isLastKnownFallback) "LAST_KNOWN" else "CURRENT",
                        capturedAt = java.time.Instant.ofEpochMilli(entry.locationTimestamp).toString(),
                    ),
                )
                if (response.isSuccessful) {
                    dao.markSent(entry.eventId)
                    val remaining = response.body()?.data?.requestsRemaining
                    if (remaining != null) {
                        session.applyServerRemaining(remaining)
                    }
                } else if (response.code() in 400..499) {
                    dao.markFailed(entry.eventId)
                } else {
                    dao.markAttempt(entry.eventId)
                    return false
                }
            } catch (_: Exception) {
                dao.markAttempt(entry.eventId)
                return false
            }
        }
        dao.pruneCompleted(System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1_000L)
        return true
    }

    private fun normalizeAccuracy(raw: String, lastKnown: Boolean): String {
        val value = raw.trim().uppercase()
        if (value in setOf("HIGH", "MEDIUM", "LOW")) return value
        if (lastKnown || value == "LAST_KNOWN") return "LOW"
        return "LOW"
    }
}
