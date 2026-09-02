package com.sonnik.telemetry.security

import android.content.Context
import java.io.File
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM file encryption at rest, keyed by the same Keystore-wrapped key used
 * for the TDLib database ([DbKey]). Files are written as: 12-byte IV, then the GCM
 * ciphertext (with its authentication tag). Streaming, so large media never sits
 * fully in memory.
 */
object FileCrypto {

    private const val IV_LEN = 12

    private fun key(context: Context) = SecretKeySpec(DbKey.get(context), "AES")

    /** Encrypts [src] into [dst] (dst is overwritten). */
    fun encryptFile(context: Context, src: File, dst: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(context))
        dst.outputStream().use { out ->
            out.write(cipher.iv)
            CipherOutputStream(out, cipher).use { cos -> src.inputStream().use { it.copyTo(cos) } }
        }
    }

    /** Decrypts the encrypted [enc] file, writing plaintext to [out]. */
    fun decryptToStream(context: Context, enc: File, out: OutputStream) {
        enc.inputStream().use { input ->
            val iv = ByteArray(IV_LEN)
            var read = 0
            while (read < IV_LEN) {
                val n = input.read(iv, read, IV_LEN - read)
                if (n < 0) break
                read += n
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(context), GCMParameterSpec(128, iv))
            CipherInputStream(input, cipher).use { cis -> cis.copyTo(out) }
        }
    }

    /** Decrypts [enc] into a new file [dst] and returns it. */
    fun decryptToFile(context: Context, enc: File, dst: File): File {
        dst.outputStream().use { decryptToStream(context, enc, it) }
        return dst
    }

    /** Encrypts a small in-memory blob as IV + ciphertext. */
    fun encryptBytes(context: Context, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(context))
        return cipher.iv + cipher.doFinal(data)
    }

    /** Reverses [encryptBytes]. */
    fun decryptBytes(context: Context, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(context),
            GCMParameterSpec(128, data.copyOfRange(0, IV_LEN)),
        )
        return cipher.doFinal(data.copyOfRange(IV_LEN, data.size))
    }
}
