package com.sonnik.telemetry.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App-entry lock: an optional PIN plus an optional biometric unlock. The PIN is
 * stored only as a salted PBKDF2-HMAC-SHA256 hash on the device (never in plaintext,
 * never off the device). Legacy unsalted SHA-256 hashes are still verified and are
 * transparently upgraded to the salted form on the next successful check.
 */
class AppLock(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false) && prefs.getString(KEY_PIN, null) != null

    fun biometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN, pbkdf2(pin, salt))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    fun check(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        val saltB64 = prefs.getString(KEY_SALT, null)
        if (saltB64 == null) {
            // Legacy unsalted SHA-256 hash: verify, then upgrade to the salted form.
            val ok = constantTimeEquals(stored, legacySha256(pin))
            if (ok) setPin(pin)
            return ok
        }
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        return constantTimeEquals(stored, pbkdf2(pin, salt))
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun disable() {
        prefs.edit()
            .remove(KEY_PIN)
            .remove(KEY_SALT)
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC, false)
            .apply()
    }

    private fun pbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun legacySha256(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Length-constant comparison so verification time doesn't leak the hash. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private companion object {
        const val ITERATIONS = 120_000
        const val KEY_ENABLED = "lock_enabled"
        const val KEY_PIN = "lock_pin"
        const val KEY_SALT = "lock_salt"
        const val KEY_BIOMETRIC = "lock_biometric"
    }
}
