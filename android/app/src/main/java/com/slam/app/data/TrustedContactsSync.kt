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
    suspend fun pull(context: Context): Boolean {
        val session = SessionStore(context)
        val token = session.token.first()
        if (token.isBlank()) return false
        val response = runCatching {
            SlamApiFactory.create(BuildConfig.API_BASE_URL).getTrustedNumbers("Bearer $token")
        }.getOrNull() ?: return false
        val numbers = response.body()?.data?.numbers
        if (!response.isSuccessful || numbers == null) return false

        val accountId = AccountIdentity.current(context)
        val dao = SlamDatabase.get(context).trustedNumbers()
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
        return response.isSuccessful
    }
}
