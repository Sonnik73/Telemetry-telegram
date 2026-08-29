package com.sonnik.telemetry.stats

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterAnimation
import dev.g000sha256.tdl.dto.SearchMessagesFilterAudio
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterEmpty
import dev.g000sha256.tdl.dto.SearchMessagesFilterPhoto
import dev.g000sha256.tdl.dto.SearchMessagesFilterUrl
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideoNote
import dev.g000sha256.tdl.dto.SearchMessagesFilterVoiceNote
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Quick per-type message counts computed server-side; null means unavailable for this chat. */
data class QuickCounts(
    val total: Int?,
    val photos: Int?,
    val videos: Int?,
    val documents: Int?,
    val audio: Int?,
    val voiceNotes: Int?,
    val videoNotes: Int?,
    val animations: Int?,
    val links: Int?,
)

/** Byte and message totals for one media category, accumulated during a deep scan. */
data class MediaBucket(val count: Int = 0, val bytes: Long = 0) {
    operator fun plus(size: Long): MediaBucket = MediaBucket(count + 1, bytes + size)
}

data class SenderStat(val name: String, val messages: Int)

data class WordStat(val text: String, val count: Int)

data class DeepStats(
    val scannedMessages: Int,
    val textMessages: Int,
    val textCharacters: Long,
    val media: Map<String, MediaBucket>,
    val totalMediaBytes: Long,
    val totalMediaCount: Int,
    val topSenders: List<SenderStat>,
    val topWords: List<WordStat>,
    val topEmoji: List<WordStat>,
    val perDay: Map<LocalDate, Int>,
    val firstMessageDate: Int,
    val lastMessageDate: Int,
    val partial: Boolean,
    val error: String?,
)

data class ScanProgress(val processed: Int, val estimatedTotal: Int?, val reachedDate: Int)

class StatsEngine(private val client: TdlClient) {

    /** Server-side total message count for a chat; null when unavailable. */
    suspend fun totalMessages(chatId: Long): Int? =
        when (val result = client.getChatMessageCount(chatId = chatId, filter = SearchMessagesFilterEmpty(), returnLocal = false)) {
            is TdlResult.Success -> result.result.count
            is TdlResult.Failure -> null
        }

    suspend fun quickCounts(chatId: Long): QuickCounts {
        suspend fun count(filter: SearchMessagesFilter): Int? =
            when (val result = client.getChatMessageCount(chatId = chatId, filter = filter, returnLocal = false)) {
                is TdlResult.Success -> result.result.count
                is TdlResult.Failure -> null
            }
        return QuickCounts(
            total = count(SearchMessagesFilterEmpty()),
            photos = count(SearchMessagesFilterPhoto()),
            videos = count(SearchMessagesFilterVideo()),
            documents = count(SearchMessagesFilterDocument()),
            audio = count(SearchMessagesFilterAudio()),
            voiceNotes = count(SearchMessagesFilterVoiceNote()),
            videoNotes = count(SearchMessagesFilterVideoNote()),
            animations = count(SearchMessagesFilterAnimation()),
            links = count(SearchMessagesFilterUrl()),
        )
    }

