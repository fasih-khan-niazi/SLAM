package com.slam.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecureTokenStore private constructor(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
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

    companion object {
        @Volatile
        private var instance: SecureTokenStore? = null

        fun get(context: Context): SecureTokenStore {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: SecureTokenStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
