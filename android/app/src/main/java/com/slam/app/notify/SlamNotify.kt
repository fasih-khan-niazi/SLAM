package com.slam.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.slam.app.MainActivity
import com.slam.app.R

object SlamNotify {
    const val CHANNEL_EVENTS = "slam_events"
    const val CHANNEL_ACCOUNT = "slam_account"
    const val CHANNEL_LOCATION = "slam_location_hint"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, "Location events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When SLAM sends a location or emergency update"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACCOUNT, "Account", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Plan limits and payment updates"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LOCATION, "Location needed", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders to turn on location"
            },
        )
    }

    fun locationSent(context: Context, toLabel: String? = null) {
        show(
            context,
            CHANNEL_EVENTS,
            id = 2101,
            title = "Location sent",
            body = if (toLabel.isNullOrBlank()) {
                "SLAM shared your location with a trusted contact."
            } else {
                "SLAM shared your location with $toLabel."
            },
        )
    }

    fun emergencySent(context: Context) {
        show(
            context,
            CHANNEL_EVENTS,
            id = 2102,
            title = "Emergency update sent",
            body = "Your emergency location SMS was sent to trusted contacts.",
        )
    }

    fun quotaExhausted(context: Context, resetLabel: String?) {
        show(
            context,
            CHANNEL_ACCOUNT,
            id = 2103,
            title = "Locate limit reached",
            body = if (resetLabel.isNullOrBlank()) {
                "Listening stopped. Upgrade on the web portal or wait for your plan to renew."
            } else {
                "Listening stopped. Upgrade on the web portal or wait until $resetLabel."
            },
        )
    }

    fun paymentUpdate(context: Context, title: String, body: String) {
        show(context, CHANNEL_ACCOUNT, id = 2104, title = title, body = body)
    }

    fun turnLocationOn(context: Context) {
        show(
            context,
            CHANNEL_LOCATION,
            id = 2105,
            title = "Turn on Location",
            body = "SLAM could not get a live fix. Enable Location so trusted contacts get an accurate update.",
            highPriority = true,
        )
    }

    private fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        highPriority: Boolean = false,
    ) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val launch = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
