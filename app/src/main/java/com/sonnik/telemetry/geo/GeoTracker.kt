package com.sonnik.telemetry.geo

import com.sonnik.telemetry.data.ChatRepository
import com.sonnik.telemetry.td.TelegramClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageLiveLocation
import dev.g000sha256.tdl.dto.MessageSenderUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A currently-active live-location share and its latest known point. */
data class GeoShare(
    val chatId: Long,
    val messageId: Long,
    val userId: Long,
    val title: String,
    val lat: Double,
    val lon: Double,
    val heading: Int,
    val accuracy: Double,
    val updatedAt: Long,
    val expiresAt: Long,
)

/**
 * Watches every live-location share visible to the account. TDLib pushes the
 * active set through activeLiveLocationMessagesUpdates and each movement through
 * messageContentUpdates, so no polling is needed — points are recorded into
 * [GeoStore] as they arrive.
 */
class GeoTracker(
    private val telegram: TelegramClient,
    private val chats: ChatRepository,
    val store: GeoStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    // Keyed by "chatId:messageId".
    private val shares = HashMap<String, GeoShare>()
    private val _active = MutableStateFlow<List<GeoShare>>(emptyList())
    val active: StateFlow<List<GeoShare>> = _active

    fun start() {
        if (started) return
        started = true
        scope.launch {
            telegram.client.activeLiveLocationMessagesUpdates.collect { update ->
                // Full authoritative set of active shares; drop any that ended.
                val keep = HashSet<String>()
                for (message in update.messages) {
                    ingest(message)?.let { keep += key(it.chatId, it.messageId) }
                }
                synchronized(shares) {
                    shares.keys.retainAll(keep)
                    publish()
                }
            }
        }
        scope.launch {
            telegram.client.newMessageUpdates.collect { update -> ingest(update.message) }
        }
        scope.launch {
            telegram.client.messageContentUpdates.collect { update ->
                val content = update.newContent
                if (content is MessageLiveLocation) {
                    val existing = synchronized(shares) { shares[key(update.chatId, update.messageId)] }
                    val userId = existing?.userId ?: 0L
                    val title = existing?.title ?: resolveTitle(update.chatId)
                    record(update.chatId, update.messageId, userId, title, content)
                }
            }
        }
    }

    /** Extracts a live-location share from a message, recording its point. */
    private suspend fun ingest(message: Message): GeoShare? {
        val content = message.content
        if (content !is MessageLiveLocation) return null
        val userId = (message.senderId as? MessageSenderUser)?.userId ?: 0L
        val title = resolveTitle(message.chatId)
        return record(message.chatId, message.id, userId, title, content)
    }

    private fun record(chatId: Long, messageId: Long, userId: Long, title: String, content: MessageLiveLocation): GeoShare {
        val loc = content.location
        val now = System.currentTimeMillis() / 1000
        val point = GeoPoint(
            at = now,
            lat = loc.location.latitude,
            lon = loc.location.longitude,
            heading = loc.heading,
            accuracy = loc.location.horizontalAccuracy,
        )
        store.record(chatId, messageId, userId, point)
        val share = GeoShare(
            chatId = chatId,
            messageId = messageId,
            userId = userId,
            title = title,
            lat = point.lat,
            lon = point.lon,
            heading = point.heading,
            accuracy = point.accuracy,
            updatedAt = now,
            expiresAt = now + content.expiresIn,
        )
        synchronized(shares) {
            shares[key(chatId, messageId)] = share
            publish()
        }
        return share
    }

    private suspend fun resolveTitle(chatId: Long): String =
        chats.getChat(chatId)?.title ?: "Чат $chatId"

    private fun publish() {
        _active.value = shares.values.sortedByDescending { it.updatedAt }
    }

    private fun key(chatId: Long, messageId: Long) = "$chatId:$messageId"
}
