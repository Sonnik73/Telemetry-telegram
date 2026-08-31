package com.sonnik.telemetry.presence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sonnik.telemetry.R
import com.sonnik.telemetry.td.TelegramClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.UserStatus
import dev.g000sha256.tdl.dto.UserStatusOffline
import dev.g000sha256.tdl.dto.UserStatusOnline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watches the online status of selected users. Subscribes to TDLib's
 * userStatusUpdates, records transitions into [PresenceStore], and posts a
 * notification when a watched user comes online. Collection lives for the whole
 * process; [PresenceService] keeps that process alive in the background.
 */
class PresenceTracker(
    private val context: Context,
    private val telegram: TelegramClient,
    val store: PresenceStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val _changed = MutableStateFlow(0L)
    /** Bumped whenever a watched user's status changes, so the UI can refresh. */
    val changed: StateFlow<Long> = _changed

    fun start() {
        if (started) return
        started = true
        ensureChannels()
        scope.launch {
            seedCurrentStatuses()
            telegram.client.userStatusUpdates.collect { update ->
                if (update.userId in store.watchedIds()) {
                    handleStatus(update.userId, update.status, notifyOnOnline = true)
                }
            }
        }
    }

    /** Adds a user to the watch list and immediately records their current status. */
    fun watch(userId: Long, title: String) {
        store.addWatched(userId, title)
        scope.launch {
            when (val result = telegram.client.getUser(userId)) {
                is TdlResult.Success -> handleStatus(userId, result.result.status, notifyOnOnline = false)
                is TdlResult.Failure -> Unit
            }
        }
        start()
    }

    fun unwatch(userId: Long) {
        store.removeWatched(userId)
        _changed.value = System.currentTimeMillis()
    }

    private suspend fun seedCurrentStatuses() {
        for (userId in store.watchedIds()) {
            when (val result = telegram.client.getUser(userId)) {
                is TdlResult.Success -> handleStatus(userId, result.result.status, notifyOnOnline = false)
                is TdlResult.Failure -> Unit
            }
        }
    }

    private fun handleStatus(userId: Long, status: UserStatus, notifyOnOnline: Boolean) {
        val online = status is UserStatusOnline
        val wasOnline = (status as? UserStatusOffline)?.wasOnline ?: 0
        val changed = store.recordIfChanged(userId, online, wasOnline)
        if (changed) {
            _changed.value = System.currentTimeMillis()
            if (online && notifyOnOnline) {
                notifyOnline(userId)
            }
        }
    }

    private fun notifyOnline(userId: Long) {
        // POST_NOTIFICATIONS is a runtime permission only on Android 13+; on older
        // versions notifications are allowed without it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val title = store.watchedTitle(userId) ?: "Контакт"
        val notification = NotificationCompat.Builder(context, CHANNEL_ONLINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$title в сети")
            .setContentText("Появился(ась) в Telegram")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(userId.toInt(), notification)
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONLINE, "Появление в сети", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Фоновое отслеживание", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PAUSED, "Отслеживание приостановлено", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_ONLINE = "online_alerts"
        const val CHANNEL_SERVICE = "presence_service"
        const val CHANNEL_PAUSED = "presence_paused"
    }
}
