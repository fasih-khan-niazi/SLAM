package com.slam.app.data

import android.content.Context
import com.slam.app.BuildConfig
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.data.remote.TrustedNumberItem
import com.slam.app.data.remote.TrustedNumbersBody
import com.slam.app.sms.PhoneNumbers
import kotlinx.coroutines.flow.first

object TrustedContactsSync {
    private const val PREFS = "slam_contacts_sync"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True while this phone has contact edits the cloud has not accepted yet. */
    private fun isDirty(context: Context): Boolean =
        prefs(context).getBoolean("dirty:${AccountIdentity.current(context)}", false)

    private fun setDirty(context: Context, dirty: Boolean) {
        prefs(context).edit().putBoolean("dirty:${AccountIdentity.current(context)}", dirty).apply()
    }

    suspend fun pull(context: Context): Boolean {
        val session = SessionStore(context)
        val token = session.token.first()
        if (token.isBlank()) return false
        // Unsynced local edits win: send them first so a pull never reverts them.
        if (isDirty(context)) return push(context)
        val response = runCatching {
            SlamApiFactory.create(BuildConfig.API_BASE_URL).getTrustedNumbers("Bearer $token")
        }.getOrNull() ?: return false
        val numbers = response.body()?.data?.numbers
        if (!response.isSuccessful || numbers == null) return false

        val accountId = AccountIdentity.current(context)
        val dao = SlamDatabase.get(context).trustedNumbers()
        if (numbers.isEmpty()) {
            // Nothing in the cloud yet (for example contacts added before sync existed):
            // keep this phone's list and upload it instead of wiping it.
            if (dao.count(accountId) > 0) push(context)
            return true
        }
        dao.clear(accountId)
        numbers.forEach { item ->
            val normalized = PhoneNumbers.digits(item.phone).ifBlank { return@forEach }
            runCatching {
                dao.insert(
                    TrustedNumberEntity(
                        accountId = accountId,
                        label = item.label.ifBlank { item.phone },
                        number = item.phone,
                        normalized = normalized,
                    ),
                )
            }
        }
        return true
    }

    suspend fun push(context: Context): Boolean {
        val session = SessionStore(context)
        val token = session.token.first()
        if (token.isBlank()) return false
        setDirty(context, true)
        val accountId = AccountIdentity.current(context)
        val local = SlamDatabase.get(context).trustedNumbers().all(accountId)
        val body = TrustedNumbersBody(
            numbers = local.map {
                TrustedNumberItem(label = it.label, phone = it.number)
            },
        )
        val response = runCatching {
            SlamApiFactory.create(BuildConfig.API_BASE_URL).putTrustedNumbers("Bearer $token", body)
        }.getOrNull() ?: return false
        val accepted = response.isSuccessful
        // A 4xx means the server refused this list (for example over the plan's contact cap);
        // retrying the same list cannot succeed, so stop treating it as pending.
        if (accepted || response.code() in 400..499) setDirty(context, false)
        return accepted
    }
}
