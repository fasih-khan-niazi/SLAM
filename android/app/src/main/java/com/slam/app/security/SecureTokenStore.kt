package com.slam.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureTokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "slam_tokens",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(userId: String): String = prefs.getString(key(userId), "").orEmpty()

    fun put(userId: String, token: String) {
        prefs.edit().putString(key(userId), token).commit()
    }

    fun clear(userId: String) {
        prefs.edit().remove(key(userId)).commit()
    }

    private fun key(userId: String) = "token:$userId"
}
