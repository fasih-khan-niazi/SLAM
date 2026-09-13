package com.slam.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class OutboxWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        if (AccountIdentity.current(applicationContext) != owner) return Result.success()
        return runCatching {
            OutboxDispatcher(applicationContext).flush()
        }.fold(
            onSuccess = { complete -> if (complete) Result.success() else Result.retry() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
    }
}
