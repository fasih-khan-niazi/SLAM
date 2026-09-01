package com.slam.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationClient(private val context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    suspend fun acquire(): SlamFix? {
        if (!hasLocationPermission()) return cellFallback()

        val batterySaver = batteryPercent() < 15
        if (!batterySaver) {
            requestFix(Priority.PRIORITY_HIGH_ACCURACY, 12_000L, "HIGH")?.let { return it }
        }
        requestFix(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8_000L, "MEDIUM")?.let { return it }
        lastKnown()?.let { return it }
        return cellFallback()
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
                            cont.resume(SlamFix(loc.latitude, loc.longitude, label))
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

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(): SlamFix? {
        return suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(SlamFix(loc.latitude, loc.longitude, "MEDIUM"))
                    } else {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val gps = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                        val net = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
                        val best = listOfNotNull(gps, net).maxByOrNull { it.time }
                        cont.resume(best?.let { SlamFix(it.latitude, it.longitude, "MEDIUM") })
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cellFallback(): SlamFix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val net = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        if (net != null) return SlamFix(net.latitude, net.longitude, "LOW")

        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val cells = runCatching { telephony?.allCellInfo }.getOrNull()
        if (cells.isNullOrEmpty()) return null
        return null
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun batteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
