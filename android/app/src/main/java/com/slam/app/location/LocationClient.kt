package com.slam.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.os.Looper
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.slam.app.data.AccountIdentity
import com.slam.app.data.UiPreferences
import com.slam.app.data.local.LastLocationEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.permissions.CorePrerequisites
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

class LocationClient(private val context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    suspend fun acquire(preferBattery: Boolean = false): SlamFix? {
        val status = CorePrerequisites.status(context)
        if (status.locationGranted && status.locationServicesEnabled) {
            val batterySaver = preferBattery || batteryPercent() in 0..14
            if (!batterySaver) {
                requestFix(Priority.PRIORITY_HIGH_ACCURACY, 12_000L, "HIGH")?.let {
                    persist(it)
                    return it
                }
            }
            requestFix(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8_000L, "MEDIUM")?.let {
                persist(it)
                return it
            }
        }
        return if (UiPreferences(context).lastKnownFallbackEnabled.first()) {
            persistedLastKnown()
        } else {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFix(priority: Int, timeoutMs: Long, label: String): SlamFix? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val request = LocationRequest.Builder(priority, 1000L)
                    .setMaxUpdates(1)
                    .setWaitForAccurateLocation(priority == Priority.PRIORITY_HIGH_ACCURACY)
                    .build()
                val callback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        fused.removeLocationUpdates(this)
                        val loc = result.lastLocation
                        if (loc != null && cont.isActive) {
                            cont.resume(
                                SlamFix(
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    accuracy = label,
                                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                                    provider = loc.provider ?: "fused",
                                    timestamp = loc.time,
                                )
                            )
                        } else if (cont.isActive) {
                            cont.resume(null)
                        }
                    }
                }
                fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                cont.invokeOnCancellation {
                    fused.removeLocationUpdates(callback)
                }
            }
        }
    }

    private suspend fun persist(fix: SlamFix) {
        SlamDatabase.get(context).lastLocations().save(
            LastLocationEntity(
                accountId = AccountIdentity.current(context),
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
                provider = fix.provider,
                locationTimestamp = fix.timestamp,
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun persistedLastKnown(): SlamFix? {
        val saved = SlamDatabase.get(context).lastLocations().get(AccountIdentity.current(context)) ?: return null
        return SlamFix(
            latitude = saved.latitude,
            longitude = saved.longitude,
            accuracy = "LAST_KNOWN",
            accuracyMeters = saved.accuracyMeters,
            provider = saved.provider,
            timestamp = saved.locationTimestamp,
            isLastKnownFallback = true,
        )
    }

    private fun batteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
