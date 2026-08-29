package com.sonnik.telemetry.security

import android.content.Context
import java.security.MessageDigest

/**
 * App-entry lock: an optional PIN (stored only as a salted-free SHA-256 hash on
 * the device) plus an optional biometric unlock. State lives in the same prefs
 * file as other local settings; nothing leaves the device.
 */
class AppLock(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false) && prefs.getString(KEY_PIN, null) != null

    fun biometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setPin(pin: String) {
        prefs.edit()
            .putString(KEY_PIN, hash(pin))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    fun check(pin: String): Boolean = prefs.getString(KEY_PIN, null) == hash(pin)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun disable() {
        prefs.edit()
            .remove(KEY_PIN)
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC, false)
            .apply()
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_ENABLED = "lock_enabled"
        const val KEY_PIN = "lock_pin"
        const val KEY_BIOMETRIC = "lock_biometric"
    }
}
