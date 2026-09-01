package com.slam.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.slam.app.MainActivity
import com.slam.app.R
import com.slam.app.sms.LocateRequestHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SlamListenerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val from = intent?.getStringExtra(EXTRA_FROM)
        val body = intent?.getStringExtra(EXTRA_BODY)
        if (!from.isNullOrBlank() && !body.isNullOrBlank()) {
            scope.launch {
                LocateRequestHandler(applicationContext).handle(from, body)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening for location requests")
            .setSmallIcon(R.drawable.ic_slam_mark)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tracking",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "slam_listener"
        const val NOTIFICATION_ID = 41
        const val EXTRA_FROM = "from"
        const val EXTRA_BODY = "body"

        fun start(context: Context) {
            val intent = Intent(context, SlamListenerService::class.java)
            context.startForegroundService(intent)
        }

        fun locate(context: Context, from: String, body: String) {
            val intent = Intent(context, SlamListenerService::class.java)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_BODY, body)
            context.startForegroundService(intent)
        }
    }
}
