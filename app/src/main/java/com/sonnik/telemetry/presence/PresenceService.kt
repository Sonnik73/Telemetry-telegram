package com.sonnik.telemetry.presence

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sonnik.telemetry.R
import com.sonnik.telemetry.TelemetryApp

/**
 * Keeps the process alive so [PresenceTracker] can observe status updates while
 * the app is in the background. Runs only while at least one user is watched;
 * started and stopped from the tracker UI.
 */
class PresenceService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = TelemetryApp.instance
        app.presence.start()
        app.geo.start()
        app.intel.start()
        val count = app.presence.store.watchedIds().size
        // Android restricts when a dataSync foreground service may start (e.g. from
        // BOOT_COMPLETED on Android 14+, or from the background). If disallowed, the
        // in-process collectors above still run; just don't crash the app.
        try {
            startForegroundCompat(NOTIFICATION_ID, buildNotification(count))
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(count: Int): Notification {
        return NotificationCompat.Builder(this, PresenceTracker.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Отслеживание присутствия")
            .setContentText("Наблюдаю за контактами: $count")
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PresenceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresenceService::class.java))
        }
    }
}
