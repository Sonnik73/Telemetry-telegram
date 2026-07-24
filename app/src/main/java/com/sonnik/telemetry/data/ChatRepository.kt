package com.sonnik.telemetry.data

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser

enum class ChatKind { PRIVATE, GROUP, CHANNEL, SECRET }

data class WatchCandidate(val userId: Long, val title: String)

data class ChatSummary(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val memberCount: Int?,
    val unreadCount: Int,
    val lastMessageDate: Int,
)

class ChatRepository(private val client: TdlClient) {

    /**
     * Loads the full main chat list. TDLib pages chats in via loadChats and
     * reports 404 once every chat is loaded, after which getChats returns the
     * complete ordered id list.
     */
    suspend fun loadAllChats(): Result<List<ChatSummary>> {
        while (true) {
            when (val result = client.loadChats(ChatListMain(), limit = 200)) {
                is TdlResult.Success -> Unit
                is TdlResult.Failure ->
                    if (result.code == 404) break
                    else return Result.failure(Exception("${result.code}: ${result.message}"))
            }
        }
        val chatIds = when (val result = client.getChats(ChatListMain(), limit = 10_000)) {
            is TdlResult.Success -> result.result.chatIds
            is TdlResult.Failure -> return Result.failure(Exception("${result.code}: ${result.message}"))
        }
        val chats = ArrayList<ChatSummary>(chatIds.size)
        for (chatId in chatIds) {
            val chat = when (val result = client.getChat(chatId)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> continue
            }
            chats += chat.toSummary(memberCount = memberCountOf(chat))
        }
        return Result.success(chats)
    }

    /** Private-chat counterparts (user id + display name) usable as watch targets. */
    suspend fun privateUsers(): Result<List<WatchCandidate>> {
        val chats = loadAllChats().getOrElse { return Result.failure(it) }
        val candidates = ArrayList<WatchCandidate>()
        for (summary in chats) {
            if (summary.kind != ChatKind.PRIVATE) continue
            val chat = when (val result = client.getChat(summary.id)) {
                is TdlResult.Success -> result.result
                is TdlResult.Failure -> continue
            }
            val type = chat.type
            if (type is ChatTypePrivate) {
                candidates += WatchCandidate(userId = type.userId, title = summary.title)
            }
        }
        return Result.success(candidates)
    }

    suspend fun getChat(chatId: Long): ChatSummary? {
        val chat = when (val result = client.getChat(chatId)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return null
        }
        return chat.toSummary(memberCount = memberCountOf(chat))
    }

    /** Resolves a human-readable name for a message sender (user or chat). */
    suspend fun senderName(senderId: MessageSender): String {
        return when (senderId) {
            is MessageSenderUser -> when (val result = client.getUser(senderId.userId)) {
                is TdlResult.Success ->
                    listOf(result.result.firstName, result.result.lastName)
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                        .ifBlank { "User ${senderId.userId}" }
                is TdlResult.Failure -> "User ${senderId.userId}"
            }
            is MessageSenderChat -> when (val result = client.getChat(senderId.chatId)) {
                is TdlResult.Success -> result.result.title.ifBlank { "Chat ${senderId.chatId}" }
                is TdlResult.Failure -> "Chat ${senderId.chatId}"
            }
            else -> "Unknown"
        }
    }

    private suspend fun memberCountOf(chat: Chat): Int? {
        return when (val type = chat.type) {
            is ChatTypeSupergroup -> when (val result = client.getSupergroup(type.supergroupId)) {
                is TdlResult.Success -> result.result.memberCount.takeIf { it > 0 }
                is TdlResult.Failure -> null
            }
            is ChatTypeBasicGroup -> when (val result = client.getBasicGroupFullInfo(type.basicGroupId)) {
                is TdlResult.Success -> result.result.members.size
                is TdlResult.Failure -> null
            }
            else -> null
        }
    }

    private fun Chat.toSummary(memberCount: Int?): ChatSummary {
        val kind = when (val t = type) {
            is ChatTypePrivate -> ChatKind.PRIVATE
            is ChatTypeSecret -> ChatKind.SECRET
            is ChatTypeBasicGroup -> ChatKind.GROUP
            is ChatTypeSupergroup -> if (t.isChannel) ChatKind.CHANNEL else ChatKind.GROUP
            else -> ChatKind.PRIVATE
        }
        return ChatSummary(
            id = id,
            title = title.ifBlank { "Chat $id" },
            kind = kind,
            memberCount = memberCount,
            unreadCount = unreadCount,
            lastMessageDate = lastMessage?.date ?: 0,
        )
    }
}
