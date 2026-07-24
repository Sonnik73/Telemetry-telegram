package com.sonnik.telemetry.presence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sonnik.telemetry.TelemetryApp

/** Resumes background presence tracking after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (TelemetryApp.instance.presence.store.watchedIds().isNotEmpty()) {
            runCatching { PresenceService.start(context) }
        }
    }
}
