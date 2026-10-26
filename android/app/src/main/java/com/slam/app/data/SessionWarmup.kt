package com.slam.app.data

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.security.PinCloudSync
import com.slam.app.security.PinStore
import com.slam.app.sms.EmergencyScheduler
import kotlinx.coroutines.flow.first

object SessionWarmup {
    suspend fun warm(context: Context) {
        runCatching {
            val session = SessionStore(context)
            val token = session.token.first()
            val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
            runCatching {
                // A failed fetch must not overwrite the cached config with defaults.
                val config = api.config().body()?.data ?: return@runCatching
                session.cacheProductConfig(
                    config.pinAttemptCap,
                    config.pinWindowMinutes,
                    config.emergencyEnabled,
                    config.resolvedEmergencyMinutes(),
                    config.smsPrefix,
                    config.pinMinLength,
                    config.pinMaxLength,
                    config.maintenance,
                    config.paymentsEnabled,
                    config.quietLocationIntervalMinutes,
                )
                LoginLockoutStore(context).applyConfig(config.loginAttemptCap, config.loginWindowMinutes)
                EmergencyScheduler.syncWithConfig(context, session.cachedEmergencyMinutes())
                com.slam.app.location.QuietLocationWorker.syncWithConfig(context, session.cachedQuietLocationMinutes())
            }
            if (token.isBlank()) return@runCatching
            val accountId = AccountIdentity.current(context)
            OutboxScheduler.schedule(context, accountId)
            runCatching { OutboxDispatcher(context).flush() }
            runCatching {
                val response = api.me("Bearer $token")
                val data = response.body()?.data
                if (response.isSuccessful && data != null) {
                    session.cacheUsage(data.subscription)
                    if (!session.consentAccepted.first()) {
                        session.setConsent(true)
                    }
                    PinCloudSync.restoreLocal(context, data.trackingPin)
                    if (PinStore.get(context).hasPin()) {
                        PinCloudSync.pushCurrent(context)
                    } else {
                        PinCloudSync.pullIfNeeded(context)
                    }
                    TrustedContactsSync.pull(context)
                }
            }
        }
    }
}
