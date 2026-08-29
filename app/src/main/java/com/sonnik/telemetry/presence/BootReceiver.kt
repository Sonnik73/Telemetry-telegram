package com.sonnik.telemetry.presence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sonnik.telemetry.TelemetryApp

/**
 * Resumes background presence tracking after a device reboot. Android 14+ forbids
 * starting a dataSync foreground service from BOOT_COMPLETED, so there we skip the
 * boot start — tracking resumes when the app is next opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (TelemetryApp.instance.presence.store.watchedIds().isNotEmpty()) {
            runCatching { PresenceService.start(context) }
        }
    }
}