    /**
     * Walks the chat history newest-to-oldest, accumulating media sizes, sender
     * and per-day activity. [fromDateSec]/[toDateSec] bound the scan (0 = unbounded).
     * Reports progress through [onProgress] and honors coroutine cancellation.
     */
    suspend fun deepScan(
        chatId: Long,
        fromDateSec: Int = 0,
        toDateSec: Int = 0,
        estimatedTotal: Int? = null,
        onProgress: (ScanProgress) -> Unit = {},
        onMessage: suspend (Message) -> Unit = {},
    ): DeepStats = withContext(Dispatchers.Default) {
        val zone = ZoneId.systemDefault()
        val media = LinkedHashMap<String, MediaBucket>()
        val senderMessages = HashMap<String, Int>()
        val senderIds = HashMap<String, MessageSender>()
        val perDay = LinkedHashMap<LocalDate, Int>()
        val wordCounts = HashMap<String, Int>()
        val emojiCounts = HashMap<String, Int>()
        var scanned = 0
        var textMessages = 0
        var textCharacters = 0L
        var firstDate = 0
        var lastDate = 0
        var fromMessageId = 0L
        var partial = false
        var error: String? = null

        loop@ while (true) {
            coroutineContext.ensureActive()
            val rawBatch = when (val result = client.getChatHistory(chatId, fromMessageId, 0, 100, onlyLocal = false)) {
                is TdlResult.Success -> result.result.messages
                is TdlResult.Failure -> {
                    partial = true
                    error = "${result.code}: ${result.message}"
                    break@loop
                }
            }
            // An empty raw array means the end of history. A non-empty array whose
            // entries are all null means a page of unparsable messages — advance
            // past it using the last known id so the scan doesn't stall.
            if (rawBatch.isEmpty()) break
            val batch = rawBatch.filterNotNull()
            if (batch.isEmpty()) {
                partial = true
                error = "пропущена страница нечитаемых сообщений"
                break@loop
            }

            for (message in batch) {
                fromMessageId = message.id
                if (message.date < fromDateSec) break@loop
                if (toDateSec > 0 && message.date > toDateSec) continue

                scanned++
                if (lastDate == 0) lastDate = message.date
                firstDate = message.date

                // A single malformed message must not abort a multi-hour scan.
                try {
                    val senderKey = message.senderId.key()
                    senderMessages.merge(senderKey, 1, Int::plus)
                    senderIds.putIfAbsent(senderKey, message.senderId)

                    val day = Instant.ofEpochSecond(message.date.toLong()).atZone(zone).toLocalDate()
                    perDay.merge(day, 1, Int::plus)

                    when (val content = message.content) {
                        is MessageText -> {
                            textMessages++
                            textCharacters += content.text.text.length
                        }
                        is MessagePhoto -> media.add("Фото", content.photo.sizes.maxOfOrNull { it.photo.sizeOrExpected() } ?: 0)
                        is MessageVideo -> media.add("Видео", content.video.video.sizeOrExpected())
                        is MessageDocument -> media.add("Файлы", content.document.document.sizeOrExpected())
                        is MessageAudio -> media.add("Музыка", content.audio.audio.sizeOrExpected())
                        is MessageVoiceNote -> media.add("Голосовые", content.voiceNote.voice.sizeOrExpected())
                        is MessageVideoNote -> media.add("Видеосообщения", content.videoNote.video.sizeOrExpected())
                        is MessageAnimation -> media.add("GIF", content.animation.animation.sizeOrExpected())
                        is MessageSticker -> media.add("Стикеры", content.sticker.sticker.sizeOrExpected())
                        else -> Unit
                    }

                    tallyText(textOf(content), wordCounts, emojiCounts)

                    onMessage(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error = "сообщение ${message.id}: ${e.message ?: e::class.simpleName}"
                }
            }
            onProgress(ScanProgress(scanned, estimatedTotal, firstDate))
        }

        val topSenders = senderMessages.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { SenderStat(name = it.key, messages = it.value) }

        val topWords = wordCounts.entries
            .sortedByDescending { it.value }
            .take(30)
            .map { WordStat(it.key, it.value) }
        val topEmoji = emojiCounts.entries
            .sortedByDescending { it.value }
            .take(30)
            .map { WordStat(it.key, it.value) }

        return@withContext DeepStats(
            scannedMessages = scanned,
            textMessages = textMessages,
            textCharacters = textCharacters,
            media = media,
            totalMediaBytes = media.values.sumOf { it.bytes },
            totalMediaCount = media.values.sumOf { it.count },
            topSenders = topSenders,
            topWords = topWords,
            topEmoji = topEmoji,
            perDay = perDay,
            firstMessageDate = firstDate,
            lastMessageDate = lastDate,
            partial = partial,
            error = error,
        )
    }

    /** Resolves opaque sender keys produced by [deepScan] into display names. */
    suspend fun resolveSenderNames(stats: DeepStats, resolve: suspend (String) -> String): DeepStats {
        val resolved = stats.topSenders.map { it.copy(name = resolve(it.name)) }
        return stats.copy(topSenders = resolved)
    }

    companion object {
        fun MessageSender.key(): String = when (this) {
            is MessageSenderUser -> "u:$userId"
            is MessageSenderChat -> "c:$chatId"
            else -> "?"
        }

        fun parseSenderKey(key: String): MessageSender? {
            val id = key.drop(2).toLongOrNull() ?: return null
            return when {
                key.startsWith("u:") -> MessageSenderUser(id)
                key.startsWith("c:") -> MessageSenderChat(id)
                else -> null
            }
        }
    }
}

private fun File.sizeOrExpected(): Long = if (size > 0) size else expectedSize

private fun MutableMap<String, MediaBucket>.add(category: String, bytes: Long) {
    this[category] = (this[category] ?: MediaBucket()) + bytes
}

/** Extracts the text or caption of a message for word/emoji tallying. */
private fun textOf(content: dev.g000sha256.tdl.dto.MessageContent): String = when (content) {
    is MessageText -> content.text.text
    is MessagePhoto -> content.caption.text
    is MessageVideo -> content.caption.text
    is MessageDocument -> content.caption.text
    is MessageAudio -> content.caption.text
    is MessageVoiceNote -> content.caption.text
    is MessageAnimation -> content.caption.text
    else -> ""
}

/** Counts non-trivial words (>=3 letters, not a stopword) and emoji in [text]. */
private fun tallyText(text: String, words: MutableMap<String, Int>, emoji: MutableMap<String, Int>) {
    if (text.isEmpty()) return
    // Emoji: scan by code point.
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        if (isEmoji(cp)) {
            val s = String(Character.toChars(cp))
            emoji.merge(s, 1, Int::plus)
        }
        i += Character.charCount(cp)
    }
    // Words: split on anything that isn't a letter or digit.
    for (raw in text.split(WORD_SPLIT)) {
        val w = raw.lowercase()
        if (w.length >= 3 && w !in STOPWORDS && w.any { it.isLetter() }) {
            words.merge(w, 1, Int::plus)
        }
    }
}

private fun isEmoji(cp: Int): Boolean =
    (cp in 0x1F300..0x1FAFF) || // symbols, emoticons, transport, supplemental
        (cp in 0x2600..0x27BF) || // misc symbols + dingbats
        (cp in 0x1F000..0x1F0FF) || // mahjong/dominoes/cards
        cp == 0x2764 || // heart
        (cp in 0x1F1E6..0x1F1FF) // regional indicators (flags)

private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")

private val STOPWORDS = setOf(
    // Russian
    "что", "как", "это", "так", "вот", "был", "она", "они", "оно", "его", "нет",
    "да", "же", "бы", "ли", "или", "уже", "все", "всё", "там", "тут", "как-то",
    "если", "тоже", "меня", "тебя", "него", "неё", "них", "мне", "тебе", "нам",
    "вам", "для", "под", "над", "при", "про", "без", "the", "and", "you", "that",
    "for", "are", "was", "but", "not", "this", "with", "have", "your", "can",
    "все", "чтобы", "когда", "потом", "очень", "может", "надо", "быть", "есть",
)
