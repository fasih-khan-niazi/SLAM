package com.slam.app.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class EmergencyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EmergencyHandler(applicationContext).runOnce()
        return Result.success()
    }
}
