package com.slam.app.data

import android.content.Context
import com.slam.app.data.local.SlamDatabase

object LegacyAccountMigrator {
    suspend fun migrate(context: Context, userId: String) {
        if (userId == AccountIdentity.LEGACY_ACCOUNT_ID) return
        val app = context.applicationContext
        val markers = app.getSharedPreferences("slam_account_migrations", Context.MODE_PRIVATE)
        if (markers.getBoolean(userId, false)) return
        if (!app.databaseList().contains("slam.db")) {
            markers.edit().putBoolean(userId, true).commit()
            return
        }

        val source = SlamDatabase.get(app, AccountIdentity.LEGACY_ACCOUNT_ID)
        val target = SlamDatabase.get(app, userId)
        source.trustedNumbers().all().forEach { item ->
            runCatching { target.trustedNumbers().insert(item.copy(id = 0, accountId = userId)) }
        }
        source.locationHistory().latest().forEach { item ->
            target.locationHistory().insert(item.copy(id = 0, accountId = userId))
        }
        source.failedPins().latest().forEach { item ->
            target.failedPins().insert(item.copy(id = 0, accountId = userId))
        }
        markers.edit().putBoolean(userId, true).commit()
        SlamDatabase.close(app, AccountIdentity.LEGACY_ACCOUNT_ID)
    }

    fun markWiped(context: Context, userId: String) {
        context.applicationContext.getSharedPreferences("slam_account_migrations", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(userId, true)
            .commit()
    }
}
