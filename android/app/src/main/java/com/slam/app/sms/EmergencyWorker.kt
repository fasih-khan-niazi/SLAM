package com.slam.app.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.SessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmergencyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scheduledAccount = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        if (scheduledAccount != AccountIdentity.current(applicationContext)) return Result.success()
        return mutex.withLock {
            EmergencyHandler(applicationContext).runOnce(
                inputData.getString(KEY_RUN_ID) ?: id.toString(),
            )
            if (EmergencyPrefs(applicationContext).isOn()) {
                val fromInput = inputData.getLong(KEY_INTERVAL_MINUTES, 0L)
                val minutes = if (fromInput >= 5L) {
                    fromInput
                } else {
                    SessionStore(applicationContext).cachedEmergencyMinutes().toLong()
                }.coerceIn(5L, 24L * 60L)
                EmergencyScheduler.scheduleNext(applicationContext, scheduledAccount, minutes)
            }
            Result.success()
        }
    }

    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_RUN_ID = "run_id"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private val mutex = Mutex()
    }
}
