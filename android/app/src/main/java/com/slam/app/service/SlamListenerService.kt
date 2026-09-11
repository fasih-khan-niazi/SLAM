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
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.location.QuietLocationWorker
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.PinStore
import com.slam.app.sms.EmergencyScheduler
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
        ListenerPrefs(this).setServiceActive(true)
        QuietLocationWorker.schedule(this)
        ensureChannel()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            QuietLocationWorker.cancel(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!CorePrerequisites.status(this).listenerReady || !PinStore(this).hasPin()) {
            ListenerPrefs(this).setListening(false)
            QuietLocationWorker.cancel(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        QuietLocationWorker.schedule(this)
        startInForeground()
        val from = intent?.getStringExtra(EXTRA_FROM)
        val body = intent?.getStringExtra(EXTRA_BODY)
        val messageId = intent?.getStringExtra(EXTRA_MESSAGE_ID)
        if (!from.isNullOrBlank() && !body.isNullOrBlank()) {
            scope.launch {
                LocateRequestHandler(applicationContext).handle(from, body, messageId ?: "$from:${body.hashCode()}")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ListenerPrefs(this).setServiceActive(false)
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
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "slam_listener_visible"
        const val NOTIFICATION_ID = 41
        const val EXTRA_FROM = "from"
        const val EXTRA_BODY = "body"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val ACTION_STOP = "com.slam.app.STOP_LISTENER"

        fun start(context: Context): Boolean {
            if (!CorePrerequisites.status(context).listenerReady || !PinStore(context).hasPin()) {
                ListenerPrefs(context).setListening(false)
                return false
            }
            return try {
                context.startForegroundService(Intent(context, SlamListenerService::class.java))
                ListenerPrefs(context).setListening(true)
                true
            } catch (_: Exception) {
                ListenerPrefs(context).setListening(false)
                false
            }
        }

        fun stop(context: Context) {
            ListenerPrefs(context).setListening(false)
            ListenerPrefs(context).setServiceActive(false)
            QuietLocationWorker.cancel(context)
            EmergencyPrefs(context).setOn(false)
            EmergencyScheduler.stop(context)
            val intent = Intent(context, SlamListenerService::class.java).setAction(ACTION_STOP)
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, SlamListenerService::class.java))
            }
        }

        fun locate(context: Context, from: String, body: String, messageId: String? = null) {
            if (!ListenerPrefs(context).isListening()) return
            val intent = Intent(context, SlamListenerService::class.java)
                .putExtra(EXTRA_FROM, from)
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
            context.startForegroundService(intent)
        }
    }
}
