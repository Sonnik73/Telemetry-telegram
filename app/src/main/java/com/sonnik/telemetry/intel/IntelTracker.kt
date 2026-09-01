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
import dev.g000sha256.tdl.dto.ChatAction
import dev.g000sha256.tdl.dto.ChatActionCancel
import dev.g000sha256.tdl.dto.ChatActionChoosingContact
import dev.g000sha256.tdl.dto.ChatActionChoosingLocation
import dev.g000sha256.tdl.dto.ChatActionChoosingSticker
import dev.g000sha256.tdl.dto.ChatActionRecordingVideo
import dev.g000sha256.tdl.dto.ChatActionRecordingVideoNote
import dev.g000sha256.tdl.dto.ChatActionRecordingVoiceNote
import dev.g000sha256.tdl.dto.ChatActionStartPlayingGame
import dev.g000sha256.tdl.dto.ChatActionTyping
import dev.g000sha256.tdl.dto.ChatActionUploadingDocument
import dev.g000sha256.tdl.dto.ChatActionUploadingPhoto
import dev.g000sha256.tdl.dto.ChatActionUploadingVideo
import dev.g000sha256.tdl.dto.ChatActionUploadingVideoNote
import dev.g000sha256.tdl.dto.ChatActionUploadingVoiceNote
import dev.g000sha256.tdl.dto.ChatActionWatchingAnimations
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
import java.io.File
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

    /** Whether to auto-save incoming self-destructing photos/videos before they vanish. */
    fun captureEnabled(): Boolean = prefs.getBoolean(KEY_CAPTURE, true)

    fun setCaptureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAPTURE, enabled).apply()
    }

    @Volatile
    private var keywordCache: List<String> = emptyList()

    @Volatile
    private var myId: Long = 0L

    /** Debounces repeated action updates (same sender+chat+action within a few seconds). */
    private val typingDedup = HashMap<String, Long>()

    /** Keywords tracked in incoming messages across all chats/channels. */
    fun keywords(): List<String> =
        prefs.getStringSet(KEY_KEYWORDS, emptySet())!!.toList().sortedBy { it.lowercase() }

    fun addKeyword(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        val set = prefs.getStringSet(KEY_KEYWORDS, emptySet())!!.toMutableSet()
        set.add(w)
        prefs.edit().putStringSet(KEY_KEYWORDS, set).apply()
        keywordCache = keywords()
    }

    fun removeKeyword(word: String) {
        val set = prefs.getStringSet(KEY_KEYWORDS, emptySet())!!.toMutableSet()
        set.remove(word)
        prefs.edit().putStringSet(KEY_KEYWORDS, set).apply()
        keywordCache = keywords()
    }

    fun start() {
        if (started) return
        started = true
        keywordCache = keywords()
        ensureChannel()

        scope.launch {
            myId = (telegram.client.getMe() as? dev.g000sha256.tdl.TdlResult.Success)?.result?.id ?: 0L
        }
        scope.launch {
            telegram.client.chatActionUpdates.collect { update ->
                val sender = (update.senderId as? MessageSenderUser)?.userId ?: return@collect
                if (sender == myId) return@collect // ignore my own actions
                val label = actionLabel(update.action) ?: return@collect
                val now = System.currentTimeMillis() / 1000
                val key = "${update.chatId}:$sender:$label"
                val last = typingDedup[key] ?: 0L
                if (now - last < TYPING_DEBOUNCE_SEC) return@collect
                typingDedup[key] = now
                store.recordTyping(TypingEvent(update.chatId, sender, label, now))
                bump()
            }
        }
        scope.launch {
            telegram.client.newMessageUpdates.collect { update ->
                val m = update.message
                store.cache(m.chatId, m.id, senderId(m), m.date, messageBody(m.content))
                matchKeywords(m)
                captureSelfDestruct(m)
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

    /** Checks a new message against tracked keywords; records and notifies on a match. */
    private suspend fun matchKeywords(m: Message) {
        if (keywordCache.isEmpty()) return
        val text = rawText(m.content)
        if (text.isBlank()) return
        val hit = keywordCache.firstOrNull { text.contains(it, ignoreCase = true) } ?: return
        val snippet = messageBody(m.content)
        store.recordKeywordHit(
            KeywordHit(
                chatId = m.chatId,
                messageId = m.id,
                senderId = senderId(m),
                at = System.currentTimeMillis() / 1000,
                keyword = hit,
                body = snippet,
            ),
        )
        bump()
        notifyKeyword(hit, senderId(m), m.chatId, snippet)
    }

    /**
     * Captures an incoming self-destructing photo/video/voice by downloading it and
     * copying it into private app storage before Telegram deletes it.
     */
    private suspend fun captureSelfDestruct(m: Message) {
        if (!captureEnabled()) return
        if (m.selfDestructType == null || m.isOutgoing) return
        val target = selfDestructFile(m.content) ?: return
        val (fileId, type, ext) = target
        val local = downloadToPath(fileId) ?: return
        val dir = File(context.filesDir, "captured").apply { mkdirs() }
        val out = File(dir, "cap_${m.chatId}_${m.id}_$fileId.$ext")
        val ok = runCatching {
            File(local).inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        }.isSuccess
        if (!ok) return
        store.recordCaptured(
            m.chatId, senderId(m), System.currentTimeMillis() / 1000, type, out.absolutePath, rawText(m.content),
        )
        bump()
        notifyCaptured(senderId(m), m.chatId, type)
    }

    private fun selfDestructFile(content: MessageContent): Triple<Int, String, String>? = when (content) {
        is MessagePhoto -> content.photo.sizes.maxByOrNull { it.width }?.photo?.id?.let { Triple(it, "photo", "jpg") }
        is MessageVideo -> Triple(content.video.video.id, "video", "mp4")
        is MessageVoiceNote -> Triple(content.voiceNote.voice.id, "voice", "ogg")
        is MessageVideoNote -> Triple(content.videoNote.video.id, "videonote", "mp4")
        is MessageAnimation -> Triple(content.animation.animation.id, "gif", "mp4")
        else -> null
    }

    private suspend fun downloadToPath(fileId: Int): String? {
        val result = telegram.client.downloadFile(fileId, priority = 32, offset = 0, limit = 0, synchronous = true)
        val file = (result as? dev.g000sha256.tdl.TdlResult.Success)?.result ?: return null
        return if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) file.local.path else null
    }

    private suspend fun notifyCaptured(senderId: Long, chatId: Long, type: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val chats = TelemetryApp.instance.chats
        val sender = runCatching {
            chats.senderName(
                if (senderId > 0) MessageSenderUser(senderId) else MessageSenderChat(senderId),
            )
        }.getOrNull() ?: "Контакт"
        val what = when (type) {
            "video" -> "видео"
            "voice" -> "голосовое"
            "videonote" -> "кружок"
            "gif" -> "GIF"
            else -> "фото"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_INTEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📸 Перехвачено одноразовое $what")
            .setContentText("от $sender")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextNotificationId(), notification) }
    }

    private fun rawText(content: MessageContent): String = when (content) {
        is MessageText -> content.text.text
        is MessagePhoto -> content.caption.text
        is MessageVideo -> content.caption.text
        is MessageDocument -> content.caption.text
        is MessageAudio -> content.caption.text
        is MessageVoiceNote -> content.caption.text
        is MessageAnimation -> content.caption.text
        else -> ""
    }

    private suspend fun notifyKeyword(keyword: String, senderId: Long, chatId: Long, snippet: String) {
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
        }.getOrNull() ?: ""
        val chatName = runCatching { chats.getChat(chatId)?.title }.getOrNull()
        val where = listOfNotNull(chatName, sender.ifBlank { null }).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_KEYWORD)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔎 «$keyword»" + if (where.isNotEmpty()) " — $where" else "")
            .setContentText(snippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText(snippet))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(nextNotificationId(), notification)
        }
    }

    /** Human-readable label for a chat action, or null for ones we don't record. */
    private fun actionLabel(a: ChatAction): String? = when (a) {
        is ChatActionTyping -> "печатает"
        is ChatActionRecordingVoiceNote -> "записывает голосовое"
        is ChatActionRecordingVideoNote -> "записывает кружок"
        is ChatActionRecordingVideo -> "записывает видео"
        is ChatActionUploadingPhoto -> "отправляет фото"
        is ChatActionUploadingVideo -> "отправляет видео"
        is ChatActionUploadingVoiceNote -> "отправляет голосовое"
        is ChatActionUploadingVideoNote -> "отправляет кружок"
        is ChatActionUploadingDocument -> "отправляет файл"
        is ChatActionChoosingSticker -> "выбирает стикер"
        is ChatActionChoosingLocation -> "выбирает геопозицию"
        is ChatActionChoosingContact -> "выбирает контакт"
        is ChatActionStartPlayingGame -> "играет"
        is ChatActionWatchingAnimations -> "смотрит анимации"
        is ChatActionCancel -> null
        else -> null
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INTEL, "Удаления и правки", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_KEYWORD, "Ключевые слова", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun nextNotificationId(): Int = (System.currentTimeMillis() % 100000).toInt() + 200000

    private companion object {
        const val KEY_ALERTS = "intel_alerts"
        const val KEY_CAPTURE = "intel_capture"
        const val KEY_KEYWORDS = "intel_keywords"
        const val CHANNEL_INTEL = "intel_alerts"
        const val CHANNEL_KEYWORD = "keyword_alerts"
        const val TYPING_DEBOUNCE_SEC = 8L
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
