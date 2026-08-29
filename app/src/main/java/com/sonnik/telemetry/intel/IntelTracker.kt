package com.sonnik.telemetry.intel

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
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.td.TelegramClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Background collector that powers the deleted-message archive, edit history and
 * contact-change log. It caches every incoming message, diffs content updates to
 * catch edits, keeps content when a message is deleted, and logs profile changes
 * from user updates. Runs for the whole process; the presence foreground service
 * keeps it alive in the background.
 */
class IntelTracker(
    private val context: Context,
    private val telegram: TelegramClient,
    val store: ArchiveStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val prefs = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    private val _changed = MutableStateFlow(0L)
    /** Bumped whenever an event is recorded, so open screens can refresh. */
    val changed: StateFlow<Long> = _changed

    /** Whether to post a notification when a message is deleted or edited. */
    fun alertsEnabled(): Boolean = prefs.getBoolean(KEY_ALERTS, false)

    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS, enabled).apply()
    }

    fun start() {
        if (started) return
        started = true
        ensureChannel()

        scope.launch {
            telegram.client.newMessageUpdates.collect { update ->
                val m = update.message
                store.cache(m.chatId, m.id, senderId(m), m.date, messageBody(m.content))
            }
        }
        scope.launch {
            telegram.client.messageContentUpdates.collect { update ->
                val newBody = messageBody(update.newContent)
                val cached = store.cachedBody(update.chatId, update.messageId)
                if (cached != null && cached.second != newBody) {
                    store.recordEvent(
                        ArchiveEvent(
                            kind = "edited",
                            chatId = update.chatId,
                            messageId = update.messageId,
                            senderId = cached.first,
                            at = System.currentTimeMillis() / 1000,
                            oldBody = cached.second,
                            newBody = newBody,
                        ),
                    )
                    store.cache(update.chatId, update.messageId, cached.first, 0, newBody)
                    bump()
                    notify("edited", cached.first, update.chatId, cached.second, newBody)
                }
            }
        }
        scope.launch {
            telegram.client.deleteMessagesUpdates.collect { update ->
                // fromCache = local eviction, not a real deletion; ignore those.
                if (!update.isPermanent || update.fromCache) return@collect
                for (id in update.messageIds) {
                    val cached = store.cachedBody(update.chatId, id) ?: continue
                    store.recordEvent(
                        ArchiveEvent(
                            kind = "deleted",
                            chatId = update.chatId,
                            messageId = id,
                            senderId = cached.first,
                            at = System.currentTimeMillis() / 1000,
                            oldBody = cached.second,
                            newBody = "",
                        ),
                    )
                    notify("deleted", cached.first, update.chatId, cached.second, "")
                }
                bump()
            }
        }
        scope.launch {
            telegram.client.userUpdates.collect { update ->
                val u = update.user
                val name = listOf(u.firstName, u.lastName).filter(String::isNotBlank).joinToString(" ")
                val username = u.usernames?.activeUsernames?.firstOrNull() ?: ""
                val photoId = u.profilePhoto?.id ?: 0L
                if (store.diffContact(u.id, name, username, photoId).isNotEmpty()) bump()
            }
        }
    }

    private fun senderId(m: Message): Long = when (val s = m.senderId) {
        is MessageSenderUser -> s.userId
        is MessageSenderChat -> s.chatId
        else -> 0L
    }

    private fun bump() {
        _changed.value = System.currentTimeMillis()
    }

    /** Posts a delete/edit alert if enabled and notifications are permitted. */
    private suspend fun notify(kind: String, senderId: Long, chatId: Long, oldBody: String, newBody: String) {
        if (!alertsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val chats = TelemetryApp.instance.chats
        val sender = runCatching {
            chats.senderName(
                if (senderId > 0) dev.g000sha256.tdl.dto.MessageSenderUser(senderId)
                else dev.g000sha256.tdl.dto.MessageSenderChat(senderId),
            )
        }.getOrNull() ?: "Контакт"
        val chatName = runCatching { chats.getChat(chatId)?.title }.getOrNull()
        val deleted = kind == "deleted"
        val title = (if (deleted) "🗑 Удалено" else "✏️ Изменено") +
            " · $sender" + (if (chatName != null) " ($chatName)" else "")
        val text = if (deleted) oldBody.take(200) else "${oldBody.take(120)} → ${newBody.take(120)}"
        val notification = NotificationCompat.Builder(context, CHANNEL_INTEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(nextNotificationId(), notification)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INTEL, "Удаления и правки", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun nextNotificationId(): Int = (System.currentTimeMillis() % 100000).toInt() + 200000

    private companion object {
        const val KEY_ALERTS = "intel_alerts"
        const val CHANNEL_INTEL = "intel_alerts"
    }
}

/** Short human-readable one-line summary of a message's content. */
fun messageBody(content: MessageContent): String = when (content) {
    is MessageText -> content.text.text
    is MessagePhoto -> "🖼 Фото" + suffix(content.caption.text)
    is MessageVideo -> "🎬 Видео" + suffix(content.caption.text)
    is MessageDocument -> "📎 " + content.document.fileName.ifBlank { "файл" } + suffix(content.caption.text)
    is MessageAudio -> "🎵 " + content.audio.title.ifBlank { "аудио" }
    is MessageVoiceNote -> "🎤 Голосовое" + suffix(content.caption.text)
    is MessageVideoNote -> "⭕ Видеосообщение"
    is MessageAnimation -> "GIF" + suffix(content.caption.text)
    is MessageSticker -> content.sticker.emoji + " стикер"
    else -> "[" + (content::class.simpleName?.removePrefix("Message") ?: "сообщение") + "]"
}

private fun suffix(caption: String): String = if (caption.isNotBlank()) ": $caption" else ""
