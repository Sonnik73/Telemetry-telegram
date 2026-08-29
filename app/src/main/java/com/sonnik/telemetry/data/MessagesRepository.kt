package com.sonnik.telemetry.data

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhotoAndVideo
import dev.g000sha256.tdl.dto.TextEntity

/**
 * Reads and sends messages for a single dialog.
 *
 * Reading history through [loadHistory] does NOT mark anything as read — TDLib
 * only reports read receipts when [markRead] (viewMessages) is called — so a
 * dialog opened without calling [markRead] is read invisibly to the other side.
 */
class MessagesRepository(private val client: TdlClient) {

    /**
     * Loads up to [limit] messages older than [fromMessageId] (0 = newest). No read receipts.
     *
     * TDLib's first getChatHistory on a not-yet-opened chat returns only what's in
     * the local cache — often just the last message, or nothing at all. So this
     * pages from the newest anchor downwards, following the oldest returned id,
     * until it has [limit] messages or the history runs out. Empty pages (a cold
     * cache priming from the server) are retried a few times before giving up.
     */
    suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int): Result<List<Message>> {
        val collected = LinkedHashMap<Long, Message>()
        var anchor = fromMessageId
        var emptyStreak = 0
        var pages = 0
        while (collected.size < limit && pages < 12) {
            pages++
            when (val result = client.getChatHistory(chatId, anchor, 0, limit, onlyLocal = false)) {
                is TdlResult.Success -> {
                    val page = result.result.messages.filterNotNull()
                    if (page.isEmpty()) {
                        // Cold cache: give TDLib a couple of tries to fetch from the server.
                        if (++emptyStreak >= 3) break else continue
                    }
                    emptyStreak = 0
                    for (m in page) collected[m.id] = m
                    val oldest = page.minByOrNull { it.id }?.id ?: break
                    if (oldest == anchor) break // no further progress
                    anchor = oldest
                }
                is TdlResult.Failure -> {
                    if (collected.isNotEmpty()) break
                    return Result.failure(Exception("${result.code}: ${result.message}"))
                }
            }
        }
        return Result.success(collected.values.toList())
    }

    /**
     * Searches a chat's media (photos and videos by default) newest-first.
     * Returns the page and the anchor for the next page (0 = no more).
     */
    suspend fun searchMedia(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
        filter: SearchMessagesFilter = SearchMessagesFilterPhotoAndVideo(),
    ): Result<Pair<List<Message>, Long>> {
        return when (
            val result = client.searchChatMessages(
                chatId = chatId,
                query = "",
                fromMessageId = fromMessageId,
                offset = 0,
                limit = limit,
                filter = filter,
            )
        ) {
            is TdlResult.Success ->
                Result.success(result.result.messages.filterNotNull() to result.result.nextFromMessageId)
            is TdlResult.Failure -> Result.failure(Exception("${result.code}: ${result.message}"))
        }
    }

    /**
     * Global full-text search across all chats (server-side), newest-first.
     * Returns the page and the opaque offset for the next page ("" = no more).
     */
    suspend fun searchGlobal(query: String, offset: String, limit: Int): Result<Pair<List<Message>, String>> {
        return when (
            val result = client.searchMessages(
                query = query,
                offset = offset,
                limit = limit,
                minDate = 0,
                maxDate = 0,
            )
        ) {
            is TdlResult.Success ->
                Result.success(result.result.messages.filterNotNull() to result.result.nextOffset)
            is TdlResult.Failure -> Result.failure(Exception("${result.code}: ${result.message}"))
        }
    }

    suspend fun sendText(chatId: Long, text: String): Result<Message> {
        val content = InputMessageText(
            text = FormattedText(text = text, entities = emptyArray<TextEntity>()),
            linkPreviewOptions = null,
            clearDraft = true,
        )
        return when (val result = client.sendMessage(chatId = chatId, inputMessageContent = content)) {
            is TdlResult.Success -> Result.success(result.result)
            is TdlResult.Failure -> Result.failure(Exception("${result.code}: ${result.message}"))
        }
    }

    /** Downloads a file synchronously and returns its local path, or null on failure. */
    suspend fun localPath(fileId: Int): String? {
        val downloaded = when (val result = client.downloadFile(fileId, priority = 1, offset = 0, limit = 0, synchronous = true)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return null
        }
        val path = downloaded.local.path
        return if (downloaded.local.isDownloadingCompleted && path.isNotEmpty()) path else null
    }

    /** Marks messages read (visible to the sender). Only call in the non-stealth path. */
    suspend fun markRead(chatId: Long, messageIds: LongArray) {
        if (messageIds.isEmpty()) return
        client.viewMessages(chatId = chatId, messageIds = messageIds, forceRead = true)
    }

    suspend fun openChat(chatId: Long) {
        client.openChat(chatId)
    }

    suspend fun closeChat(chatId: Long) {
        client.closeChat(chatId)
    }
}
