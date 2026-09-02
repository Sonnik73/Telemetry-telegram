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

    /**
     * Milliseconds left of a brute-force lockout, or 0 when entry is allowed.
     * Persisted, so it survives killing and restarting the app.
     */
    fun lockoutRemainingMs(): Long =
        (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun check(pin: String): Boolean {
        if (lockoutRemainingMs() > 0) return false
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        val saltB64 = prefs.getString(KEY_SALT, null)
        val ok = if (saltB64 == null) {
            // Legacy unsalted SHA-256 hash: verify, then upgrade to the salted form.
            constantTimeEquals(stored, legacySha256(pin)).also { if (it) setPin(pin) }
        } else {
            constantTimeEquals(stored, pbkdf2(pin, Base64.decode(saltB64, Base64.NO_WRAP)))
        }
        if (ok) clearFailures() else registerFailure()
        return ok
    }

    /**
     * Counts a wrong PIN and, past a few free tries, starts an exponentially
     * growing lockout (30s, 1m, 2m … capped at 15m) so the code can't be brute-forced.
     */
    private fun registerFailure() {
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        var until = 0L
        if (fails > FREE_ATTEMPTS) {
            val step = (fails - FREE_ATTEMPTS - 1).coerceIn(0, 5)
            val delay = (BASE_LOCKOUT_MS shl step).coerceAtMost(MAX_LOCKOUT_MS)
            until = System.currentTimeMillis() + delay
        }
        prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCKOUT_UNTIL, until).apply()
    }

    private fun clearFailures() {
        prefs.edit().remove(KEY_FAILS).remove(KEY_LOCKOUT_UNTIL).apply()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun disable() {
        prefs.edit()
            .remove(KEY_PIN)
            .remove(KEY_SALT)
            .remove(KEY_FAILS)
            .remove(KEY_LOCKOUT_UNTIL)
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
        const val FREE_ATTEMPTS = 4
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L
        const val KEY_ENABLED = "lock_enabled"
        const val KEY_PIN = "lock_pin"
        const val KEY_SALT = "lock_salt"
        const val KEY_BIOMETRIC = "lock_biometric"
        const val KEY_FAILS = "lock_fails"
        const val KEY_LOCKOUT_UNTIL = "lock_until"
    }
}
