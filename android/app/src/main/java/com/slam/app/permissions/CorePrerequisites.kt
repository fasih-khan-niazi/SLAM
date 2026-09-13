package com.slam.app.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

data class CoreStatus(
    val receiveSmsGranted: Boolean,
    val sendSmsGranted: Boolean,
    val locationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val locationServicesEnabled: Boolean,
    val notificationsGranted: Boolean,
) {
    val listenerReady: Boolean
        get() = receiveSmsGranted && sendSmsGranted && locationGranted &&
            backgroundLocationGranted && notificationsGranted
    val emergencyReady: Boolean
        get() = sendSmsGranted && locationGranted && backgroundLocationGranted && notificationsGranted
}

object CorePrerequisites {
    fun status(context: Context): CoreStatus {
        val locationManager = context.getSystemService(LocationManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return CoreStatus(
            receiveSmsGranted = granted(context, Manifest.permission.RECEIVE_SMS),
            sendSmsGranted = granted(context, Manifest.permission.SEND_SMS),
            locationGranted = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            backgroundLocationGranted = granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            locationServicesEnabled = runCatching { locationManager?.isLocationEnabled == true }.getOrDefault(false),
            notificationsGranted = granted(context, Manifest.permission.POST_NOTIFICATIONS) &&
                notificationManager?.areNotificationsEnabled() == true,
        )
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
