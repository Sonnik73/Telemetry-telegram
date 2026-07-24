package com.sonnik.telemetry

import android.app.Application
import android.util.Log
import com.sonnik.telemetry.data.ChatRepository
import com.sonnik.telemetry.presence.PresenceStore
import com.sonnik.telemetry.presence.PresenceTracker
import com.sonnik.telemetry.td.TelegramClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryApp : Application() {

    lateinit var telegram: TelegramClient
        private set

    val chats: ChatRepository by lazy { ChatRepository(telegram.client) }

    val presence: PresenceTracker by lazy {
        PresenceTracker(this, telegram, PresenceStore(this))
    }

    private val crashFile: File
        get() = File(filesDir, "last_crash.txt")

    override fun onCreate() {
        super.onCreate()
        instance = this
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
