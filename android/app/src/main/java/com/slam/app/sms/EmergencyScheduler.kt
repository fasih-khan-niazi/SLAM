package com.slam.app.sms

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object EmergencyScheduler {
    private const val UNIQUE = "slam-emergency"

    fun start(context: Context, hours: Int) {
        val interval = hours.coerceIn(1, 24).toLong()
        val periodic = PeriodicWorkRequestBuilder<EmergencyWorker>(interval, TimeUnit.HOURS)
            .setInitialDelay(interval, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
    }

    fun pingNow(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<EmergencyWorker>().build())
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE)
    }
}
