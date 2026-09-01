package com.slam.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "slam_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasPin(): Boolean = !prefs.getString(KEY_PIN, null).isNullOrBlank()

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) return false
        prefs.edit().putString(KEY_PIN, pin).apply()
        return true
    }

    fun verify(pin: String): Boolean = pin == prefs.getString(KEY_PIN, null)

    private companion object {
        const val KEY_PIN = "owner_pin"
    }
}
