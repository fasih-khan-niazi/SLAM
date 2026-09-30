package com.slam.app.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
        val record = QuietLocationPrefs(applicationContext)
        fun skip(reason: String): Result {
            record.record(owner, "Skipped: $reason")
            return Result.success()
        }
        if (!ListenerPrefs(applicationContext).isListening()) return skip("listening is off")
        val status = CorePrerequisites.status(applicationContext)
        if (!status.locationGranted) return skip("location permission missing")
        if (!UiPreferences(applicationContext).lastKnownFallbackEnabled.first()) {
            return skip("last known location is turned off")
        }
        // Always attempt acquire (even if services appear off); LocationClient falls back.
        val preferBattery = SessionStore(applicationContext).preferBattery.first()
        val fix = LocationClient(applicationContext).acquire(preferBattery)
        record.record(
            owner,
            when {
                fix == null -> "No location available"
                fix.isLastKnownFallback -> "No live fix, kept the last saved location"
                else -> "Saved a fresh location"
            },
        )
        return Result.success()
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        private const val UNIQUE = "slam-quiet-location"

        fun schedule(context: Context, accountId: String = AccountIdentity.current(context)) {
            val request = PeriodicWorkRequestBuilder<QuietLocationWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES)
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

        /** One quiet capture right away (for example when listening starts); never stacks up. */
        fun captureNow(context: Context, accountId: String = AccountIdentity.current(context)) {
            val request = OneTimeWorkRequestBuilder<QuietLocationWorker>()
                .setInputData(
                    androidx.work.Data.Builder().putString(KEY_ACCOUNT_ID, accountId).build(),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "${name(accountId)}-now",
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, accountId: String = AccountIdentity.current(context)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(accountId))
        }

        private fun name(accountId: String) = "$UNIQUE-$accountId"
    }
}
