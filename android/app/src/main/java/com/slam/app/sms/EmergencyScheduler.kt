package com.slam.app.sms

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import java.util.UUID
import java.util.concurrent.TimeUnit

object EmergencyScheduler {
    private const val UNIQUE = "slam-emergency"
    /** WorkManager periodic minimum is 15 minutes. */
    private const val MIN_MINUTES = 15L
    private const val MAX_MINUTES = 24L * 60L

    fun start(context: Context, minutes: Int) {
        val accountId = AccountIdentity.current(context)
        val interval = minutes.toLong().coerceIn(MIN_MINUTES, MAX_MINUTES)
        EmergencyPrefs(context).setNextRun(
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(interval),
        )
        val periodic = PeriodicWorkRequestBuilder<EmergencyWorker>(interval, TimeUnit.MINUTES)
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(EmergencyWorker.KEY_ACCOUNT_ID, accountId).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            periodicName(accountId),
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
    }

    fun pingNow(context: Context) {
        val accountId = AccountIdentity.current(context)
        val request = OneTimeWorkRequestBuilder<EmergencyWorker>()
            .setInputData(
                Data.Builder()
                    .putString(EmergencyWorker.KEY_ACCOUNT_ID, accountId)
                    .putString(EmergencyWorker.KEY_RUN_ID, UUID.randomUUID().toString())
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(immediateName(accountId), ExistingWorkPolicy.REPLACE, request)
    }

    fun stop(context: Context) {
        stop(context, AccountIdentity.current(context))
    }

    fun stop(context: Context, accountId: String) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(periodicName(accountId))
            cancelUniqueWork(immediateName(accountId))
        }
        if (AccountIdentity.current(context) == accountId) {
            EmergencyPrefs(context).setNextRun(0L)
        }
    }

    private fun periodicName(accountId: String) = "$UNIQUE-periodic-$accountId"
    private fun immediateName(accountId: String) = "$UNIQUE-immediate-$accountId"
}
