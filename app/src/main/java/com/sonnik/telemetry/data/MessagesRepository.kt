package com.sonnik.telemetry.data

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.TextEntity

/**
 * Reads and sends messages for a single dialog.
 *
 * Reading history through [loadHistory] does NOT mark anything as read — TDLib
 * only reports read receipts when [markRead] (viewMessages) is called — so a
 * dialog opened without calling [markRead] is read invisibly to the other side.
 */
class MessagesRepository(private val client: TdlClient) {

    /** Loads up to [limit] messages older than [fromMessageId] (0 = newest). No read receipts. */
    suspend fun loadHistory(chatId: Long, fromMessageId: Long, limit: Int): Result<List<Message>> {
        // TDLib often returns fewer than requested on the first call; one retry
        // with the same anchor usually fills the page from the local cache/server.
        var attempt = 0
        while (attempt < 2) {
            when (val result = client.getChatHistory(chatId, fromMessageId, 0, limit, onlyLocal = false)) {
                is TdlResult.Success -> {
                    val messages = result.result.messages.filterNotNull()
                    if (messages.isNotEmpty() || attempt == 1) return Result.success(messages)
                }
                is TdlResult.Failure -> return Result.failure(Exception("${result.code}: ${result.message}"))
            }
            attempt++
        }
        return Result.success(emptyList())
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
