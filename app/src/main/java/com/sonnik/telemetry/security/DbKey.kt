package com.sonnik.telemetry.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides a stable 32-byte key used to encrypt the TDLib database at rest.
 *
 * The random database key is itself wrapped (AES-256-GCM) by a non-exportable key
 * held in the hardware-backed AndroidKeyStore, so the database key never sits in
 * plaintext on disk. If the keystore is unavailable, it falls back to storing the
 * raw key in private prefs so the database stays readable (weaker, but functional).
 */
object DbKey {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val WRAP_ALIAS = "telemetry_db_wrap"
    private const val PREFS = "telemetry"
    private const val KEY_CT = "db_key_ct"
    private const val KEY_IV = "db_key_iv"
    private const val KEY_RAW = "db_key_raw"

    /** Returns the persistent database key, generating and storing one on first use. */
    fun get(context: Context): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val ct = prefs.getString(KEY_CT, null)
        val iv = prefs.getString(KEY_IV, null)
        if (ct != null && iv != null) {
            runCatching {
                return unwrap(Base64.decode(iv, Base64.NO_WRAP), Base64.decode(ct, Base64.NO_WRAP))
            }
        }
        prefs.getString(KEY_RAW, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }

        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = runCatching { wrap(dbKey) }.getOrNull()
        if (wrapped != null) {
            prefs.edit()
                .putString(KEY_IV, Base64.encodeToString(wrapped.first, Base64.NO_WRAP))
                .putString(KEY_CT, Base64.encodeToString(wrapped.second, Base64.NO_WRAP))
                .apply()
        } else {
            prefs.edit().putString(KEY_RAW, Base64.encodeToString(dbKey, Base64.NO_WRAP)).apply()
        }
        return dbKey
    }

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(WRAP_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAP_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun wrap(data: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val ciphertext = cipher.doFinal(data)
        return cipher.iv to ciphertext
    }

    private fun unwrap(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
