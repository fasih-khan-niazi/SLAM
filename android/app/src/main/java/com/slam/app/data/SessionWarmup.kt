package com.slam.app.data

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.remote.SlamApiFactory
import kotlinx.coroutines.flow.first

object SessionWarmup {
    suspend fun warm(context: Context) {
        val session = SessionStore(context)
        val token = session.token.first()
        val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
        runCatching {
            val config = api.config().body()?.data
            session.cacheProductConfig(
                config?.pinAttemptCap,
                config?.pinWindowMinutes,
                config?.emergencyEnabled,
                config?.emergencyIntervalHours,
                config?.smsPrefix,
                config?.pinMinLength,
                config?.pinMaxLength,
            )
        }
        if (token.isBlank()) return
        runCatching {
            val response = api.me("Bearer $token")
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                session.cacheUsage(data.subscription)
                if (!session.consentAccepted.first()) {
                    session.setConsent(true)
                }
            }
        }
    }
}
