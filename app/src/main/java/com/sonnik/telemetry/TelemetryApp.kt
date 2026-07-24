package com.sonnik.telemetry

import android.app.Application
import com.sonnik.telemetry.td.TelegramClient

class TelemetryApp : Application() {

    lateinit var telegram: TelegramClient
        private set

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
