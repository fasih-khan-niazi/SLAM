package com.slam.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.slam.app.data.AccountIdentity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinStore private constructor(private val context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "slam_secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasPin(): Boolean {
        val account = AccountIdentity.current(context)
        return prefs.contains(verifierKey(account)) || !prefs.getString(KEY_LEGACY_PIN, null).isNullOrBlank()
    }

    fun export(): ExportedPin? {
        val account = AccountIdentity.current(context)
        val salt = prefs.getString(saltKey(account), null) ?: return null
        val verifier = prefs.getString(verifierKey(account), null) ?: return null
        return ExportedPin(salt = salt, verifier = verifier)
    }

    fun restore(saltHex: String, verifierHex: String): Boolean {
        if (saltHex.length != SALT_BYTES * 2 || verifierHex.length != KEY_BITS / 4) return false
        if (saltHex.hexToBytes() == null || verifierHex.hexToBytes() == null) return false
        val account = AccountIdentity.current(context)
        prefs.edit()
            .putString(saltKey(account), saltHex.lowercase())
            .putString(verifierKey(account), verifierHex.lowercase())
            .remove(KEY_LEGACY_PIN)
            .remove(recoverableKey(account))
            .commit()
        return true
    }

    fun setPin(pin: String, minLength: Int = 4, maxLength: Int = 6): Boolean {
        if (pin.length !in minLength..maxLength || !pin.all { it.isDigit() }) return false
        val account = AccountIdentity.current(context)
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val verifier = derive(pin, salt)
        prefs.edit()
            .putString(saltKey(account), salt.toHex())
            .putString(verifierKey(account), verifier.toHex())
            .putString(recoverableKey(account), pin)
            .remove(KEY_LEGACY_PIN)
            .commit()
        return true
    }

    /** Local-only recoverable PIN for clipboard copy. Cleared on full wipe / cloud restore. */
    fun recoverablePin(): String? {
        val account = AccountIdentity.current(context)
        return prefs.getString(recoverableKey(account), null)?.takeIf { it.all(Char::isDigit) }
    }

    fun verify(pin: String): Boolean {
        val account = AccountIdentity.current(context)
        val salt = prefs.getString(saltKey(account), null)?.hexToBytes()
        val expected = prefs.getString(verifierKey(account), null)?.hexToBytes()
        if (salt != null && expected != null) {
            val matches = MessageDigest.isEqual(expected, derive(pin, salt))
            if (matches && prefs.getString(recoverableKey(account), null).isNullOrBlank()) {
                prefs.edit().putString(recoverableKey(account), pin).commit()
            }
            return matches
        }

        val legacy = prefs.getString(KEY_LEGACY_PIN, null) ?: return false
        val matches = MessageDigest.isEqual(
            legacy.toByteArray(Charsets.UTF_8),
            pin.toByteArray(Charsets.UTF_8),
        )
        if (matches) setPin(pin)
        return matches
    }

    fun clear() = clear(AccountIdentity.current(context))

    fun clear(userId: String) {
        prefs.edit()
            .remove(saltKey(userId))
            .remove(verifierKey(userId))
            .remove(recoverableKey(userId))
            .apply {
                if (userId == AccountIdentity.LEGACY_ACCOUNT_ID) remove(KEY_LEGACY_PIN)
            }
            .commit()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun saltKey(account: String) = "pin_salt:$account"
    private fun verifierKey(account: String) = "pin_verifier:$account"
    private fun recoverableKey(account: String) = "pin_recoverable:$account"
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray? = runCatching {
        if (length % 2 != 0) return@runCatching null
        ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }.getOrNull()

    companion object {
        private const val KEY_LEGACY_PIN = "owner_pin"
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256

        @Volatile
        private var instance: PinStore? = null

        fun get(context: Context): PinStore {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: PinStore(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class ExportedPin(
    val salt: String,
    val verifier: String,
)
