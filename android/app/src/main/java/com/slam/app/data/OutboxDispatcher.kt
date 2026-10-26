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
        // One-time recovery for older clients that stored accuracy=LAST_KNOWN (API 400). This never
        // touches QUOTA_BLOCKED entries (separate state below), so it can't resurrect those.
        runCatching { dao.requeueFailedLastKnown(accountId) }

        // Quota-blocked entries (403, plan-period limit reached) are retried only when there's an
        // actual signal there's room again - not on every flush, which would hammer the server
        // while a plan stays exhausted.
        val hasHeadroom = session.cachedRemaining.first().let { it == Int.MAX_VALUE || it > 0 }
        if (hasHeadroom) {
            runCatching { dao.requeueQuotaBlocked(accountId) }
        }
        // An entry still waiting on quota after a month is unlikely to ever clear (cancelled plan,
        // abandoned account) - give up and tell the user instead of holding it forever.
        val quotaStaleCutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1_000L
        val goneStale = runCatching { dao.giveUpStaleQuotaBlocked(accountId, quotaStaleCutoff) }.getOrDefault(0)
        if (goneStale > 0) {
            com.slam.app.notify.SlamNotify.locationsDropped(context, goneStale)
        }

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
                    val data = response.body()?.data
                    session.applyServerUsage(
                        remaining = data?.requestsRemaining,
                        planName = data?.planName,
                        monthlyLimit = data?.monthlyLimit,
                    )
                } else if (response.code() == 401) {
                    // Session expired or the password changed elsewhere. Keep the event queued
                    // so it uploads after the user signs in again.
                    dao.markAttempt(entry.eventId)
                    return false
                } else if (response.code() == 403) {
                    dao.markQuotaBlocked(entry.eventId)
                    runCatching {
                        val me = api.me("Bearer $token")
                        val data = me.body()?.data
                        if (me.isSuccessful && data != null) {
                            session.cacheUsage(data.subscription)
                        }
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
