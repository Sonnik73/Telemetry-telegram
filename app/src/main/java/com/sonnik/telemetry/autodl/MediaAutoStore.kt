package com.sonnik.telemetry.autodl

import android.content.Context

/**
 * Settings for background auto-download of incoming media: the destination
 * folder (a persisted SAF tree URI) and the set of chats it applies to.
 * Stored in the shared local prefs; nothing leaves the device.
 */
class MediaAutoStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    fun folderUri(): String? = prefs.getString(KEY_FOLDER, null)

    fun setFolderUri(uri: String?) {
        prefs.edit().putString(KEY_FOLDER, uri).apply()
    }

    fun enabledChats(): Set<Long> =
        prefs.getStringSet(KEY_CHATS, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun isEnabled(chatId: Long): Boolean = chatId in enabledChats()

    fun anyEnabled(): Boolean = enabledChats().isNotEmpty() && folderUri() != null

    fun setEnabled(chatId: Long, enabled: Boolean) {
        val current = enabledChats().toMutableSet()
        if (enabled) current.add(chatId) else current.remove(chatId)
        prefs.edit().putStringSet(KEY_CHATS, current.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY_FOLDER = "autodl_folder"
        const val KEY_CHATS = "autodl_chats"
    }
}
