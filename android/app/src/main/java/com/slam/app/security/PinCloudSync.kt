package com.slam.app.security

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.PinBody
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.data.remote.TrackingPinPayload
import kotlinx.coroutines.flow.first

object PinCloudSync {
    fun restoreLocal(context: Context, payload: TrackingPinPayload?) {
        val salt = payload?.salt?.trim().orEmpty()
        val verifier = payload?.verifier?.trim().orEmpty()
        if (salt.isBlank() || verifier.isBlank()) return
        val store = PinStore.get(context)
        if (store.hasPin()) return
        store.restore(salt, verifier)
    }

    suspend fun pushCurrent(context: Context) {
        val session = SessionStore(context)
        val token = session.token.first()
        if (token.isBlank()) return
        val exported = PinStore.get(context).export() ?: return
        runCatching {
            SlamApiFactory.create(BuildConfig.API_BASE_URL).savePin(
                "Bearer $token",
                PinBody(pinSalt = exported.salt, pinVerifier = exported.verifier),
            )
        }
    }

    suspend fun pullIfNeeded(context: Context) {
        val session = SessionStore(context)
        val token = session.token.first()
        if (token.isBlank()) return
        if (PinStore.get(context).hasPin()) return
        runCatching {
            val response = SlamApiFactory.create(BuildConfig.API_BASE_URL).me("Bearer $token")
            val payload = response.body()?.data?.trackingPin
            if (response.isSuccessful) restoreLocal(context, payload)
        }
    }
}
