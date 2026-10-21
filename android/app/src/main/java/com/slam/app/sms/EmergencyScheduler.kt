package com.slam.app.sms

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.SessionStore
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
        EmergencyPrefs(context).setScheduledMinutes(interval.toInt())
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

    /**
     * Reconciles a running chain with the synced interval and revives it if it died.
     * Call after product config syncs and on app start.
     */
    suspend fun syncWithConfig(context: Context, minutes: Int) {
        val prefs = EmergencyPrefs(context)
        if (!prefs.isOn()) return
        // An operator turning Emergency off stops running chains at once, not at their next run.
        if (!SessionStore(context).cachedEmergencyEnabled()) {
            prefs.setOn(false)
            stop(context)
            return
        }
        val accountId = AccountIdentity.current(context)
        val desired = minutes.toLong().coerceIn(MIN_MINUTES, MAX_MINUTES)
        val changed = prefs.scheduledMinutes().toLong() != desired
        if (changed || !chainAlive(context, accountId)) {
            scheduleNext(context, accountId, desired)
        }
    }

    private suspend fun chainAlive(context: Context, accountId: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    .getWorkInfosForUniqueWork(chainName(accountId))
                    .get()
                    .any { !it.state.isFinished }
            }.getOrDefault(true)
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
