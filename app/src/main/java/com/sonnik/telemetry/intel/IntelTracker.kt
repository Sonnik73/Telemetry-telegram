package com.sonnik.telemetry.intel

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
    private val telegram: TelegramClient,
    val store: ArchiveStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val _changed = MutableStateFlow(0L)
    /** Bumped whenever an event is recorded, so open screens can refresh. */
    val changed: StateFlow<Long> = _changed

    fun start() {
        if (started) return
        started = true

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
