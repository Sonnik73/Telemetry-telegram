package com.sonnik.telemetry.security

import android.content.Context
import java.io.File

/**
 * Housekeeping for the plaintext scratch copies the app hands to external viewers.
 *
 * Opening media (from a chat, the gallery, or a decrypted one-time capture) requires
 * a real readable file, so a copy lands in `cacheDir/shared_media`. Those copies are
 * plaintext and would otherwise linger forever next to their encrypted originals, so
 * they are wiped whenever the app starts and on demand from Settings.
 */
object SecureCache {

    /** Deletes every plaintext scratch copy. Returns how many bytes were freed. */
    fun wipeSharedMedia(context: Context): Long {
        val dir = File(context.cacheDir, "shared_media")
        if (!dir.isDirectory) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) freed += size
        }
        return freed
    }
}
