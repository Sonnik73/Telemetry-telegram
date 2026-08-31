package com.sonnik.telemetry.presence

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        app.mediaAuto.start()
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

    // Android 14 (API 34) caps the cumulative runtime of a dataSync foreground
    // service (~6 hours per 24h). When the budget is exhausted the system calls
    // onTimeout and requires us to stop the service within a few seconds, or it
    // throws ForegroundServiceDidNotStopInTimeException and crashes the app. Stop
    // the foreground service gracefully here; the in-process collectors keep
    // running while the process lives, and the service restarts the next time the
    // app is opened. On Android 15 (API 35) the two-argument overload is invoked.
    override fun onTimeout(startId: Int) {
        notifyPaused()
        stopForegroundAndSelf(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        notifyPaused()
        stopForegroundAndSelf(startId)
    }

    private fun stopForegroundAndSelf(startId: Int) {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf(startId)
    }

    // Let the user know background tracking was paused by the system timeout and
    // that reopening the app resumes it. Tapping the notification launches the app.
    private fun notifyPaused() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = launch?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(this, PresenceTracker.CHANNEL_PAUSED)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Фоновое отслеживание приостановлено")
            .setContentText("Android ограничивает фон (~6 ч/сутки). Откройте приложение, чтобы возобновить.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Система приостановила фоновое отслеживание (лимит фоновых сервисов ~6 ч в сутки). " +
                        "Откройте приложение, чтобы возобновить наблюдение за присутствием, гео, ключевыми словами и автоскачиванием.",
                ),
            )
            .setAutoCancel(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_PAUSED_ID, notification)
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
        private const val NOTIFICATION_PAUSED_ID = 1002

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PresenceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresenceService::class.java))
        }
    }
}
