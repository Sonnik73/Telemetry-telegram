package com.sonnik.telemetry

import android.app.Application
import com.sonnik.telemetry.data.ChatRepository
import com.sonnik.telemetry.td.TelegramClient

class TelemetryApp : Application() {

    lateinit var telegram: TelegramClient
        private set

    val chats: ChatRepository by lazy { ChatRepository(telegram.client) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        telegram = TelegramClient(this)
    }

    companion object {
        lateinit var instance: TelemetryApp
            private set
    }
}
