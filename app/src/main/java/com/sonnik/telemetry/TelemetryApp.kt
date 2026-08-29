package com.sonnik.telemetry

import android.app.Application
import android.util.Log
import com.sonnik.telemetry.autodl.MediaAutoDownloader
import com.sonnik.telemetry.autodl.MediaAutoStore
import com.sonnik.telemetry.data.ChatRepository
import com.sonnik.telemetry.data.MessagesRepository
import com.sonnik.telemetry.geo.GeoStore
import com.sonnik.telemetry.geo.GeoTracker
import com.sonnik.telemetry.intel.ArchiveStore
import com.sonnik.telemetry.intel.IntelTracker
import com.sonnik.telemetry.presence.PresenceStore
import com.sonnik.telemetry.presence.PresenceTracker
import com.sonnik.telemetry.security.AppLock
import com.sonnik.telemetry.td.TelegramClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryApp : Application() {

    lateinit var telegram: TelegramClient
        private set

    val chats: ChatRepository by lazy { ChatRepository(telegram.client) }

    val messages: MessagesRepository by lazy { MessagesRepository(telegram.client) }

    val presence: PresenceTracker by lazy {
        PresenceTracker(this, telegram, PresenceStore(this))
    }

    val geo: GeoTracker by lazy {
        GeoTracker(telegram, chats, GeoStore(this))
    }

    val intel: IntelTracker by lazy {
        IntelTracker(this, telegram, ArchiveStore(this))
    }

    val lock: AppLock by lazy { AppLock(this) }

    val mediaAuto: MediaAutoDownloader by lazy {
        MediaAutoDownloader(this, telegram, MediaAutoStore(this))
    }

    private val crashFile: File
        get() = File(filesDir, "last_crash.txt")

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.sonnik.telemetry.ui.theme.ThemeController.load(this)
        installCrashRecorder()
        telegram = TelegramClient(this)
    }

    /** Saves every uncaught exception so it can be shown (and copied) on the next launch. */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date())
                crashFile.writeText("$stamp · поток ${thread.name}\n\n${Log.getStackTraceString(throwable)}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readLastCrash(): String? = crashFile.takeIf { it.exists() }?.readText()

    fun clearLastCrash() {
        crashFile.delete()
    }

    companion object {
        lateinit var instance: TelemetryApp
            private set
    }
}
