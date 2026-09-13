package com.slam.app.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.slam.app.data.AccountIdentity
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.UiPreferences
import com.slam.app.permissions.CorePrerequisites
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class QuietLocationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        if (AccountIdentity.current(applicationContext) != owner) return Result.success()
        if (!ListenerPrefs(applicationContext).isListening()) return Result.success()
        val status = CorePrerequisites.status(applicationContext)
        if (!status.locationGranted || !status.locationServicesEnabled) return Result.success()
        if (!UiPreferences(applicationContext).lastKnownFallbackEnabled.first()) return Result.success()
        val preferBattery = SessionStore(applicationContext).preferBattery.first()
        LocationClient(applicationContext).acquire(preferBattery)
        return Result.success()
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        private const val UNIQUE = "slam-quiet-location"

        fun schedule(context: Context, accountId: String = AccountIdentity.current(context)) {
            val request = PeriodicWorkRequestBuilder<QuietLocationWorker>(20, TimeUnit.MINUTES)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .setInputData(
                    androidx.work.Data.Builder().putString(KEY_ACCOUNT_ID, accountId).build(),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                name(accountId),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context, accountId: String = AccountIdentity.current(context)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(accountId))
        }

        private fun name(accountId: String) = "$UNIQUE-$accountId"
    }
}
