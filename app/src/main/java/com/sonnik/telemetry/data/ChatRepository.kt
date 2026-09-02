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
import dev.g000sha256.tdl.dto.UserStatus
import dev.g000sha256.tdl.dto.UserStatusLastMonth
import dev.g000sha256.tdl.dto.UserStatusLastWeek
import dev.g000sha256.tdl.dto.UserStatusOffline
import dev.g000sha256.tdl.dto.UserStatusOnline
import dev.g000sha256.tdl.dto.UserStatusRecently
import dev.g000sha256.tdl.dto.UserTypeDeleted

enum class ChatKind { PRIVATE, GROUP, CHANNEL, SECRET }

/** A user matched by phone-number lookup. */
data class PhoneMatch(val userId: Long, val name: String, val username: String?, val phone: String)

/** Coarse "last seen" bucket for a contact, from their privacy-limited status. */
enum class SeenKind { ONLINE, OFFLINE, RECENTLY, LAST_WEEK, LAST_MONTH, LONG_AGO }

data class LastSeenEntry(val userId: Long, val name: String, val kind: SeenKind, val wasOnline: Int)

/** A node (contact) in the acquaintance graph, with how many other contacts it links to. */
data class GraphNode(val userId: Long, val name: String, val degree: Int)

/** Contacts and the edges between those who share at least one common group. */
data class ContactGraph(val nodes: List<GraphNode>, val edges: List<Pair<Int, Int>>)

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

    /**
     * Resolves a Telegram account by phone number (server lookup). Returns null when
     * no account is found or the owner's privacy settings hide them from lookup.
     */
    suspend fun lookupByPhone(phone: String): PhoneMatch? {
        val clean = phone.trim()
        if (clean.length < 3) return null
        val user = (client.searchUserByPhoneNumber(clean, false) as? TdlResult.Success)?.result ?: return null
        return PhoneMatch(
            user.id,
            userDisplayName(user),
            user.usernames?.activeUsernames?.firstOrNull(),
            user.phoneNumber.ifBlank { clean },
        )
    }

    /**
     * Snapshots the "last seen" status of every contact, sorted most-recent first.
     * Precise timestamps are only available for contacts who don't hide them.
     */
    suspend fun contactsLastSeen(onProgress: (Int, Int) -> Unit): List<LastSeenEntry> {
        val ids = when (val r = client.getContacts()) {
            is TdlResult.Success -> r.result.userIds.toList()
            is TdlResult.Failure -> return emptyList()
        }
        val out = ArrayList<LastSeenEntry>()
        ids.forEachIndexed { index, uid ->
            val user = (client.getUser(uid) as? TdlResult.Success)?.result
            if (user != null && user.type !is UserTypeDeleted) {
                val (kind, was) = classifyStatus(user.status)
                out += LastSeenEntry(uid, userDisplayName(user), kind, was)
            }
            onProgress(index + 1, ids.size)
        }
        return out.sortedByDescending { seenRank(it) }
    }

    fun classifyStatus(status: UserStatus): Pair<SeenKind, Int> = when (status) {
        is UserStatusOnline -> SeenKind.ONLINE to 0
        is UserStatusOffline -> SeenKind.OFFLINE to status.wasOnline
        is UserStatusRecently -> SeenKind.RECENTLY to 0
        is UserStatusLastWeek -> SeenKind.LAST_WEEK to 0
        is UserStatusLastMonth -> SeenKind.LAST_MONTH to 0
        else -> SeenKind.LONG_AGO to 0
    }

    private fun seenRank(e: LastSeenEntry): Long = when (e.kind) {
        SeenKind.ONLINE -> Long.MAX_VALUE
        SeenKind.OFFLINE -> e.wasOnline.toLong()
        SeenKind.RECENTLY -> -1L
        SeenKind.LAST_WEEK -> -2L
        SeenKind.LAST_MONTH -> -3L
        SeenKind.LONG_AGO -> -4L
    }

    /**
     * Builds an acquaintance graph of your contacts: two contacts are linked when
     * they share at least one group in common with you. Heavy (a getGroupsInCommon
     * call per contact), so it reports progress.
     */
    suspend fun buildContactGraph(onProgress: (Int, Int) -> Unit): ContactGraph {
        val ids = when (val r = client.getContacts()) {
            is TdlResult.Success -> r.result.userIds.toList()
            is TdlResult.Failure -> return ContactGraph(emptyList(), emptyList())
        }
        val userIds = ArrayList<Long>()
        val names = ArrayList<String>()
        val groupSets = ArrayList<Set<Long>>()
        ids.forEachIndexed { index, uid ->
            val user = (client.getUser(uid) as? TdlResult.Success)?.result
            if (user != null && user.type !is UserTypeDeleted) {
                userIds += uid
                names += userDisplayName(user)
                groupSets += commonGroupIds(uid)
            }
            onProgress(index + 1, ids.size)
        }
        val edges = ArrayList<Pair<Int, Int>>()
        val degree = IntArray(userIds.size)
        for (i in userIds.indices) {
            for (j in i + 1 until userIds.size) {
                if (groupSets[i].isNotEmpty() && groupSets[i].any { it in groupSets[j] }) {
                    edges += i to j
                    degree[i]++
                    degree[j]++
                }
            }
        }
        val nodes = userIds.indices.map { GraphNode(userIds[it], names[it], degree[it]) }
        return ContactGraph(nodes, edges)
    }

    private suspend fun commonGroupIds(userId: Long): Set<Long> {
        val out = HashSet<Long>()
        var offset = 0L
        while (true) {
            val chats = (client.getGroupsInCommon(userId, offset, 100) as? TdlResult.Success)?.result ?: break
            if (chats.chatIds.isEmpty()) break
            chats.chatIds.forEach { out += it }
            offset = chats.chatIds.last()
            if (chats.chatIds.size < 100) break
        }
        return out
    }

    /** The signed-in user's own id, or 0 if unavailable. */
    suspend fun meId(): Long = (client.getMe() as? TdlResult.Success)?.result?.id ?: 0L

    /**
     * Deletes your own messages in one chat, for everyone. Supergroups/channels use
     * the server-side bulk delete-by-sender; other chats page through your outgoing
     * messages and delete them in batches. Returns the number deleted, or -1 when a
     * bulk delete was issued (exact count unknown). Only ever touches your messages.
     */
    /**
     * Server-side estimate of how many messages in [chatId] are yours, so the UI can
     * show what a cleanup would remove before doing it. Null when unavailable.
     */
    suspend fun countMyMessages(chatId: Long): Int? {
        val me = meId()
        if (me == 0L) return null
        val found = (
            client.searchChatMessages(
                chatId = chatId,
                query = "",
                senderId = MessageSenderUser(me),
                fromMessageId = 0,
                offset = 0,
                limit = 1,
            ) as? TdlResult.Success
            )?.result ?: return null
        return found.totalCount
    }

    /**
     * Deletes your own messages in [chatId]. When [olderThanDate] is set, only
     * messages sent before that unix time are removed (which forces the paged path,
     * since the server-side bulk delete cannot filter by date).
     */
    suspend fun deleteMyMessages(chatId: Long, olderThanDate: Int? = null, onProgress: (Int) -> Unit): Int {
        val me = meId()
        if (me == 0L) return 0
        val chat = (client.getChat(chatId) as? TdlResult.Success)?.result
        if (chat?.type is ChatTypeSupergroup && olderThanDate == null) {
            client.deleteChatMessagesBySender(chatId, MessageSenderUser(me))
            return -1
        }
        var deleted = 0
        var from = 0L
        while (true) {
            // Page through the whole history; delete only messages that are actually
            // outgoing (yours), so this can never touch someone else's messages even
            // if a sender filter isn't honored for this chat type.
            val found = (
                client.searchChatMessages(
                    chatId = chatId,
                    query = "",
                    fromMessageId = from,
                    offset = 0,
                    limit = 100,
                ) as? TdlResult.Success
                )?.result ?: break
            if (found.messages.isEmpty()) break
            val ids = found.messages
                .filter { it.isOutgoing && (olderThanDate == null || it.date < olderThanDate) }
                .map { it.id }
                .toLongArray()
            if (ids.isNotEmpty()) {
                client.deleteMessages(chatId, ids, revoke = true)
                deleted += ids.size
                onProgress(deleted)
            }
            from = found.nextFromMessageId
            if (from == 0L) break
        }
        return deleted
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
