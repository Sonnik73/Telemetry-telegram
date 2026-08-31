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
import dev.g000sha256.tdl.dto.StoryContent
import dev.g000sha256.tdl.dto.User
import dev.g000sha256.tdl.dto.UserTypeDeleted

enum class ChatKind { PRIVATE, GROUP, CHANNEL, SECRET }

/** One active story published by a contact, carrying its full content for download. */
data class ContactStoryItem(
    val posterChatId: Long,
    val storyId: Int,
    val date: Int,
    val caption: String,
    val content: StoryContent,
)

/** A contact who currently has one or more active (not-yet-expired) stories. */
data class ContactStoryGroup(
    val chatId: Long,
    val name: String,
    val stories: List<ContactStoryItem>,
)

data class WatchCandidate(val userId: Long, val title: String)

/** Result of a contact-status scan: whether a contact is deleted or dropped you. */
enum class ContactStatusKind { DELETED, NOT_IN_THEIR_CONTACTS, OK }

data class ContactStatus(val userId: Long, val name: String, val kind: ContactStatusKind)

/** One interaction with your own story (a view, optionally with a reaction, or a repost/forward). */
data class StoryViewerInfo(
    val storyId: Int,
    val name: String,
    val date: Int,
    val reaction: String?,
    val kind: String, // "view" | "repost" | "forward"
)

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
     * Scans the address book and classifies each contact:
     *  - DELETED — the account was deleted;
     *  - NOT_IN_THEIR_CONTACTS — you have them, but they don't have you (they removed
     *    you from contacts or blocked you; this is a heuristic, not a certainty);
     *  - OK — mutual/normal.
     */
    suspend fun scanContacts(onProgress: (Int, Int) -> Unit): List<ContactStatus> {
        val ids = when (val r = client.getContacts()) {
            is TdlResult.Success -> r.result.userIds.toList()
            is TdlResult.Failure -> return emptyList()
        }
        val result = ArrayList<ContactStatus>()
        ids.forEachIndexed { index, id ->
            val user = (client.getUser(id) as? TdlResult.Success)?.result
            if (user != null) {
                val kind = when {
                    user.type is UserTypeDeleted -> ContactStatusKind.DELETED
                    user.isContact && !user.isMutualContact -> ContactStatusKind.NOT_IN_THEIR_CONTACTS
                    else -> ContactStatusKind.OK
                }
                result += ContactStatus(id, userDisplayName(user), kind)
            }
            onProgress(index + 1, ids.size)
        }
        return result
    }

    /**
     * Fetches viewers of your own currently-active stories: who watched them, when,
     * and whether they reacted. Empty when you have no active stories.
     */
    suspend fun myStoryViewers(): List<StoryViewerInfo> {
        val me = (client.getMe() as? TdlResult.Success)?.result ?: return emptyList()
        val active = when (val r = client.getChatActiveStories(me.id)) {
            is TdlResult.Success -> r.result.stories
            is TdlResult.Failure -> return emptyList()
        }
        val out = ArrayList<StoryViewerInfo>()
        for (info in active) {
            var offset = ""
            var pages = 0
            while (pages < 20) {
                pages++
                val res = when (
                    val r = client.getStoryInteractions(
                        storyId = info.storyId,
                        query = "",
                        onlyContacts = false,
                        preferForwards = false,
                        preferWithReaction = false,
                        offset = offset,
                        limit = 100,
                    )
                ) {
                    is TdlResult.Success -> r.result
                    is TdlResult.Failure -> break
                }
                for (i in res.interactions) {
                    val kind = when (i.type) {
                        is dev.g000sha256.tdl.dto.StoryInteractionTypeForward -> "forward"
                        is dev.g000sha256.tdl.dto.StoryInteractionTypeRepost -> "repost"
                        else -> "view"
                    }
                    val reaction = (i.type as? dev.g000sha256.tdl.dto.StoryInteractionTypeView)
                        ?.chosenReactionType?.let { reactionEmoji(it) }
                    out += StoryViewerInfo(info.storyId, senderName(i.actorId), i.interactionDate, reaction, kind)
                }
                if (res.nextOffset.isEmpty()) break else offset = res.nextOffset
            }
        }
        return out
    }

    private fun reactionEmoji(type: dev.g000sha256.tdl.dto.ReactionType): String? =
        (type as? dev.g000sha256.tdl.dto.ReactionTypeEmoji)?.emoji

    /**
     * Collects the currently-active stories published by your contacts, with each
     * story's full content so the UI can preview and download it — including stories
     * whose author disabled saving (TDLib fetches the media regardless of that flag).
     * A contact's private chat id equals their user id, so getChatActiveStories is
     * queried per contact. Stories are only visible until they expire (~24h).
     */
    suspend fun contactStories(onProgress: (Int, Int) -> Unit): List<ContactStoryGroup> {
        val ids = when (val r = client.getContacts()) {
            is TdlResult.Success -> r.result.userIds.toList()
            is TdlResult.Failure -> return emptyList()
        }
        val groups = ArrayList<ContactStoryGroup>()
        ids.forEachIndexed { index, uid ->
            val active = (client.getChatActiveStories(uid) as? TdlResult.Success)?.result
            val infos = active?.stories.orEmpty()
            if (infos.isNotEmpty()) {
                val items = ArrayList<ContactStoryItem>()
                for (info in infos) {
                    val story = (client.getStory(uid, info.storyId, false) as? TdlResult.Success)?.result
                        ?: continue
                    items += ContactStoryItem(uid, info.storyId, story.date, story.caption.text, story.content)
                }
                if (items.isNotEmpty()) {
                    val user = (client.getUser(uid) as? TdlResult.Success)?.result
                    val name = user?.let { userDisplayName(it) } ?: "ID $uid"
                    groups += ContactStoryGroup(uid, name, items)
                }
            }
            onProgress(index + 1, ids.size)
        }
        return groups.sortedByDescending { g -> g.stories.maxOfOrNull { it.date } ?: 0 }
    }

    private fun userDisplayName(user: User): String {
        val name = listOf(user.firstName, user.lastName).filter(String::isNotBlank).joinToString(" ")
        return when {
            name.isNotBlank() -> name
            user.usernames?.activeUsernames?.firstOrNull() != null -> "@${user.usernames!!.activeUsernames.first()}"
            user.phoneNumber.isNotBlank() -> "+${user.phoneNumber}"
            else -> "ID ${user.id}"
        }
    }

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
