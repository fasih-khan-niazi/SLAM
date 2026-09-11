package com.slam.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object OutboxScheduler {
    fun schedule(context: Context, accountId: String = AccountIdentity.current(context)) {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(OutboxWorker.KEY_ACCOUNT_ID, accountId).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            name(accountId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, accountId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(accountId))
    }

    private fun name(accountId: String) = "slam-outbox-$accountId"
}
