package com.slam.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject

object AccountIdentity {
    const val LEGACY_ACCOUNT_ID = "legacy"
    private const val PREFS = "slam_account"
    private const val KEY_CURRENT = "current_user_id"
    @Volatile private var activeId: String = LEGACY_ACCOUNT_ID

    fun current(context: Context): String {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT, null)
            ?.takeIf { it.isNotBlank() }
            ?: LEGACY_ACCOUNT_ID
        activeId = stored
        return stored
    }

    fun active(): String = activeId

    fun setCurrent(context: Context, userId: String) {
        require(userId.isNotBlank())
        activeId = userId
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT, userId)
            .commit()
    }

    fun clearCurrent(context: Context, expectedUserId: String? = null) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (expectedUserId == null || prefs.getString(KEY_CURRENT, null) == expectedUserId) {
            prefs.edit().remove(KEY_CURRENT).commit()
            activeId = LEGACY_ACCOUNT_ID
        }
    }

    fun userIdFromJwt(token: String): String? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return@runCatching null
        val decoded = String(
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        val json = JSONObject(decoded)
        sequenceOf("user_id", "userId", "id", "sub")
            .mapNotNull { key -> json.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" } }
            .firstOrNull()
            ?: json.optJSONObject("user")?.let { user ->
                sequenceOf("id", "user_id", "userId")
                    .mapNotNull { key -> user.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" } }
                    .firstOrNull()
            }
    }.getOrNull()
}
