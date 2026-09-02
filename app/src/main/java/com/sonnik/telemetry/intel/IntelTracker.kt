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
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentVideo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

    /** Days to keep captured media; 0 means keep forever. */
    fun captureRetentionDays(): Int = prefs.getInt(KEY_CAPTURE_DAYS, 0)

    fun setCaptureRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_CAPTURE_DAYS, days).apply()
        pruneCaptured()
    }

    /** Applies the retention policy, if one is set. */
    fun pruneCaptured() {
        val days = captureRetentionDays()
        if (days <= 0) return
        store.deleteCapturedOlderThan(System.currentTimeMillis() / 1000 - days * 86_400L)
    }

    @Volatile
    private var keywordCache: List<String> = emptyList()

    @Volatile
    private var exclusionCache: List<String> = emptyList()

    @Volatile
    private var chatFilterCache: Set<Long> = emptySet()

    @Volatile
    private var wholeWordCache: Boolean = false

    @Volatile
    private var typingAlertCache: Set<Long> = emptySet()

    @Volatile
    private var storyAutoCache: Set<Long> = emptySet()

    @Volatile
    private var myId: Long = 0L

    private val _liveTyping = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)
    val liveTyping: SharedFlow<TypingEvent> = _liveTyping

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

    fun wholeWordMode(): Boolean = prefs.getBoolean(KEY_KEYWORD_WHOLE_WORD, false)

    fun setWholeWordMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEYWORD_WHOLE_WORD, enabled).apply()
        wholeWordCache = enabled
    }

    fun exclusions(): List<String> =
        prefs.getStringSet(KEY_KEYWORD_EXCL, emptySet())!!.toList().sortedBy { it.lowercase() }

    fun addExclusion(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        val set = prefs.getStringSet(KEY_KEYWORD_EXCL, emptySet())!!.toMutableSet()
        set.add(w)
        prefs.edit().putStringSet(KEY_KEYWORD_EXCL, set).apply()
        exclusionCache = exclusions()
    }

    fun removeExclusion(word: String) {
        val set = prefs.getStringSet(KEY_KEYWORD_EXCL, emptySet())!!.toMutableSet()
        set.remove(word)
        prefs.edit().putStringSet(KEY_KEYWORD_EXCL, set).apply()
        exclusionCache = exclusions()
    }

    fun keywordChatFilter(): Set<Long> =
        prefs.getStringSet(KEY_KEYWORD_CHATS, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun setKeywordChatFilter(chatIds: Set<Long>) {
        prefs.edit().putStringSet(KEY_KEYWORD_CHATS, chatIds.map { it.toString() }.toSet()).apply()
        chatFilterCache = chatIds
    }

    fun typingAlertUsers(): Set<Long> =
        prefs.getStringSet(KEY_TYPING_ALERT_USERS, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun addTypingAlertUser(userId: Long) {
        val set = prefs.getStringSet(KEY_TYPING_ALERT_USERS, emptySet())!!.toMutableSet()
        set.add(userId.toString())
        prefs.edit().putStringSet(KEY_TYPING_ALERT_USERS, set).apply()
        typingAlertCache = typingAlertUsers()
    }

    fun removeTypingAlertUser(userId: Long) {
        val set = prefs.getStringSet(KEY_TYPING_ALERT_USERS, emptySet())!!.toMutableSet()
        set.remove(userId.toString())
        prefs.edit().putStringSet(KEY_TYPING_ALERT_USERS, set).apply()
        typingAlertCache = typingAlertUsers()
    }

    fun storyAutoUsers(): Set<Long> =
        prefs.getStringSet(KEY_STORY_AUTO, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun addStoryAutoUser(userId: Long) {
        val set = prefs.getStringSet(KEY_STORY_AUTO, emptySet())!!.toMutableSet()
        set.add(userId.toString())
        prefs.edit().putStringSet(KEY_STORY_AUTO, set).apply()
        storyAutoCache = storyAutoUsers()
    }

    fun removeStoryAutoUser(userId: Long) {
        val set = prefs.getStringSet(KEY_STORY_AUTO, emptySet())!!.toMutableSet()
        set.remove(userId.toString())
        prefs.edit().putStringSet(KEY_STORY_AUTO, set).apply()
        storyAutoCache = storyAutoUsers()
    }

    fun calendarReminders(): Boolean = prefs.getBoolean(KEY_CALENDAR_REMIND, false)

    fun setCalendarReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_REMIND, enabled).apply()
    }

    fun start() {
        if (started) return
        started = true
        keywordCache = keywords()
        exclusionCache = exclusions()
        chatFilterCache = keywordChatFilter()
        wholeWordCache = wholeWordMode()
        typingAlertCache = typingAlertUsers()
        storyAutoCache = storyAutoUsers()
        ensureChannel()

        scope.launch {
            myId = (telegram.client.getMe() as? TdlResult.Success)?.result?.id ?: 0L
        }
        scope.launch { storyAutoArchiveLoop() }
        scope.launch { calendarReminderLoop() }
        scope.launch {
            telegram.client.chatActionUpdates.collect { update ->
                val sender = (update.senderId as? MessageSenderUser)?.userId ?: return@collect
                if (sender == myId) return@collect
                val label = actionLabel(update.action) ?: return@collect
                val now = System.currentTimeMillis() / 1000
                _liveTyping.tryEmit(TypingEvent(update.chatId, sender, label, now))
                val key = "${update.chatId}:$sender:$label"
                val last = typingDedup[key] ?: 0L
                if (now - last < TYPING_DEBOUNCE_SEC) return@collect
                typingDedup[key] = now
                store.recordTyping(TypingEvent(update.chatId, sender, label, now))
                bump()
                if (sender in typingAlertCache) {
                    notifyTyping(sender, update.chatId, label)
                }
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
        if (chatFilterCache.isNotEmpty() && m.chatId !in chatFilterCache) return
        val text = rawText(m.content)
        if (text.isBlank()) return
        if (exclusionCache.any { text.contains(it, ignoreCase = true) }) return
        val hit = keywordCache.firstOrNull { kw ->
            if (kw.startsWith("/") && kw.endsWith("/") && kw.length > 2) {
                runCatching { Regex(kw.substring(1, kw.length - 1), RegexOption.IGNORE_CASE).containsMatchIn(text) }
                    .getOrDefault(false)
            } else if (wholeWordCache) {
                runCatching { Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) }
                    .getOrDefault(false)
            } else {
                text.contains(kw, ignoreCase = true)
            }
        } ?: return
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
        // Store encrypted at rest (".enc"); the inner extension is kept so the type
        // and a sensible filename can be restored when opening/saving.
        val out = File(dir, "cap_${m.chatId}_${m.id}_$fileId.$ext.enc")
        val ok = runCatching {
            com.sonnik.telemetry.security.FileCrypto.encryptFile(context, File(local), out)
        }.isSuccess
        if (!ok) return
        store.recordCaptured(
            m.chatId, senderId(m), System.currentTimeMillis() / 1000, type, out.absolutePath, rawText(m.content),
        )
        pruneCaptured()
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

    private suspend fun notifyTyping(senderId: Long, chatId: Long, action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val chats = TelemetryApp.instance.chats
        val sender = runCatching {
            chats.senderName(MessageSenderUser(senderId))
        }.getOrNull() ?: "Контакт"
        val chatName = runCatching { chats.getChat(chatId)?.title }.getOrNull()
        val notification = NotificationCompat.Builder(context, CHANNEL_TYPING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⌨ $sender $action")
            .setContentText(chatName ?: "")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextNotificationId(), notification) }
    }

    private suspend fun storyAutoArchiveLoop() {
        delay(60_000)
        while (true) {
            runCatching { archiveStories() }
            delay(30 * 60_000L)
        }
    }

    private suspend fun archiveStories() {
        val users = storyAutoCache
        if (users.isEmpty()) return
        for (uid in users) {
            val active = (telegram.client.getChatActiveStories(uid) as? TdlResult.Success)?.result
            val infos = active?.stories.orEmpty()
            for (info in infos) {
                if (store.isStoryArchived(uid, info.storyId)) continue
                val story = (telegram.client.getStory(uid, info.storyId, false) as? TdlResult.Success)?.result
                    ?: continue
                val (fileId, type, ext) = storyFileInfo(story.content) ?: continue
                val local = downloadToPath(fileId) ?: continue
                val dir = File(context.filesDir, "stories").apply { mkdirs() }
                val out = File(dir, "story_${uid}_${info.storyId}.$ext.enc")
                val ok = runCatching {
                    com.sonnik.telemetry.security.FileCrypto.encryptFile(context, File(local), out)
                }.isSuccess
                if (!ok) continue
                store.recordArchivedStory(uid, info.storyId, story.date.toLong(), type, out.absolutePath, story.caption.text)
                bump()
                notifyStory(uid, type)
            }
        }
    }

    private fun storyFileInfo(content: dev.g000sha256.tdl.dto.StoryContent): Triple<Int, String, String>? =
        when (content) {
            is StoryContentPhoto -> {
                val best = content.photo.sizes.maxByOrNull { it.width }
                best?.let { Triple(it.photo.id, "photo", "jpg") }
            }
            is StoryContentVideo -> Triple(content.video.video.id, "video", "mp4")
            else -> null
        }

    private suspend fun notifyStory(userId: Long, type: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val chats = TelemetryApp.instance.chats
        val name = runCatching { chats.senderName(MessageSenderUser(userId)) }.getOrNull() ?: "Контакт"
        val what = if (type == "video") "видео-историю" else "историю"
        val notification = NotificationCompat.Builder(context, CHANNEL_STORY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📖 $name выложил(а) $what")
            .setContentText("История сохранена автоматически")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextNotificationId(), notification) }
    }

    private suspend fun calendarReminderLoop() {
        delay(120_000)
        while (true) {
            if (calendarReminders()) {
                runCatching { checkCalendarReminders() }
            }
            store.clearOldEvents()
            delay(15 * 60_000L)
        }
    }

    private suspend fun checkCalendarReminders() {
        val oneHourAhead = System.currentTimeMillis() / 1000 + 3600
        val events = store.unremindedEvents(oneHourAhead)
        if (events.isEmpty()) return
        val chats = TelemetryApp.instance.chats
        val reminded = ArrayList<Long>()
        for (e in events) {
            val chatName = runCatching { chats.getChat(e.chatId)?.title }.getOrNull() ?: "чат"
            notifyCalendarReminder(e, chatName)
            reminded += e.id
        }
        store.markReminded(reminded)
    }

    private fun notifyCalendarReminder(event: CalendarEvent, chatName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_CALENDAR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📅 Скоро событие")
            .setContentText(event.snippet.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${event.snippet}\n\n💬 $chatName"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(nextNotificationId(), notification) }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INTEL, "Удаления и правки", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_KEYWORD, "Ключевые слова", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TYPING, "Печатает — уведомления", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STORY, "Истории контактов", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CALENDAR, "Напоминания о событиях", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun nextNotificationId(): Int = (System.currentTimeMillis() % 100000).toInt() + 200000

    private companion object {
        const val KEY_ALERTS = "intel_alerts"
        const val KEY_CAPTURE = "intel_capture"
        const val KEY_CAPTURE_DAYS = "intel_capture_days"
        const val KEY_KEYWORDS = "intel_keywords"
        const val KEY_KEYWORD_WHOLE_WORD = "intel_keyword_whole"
        const val KEY_KEYWORD_EXCL = "intel_keyword_excl"
        const val KEY_KEYWORD_CHATS = "intel_keyword_chats"
        const val KEY_TYPING_ALERT_USERS = "intel_typing_alerts"
        const val KEY_STORY_AUTO = "intel_story_auto"
        const val KEY_CALENDAR_REMIND = "intel_calendar_remind"
        const val CHANNEL_INTEL = "intel_alerts"
        const val CHANNEL_KEYWORD = "keyword_alerts"
        const val CHANNEL_TYPING = "typing_alerts"
        const val CHANNEL_STORY = "story_alerts"
        const val CHANNEL_CALENDAR = "calendar_alerts"
        const val TYPING_DEBOUNCE_SEC = 8L
    }
}

fun actionLabel(a: ChatAction): String? = when (a) {
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
