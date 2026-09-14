package com.slam.app.sms

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import java.util.UUID
import java.util.concurrent.TimeUnit

object EmergencyScheduler {
    private const val UNIQUE = "slam-emergency"
    /** Product minimum; chained one-shots support values below WorkManager's 15m periodic floor. */
    private const val MIN_MINUTES = 5L
    private const val MAX_MINUTES = 24L * 60L

    fun start(context: Context, minutes: Int) {
        val accountId = AccountIdentity.current(context)
        val interval = minutes.toLong().coerceIn(MIN_MINUTES, MAX_MINUTES)
        // Drop any legacy periodic work from older builds.
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(periodicName(accountId))
        scheduleNext(context, accountId, interval)
    }

    fun scheduleNext(context: Context, accountId: String, minutes: Long) {
        val interval = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        EmergencyPrefs(context).setNextRun(
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(interval),
        )
        val request = OneTimeWorkRequestBuilder<EmergencyWorker>()
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .setInputData(
                Data.Builder()
                    .putString(EmergencyWorker.KEY_ACCOUNT_ID, accountId)
                    .putString(EmergencyWorker.KEY_RUN_ID, UUID.randomUUID().toString())
                    .putLong(EmergencyWorker.KEY_INTERVAL_MINUTES, interval)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            chainName(accountId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun pingNow(context: Context) {
        val accountId = AccountIdentity.current(context)
        val request = OneTimeWorkRequestBuilder<EmergencyWorker>()
            .setInputData(
                Data.Builder()
                    .putString(EmergencyWorker.KEY_ACCOUNT_ID, accountId)
                    .putString(EmergencyWorker.KEY_RUN_ID, UUID.randomUUID().toString())
                    .build(),
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
            cancelUniqueWork(chainName(accountId))
            cancelUniqueWork(immediateName(accountId))
        }
        if (AccountIdentity.current(context) == accountId) {
            EmergencyPrefs(context).setNextRun(0L)
        }
    }

    private fun periodicName(accountId: String) = "$UNIQUE-periodic-$accountId"
    private fun chainName(accountId: String) = "$UNIQUE-chain-$accountId"
    private fun immediateName(accountId: String) = "$UNIQUE-immediate-$accountId"
}
