package com.sonnik.telemetry.export

import com.sonnik.telemetry.data.ChatRepository
import com.sonnik.telemetry.stats.StatsEngine
import com.sonnik.telemetry.stats.StatsEngine.Companion.key
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File as TdFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    HTML("html", "text/html"),
}

/** Categories of message content the export can be filtered to. */
enum class ExportContentType(val label: String) {
    TEXT("Текст"),
    PHOTO("Фото"),
    VIDEO("Видео"),
    AUDIO("Музыка"),
    VOICE("Голосовые"),
    VIDEO_NOTE("Видеосообщения"),
    GIF("GIF"),
    STICKER("Стикеры"),
    DOCUMENT("Файлы"),
    OTHER("Прочее");

    companion object {
        /** Types offered as filter checkboxes; OTHER (service/other) rides along with TEXT. */
        val selectable: List<ExportContentType> = entries.filter { it != OTHER }
    }
}

sealed interface ExportPhase {
    data class Scanning(
        val processed: Int,
        val estimatedTotal: Int?,
        val downloadedFiles: Int = 0,
        val downloadedBytes: Long = 0,
    ) : ExportPhase

    data class Writing(
        val written: Int,
        val total: Int,
        val embeddedFiles: Int = 0,
        val embeddedBytes: Long = 0,
    ) : ExportPhase
}

/** Destination for downloaded media files; returns null when a file cannot be created. */
fun interface MediaSink {
    fun open(relativeName: String): OutputStream?
}

/**
 * Streams chat history into an export file. History arrives newest-to-oldest,
 * so messages are first spooled to a temp JSONL file and then written to the
 * destination in chronological order using recorded line offsets.
 *
 * Media handling has two modes:
 *  - [MediaSink] (JSON export): each file is downloaded, copied into a `files/`
 *    folder next to the export and evicted from the TDLib cache.
 *  - inline (HTML export): the export is a single self-contained file; each
 *    media file is downloaded during the write phase, streamed into the page as
 *    a base64 `data:` URI and immediately deleted, so the HTML opens and plays
 *    anywhere without depending on sibling files. Files larger than
 *    [MAX_INLINE_BYTES] are shown as a note instead, to keep the page openable.
 */
class ChatExporter(
    private val client: TdlClient,
    private val engine: StatsEngine,
    private val repository: ChatRepository,
    private val cacheDir: File,
) {

    suspend fun export(
        chatId: Long,
        chatTitle: String,
        format: ExportFormat,
        fromDateSec: Int,
        toDateSec: Int,
        estimatedTotal: Int?,
        output: OutputStream,
        mediaSink: MediaSink? = null,
        inlineMedia: Boolean = false,
        includeComments: Boolean = false,
        contentTypes: Set<ExportContentType>? = null,
        maxMediaBytes: Long? = null,
        onProgress: (ExportPhase) -> Unit,
    ): ExportResult {
        val tempFile = File.createTempFile("export", ".jsonl", cacheDir)
        val senderNames = HashMap<String, String>()
        val offsets = ArrayList<Long>()
        var bytesWritten = 0L
        var downloadedFiles = 0
        var downloadedBytes = 0L
        try {
            tempFile.outputStream().buffered().use { spool ->
                val stats = engine.deepScan(
                    chatId = chatId,
                    fromDateSec = fromDateSec,
                    toDateSec = toDateSec,
                    estimatedTotal = estimatedTotal,
                    onProgress = {
                        onProgress(ExportPhase.Scanning(it.processed, it.estimatedTotal, downloadedFiles, downloadedBytes))
                    },
                    onMessage = { message ->
                        if (keep(message, contentTypes)) {
                            val senderKey = message.senderId.key()
                            val senderName = senderNames.getOrPut(senderKey) {
                                repository.senderName(message.senderId)
                            }
                            val json = message.toJson(senderName, senderKey)
                            val dl = attachMedia(json, message, inlineMedia, mediaSink, maxMediaBytes)
                            downloadedFiles += dl.files
                            downloadedBytes += dl.bytes
                            // Channel posts: pull the comment thread (with its media too).
                            if (includeComments && message.isChannelPost) {
                                val (comments, cdl) = fetchComments(chatId, message.id, senderNames, inlineMedia, mediaSink, contentTypes, maxMediaBytes)
                                if (comments.length() > 0) json.put("comments", comments)
                                downloadedFiles += cdl.files
                                downloadedBytes += cdl.bytes
                            }
                            val line = json.toString().toByteArray(StandardCharsets.UTF_8)
                            offsets += bytesWritten
                            spool.write(line)
                            spool.write('\n'.code)
                            bytesWritten += line.size + 1
                        }
                    },
                )
                if (stats.error != null && offsets.isEmpty()) {
                    return ExportResult(0, error = stats.error)
                }
            }

            val total = offsets.size
            var embeddedFiles = 0
            var embeddedBytes = 0L
            OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
                RandomAccessFile(tempFile, "r").use { spool ->
                    when (format) {
                        ExportFormat.JSON ->
                            writeJson(writer, spool, offsets, chatId, chatTitle, fromDateSec, toDateSec, onProgress)
                        ExportFormat.HTML -> {
                            val result = writeHtml(writer, spool, offsets, chatTitle, fromDateSec, toDateSec, inlineMedia, onProgress)
                            embeddedFiles = result.first
                            embeddedBytes = result.second
                        }
                    }
                }
            }
            return ExportResult(
                messages = total,
                error = null,
                downloadedFiles = if (inlineMedia) embeddedFiles else downloadedFiles,
                downloadedBytes = if (inlineMedia) embeddedBytes else downloadedBytes,
            )
        } finally {
            tempFile.delete()
        }
    }

    /** The single content category a message belongs to, for filtering. */
    private fun Message.contentType(): ExportContentType = when (content) {
        is MessageText -> ExportContentType.TEXT
        is MessagePhoto -> ExportContentType.PHOTO
        is MessageVideo -> ExportContentType.VIDEO
        is MessageAudio -> ExportContentType.AUDIO
        is MessageVoiceNote -> ExportContentType.VOICE
        is MessageVideoNote -> ExportContentType.VIDEO_NOTE
        is MessageAnimation -> ExportContentType.GIF
        is MessageSticker -> ExportContentType.STICKER
        is MessageDocument -> ExportContentType.DOCUMENT
        else -> ExportContentType.OTHER
    }

    /** null = no filter (everything). Service/other messages ride along with TEXT. */
    private fun keep(message: Message, types: Set<ExportContentType>?): Boolean {
        if (types == null) return true
        return when (val t = message.contentType()) {
            ExportContentType.OTHER -> ExportContentType.TEXT in types
            else -> t in types
        }
    }

    private sealed interface DownloadOutcome {
        data class Saved(val bytes: Long) : DownloadOutcome
        data class Failed(val reason: String) : DownloadOutcome
    }

    /** Running totals of media saved during folder-mode export. */
    private class DlCounters(var files: Int = 0, var bytes: Long = 0)

    /** Attaches a message's media to its JSON per the active mode, returning saved totals. */
    private suspend fun attachMedia(
        json: JSONObject,
        message: Message,
        inlineMedia: Boolean,
        mediaSink: MediaSink?,
        maxMediaBytes: Long?,
    ): DlCounters {
        val counters = DlCounters()
        val media = message.mediaFile() ?: return counters
        val downloading = inlineMedia || mediaSink != null
        // Respect the size cap only when we'd actually fetch the file; oversized media
        // stays in the export as metadata with a note, without being downloaded.
        if (downloading && maxMediaBytes != null && media.first.sizeOrExpected() > maxMediaBytes) {
            json.put("file_skipped", "больше ${maxMediaBytes / (1024 * 1024)} МБ")
            return counters
        }
        if (inlineMedia) {
            // Deferred to the write phase so only one media file is on disk at a time.
            json.put("_fid", media.first.id)
        } else if (mediaSink != null) {
            when (val saved = downloadInto(mediaSink, media.first, media.second)) {
                is DownloadOutcome.Saved -> {
                    json.put("file", "files/${media.second}")
                    counters.files++
                    counters.bytes += saved.bytes
                }
                is DownloadOutcome.Failed -> json.put("file_error", saved.reason)
            }
        }
        return counters
    }

    /**
     * Fetches the comment thread of a channel post (the discussion-group replies)
     * newest-to-oldest, returning them chronologically as JSON with media attached.
     */
    private suspend fun fetchComments(
        chatId: Long,
        postId: Long,
        senderNames: HashMap<String, String>,
        inlineMedia: Boolean,
        mediaSink: MediaSink?,
        contentTypes: Set<ExportContentType>?,
        maxMediaBytes: Long?,
    ): Pair<JSONArray, DlCounters> {
        val counters = DlCounters()
        val collected = ArrayList<Message>()
        var fromMessageId = 0L
        loop@ while (collected.size < MAX_COMMENTS) {
            val batch = when (val result = client.getMessageThreadHistory(chatId, postId, fromMessageId, 0, 100)) {
                is TdlResult.Success -> result.result.messages.filterNotNull()
                is TdlResult.Failure -> break@loop // no thread / not a discussion post
            }
            if (batch.isEmpty()) break
            for (m in batch) {
                fromMessageId = m.id
                // Skip the post itself echoed as the thread root.
                if (m.id == postId) continue
                collected += m
            }
        }
        val array = JSONArray()
        for (m in collected.sortedBy { it.date }) {
            if (!keep(m, contentTypes)) continue
            val key = m.senderId.key()
            val name = senderNames.getOrPut(key) { repository.senderName(m.senderId) }
            val cj = m.toJson(name, key)
            val dl = attachMedia(cj, m, inlineMedia, mediaSink, maxMediaBytes)
            counters.files += dl.files
            counters.bytes += dl.bytes
            array.put(cj)
        }
        return array to counters
    }

    private suspend fun downloadInto(sink: MediaSink, file: TdFile, name: String): DownloadOutcome {
        val path = downloadToPath(file.id) ?: return DownloadOutcome.Failed("download incomplete")
        val source = File(path)
        val stream = sink.open(name) ?: return DownloadOutcome.Failed("cannot create $name")
        val copied = stream.use { out -> source.inputStream().use { it.copyTo(out) } }
        client.deleteFile(file.id)
        return DownloadOutcome.Saved(copied)
    }

    /** Downloads a file synchronously and returns its local path, or null on failure. */
    private suspend fun downloadToPath(fileId: Int): String? {
        val downloaded = when (val result = client.downloadFile(fileId, priority = 1, offset = 0, limit = 0, synchronous = true)) {
            is TdlResult.Success -> result.result
            is TdlResult.Failure -> return null
        }
        val path = downloaded.local.path
        return if (downloaded.local.isDownloadingCompleted && path.isNotEmpty()) path else null
    }

    /** Returns the primary media file of a message plus a unique export file name. */
    private fun Message.mediaFile(): Pair<TdFile, String>? {
        fun named(file: TdFile, fallback: String, fileName: String = ""): Pair<TdFile, String> {
            val safe = fileName
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
            return file to if (safe.isNotEmpty()) "${id}_$safe" else "${id}_$fallback"
        }
        return when (val c = content) {
            is MessagePhoto -> c.photo.sizes.maxByOrNull { it.photo.sizeOrExpected() }
                ?.let { named(it.photo, "photo.jpg") }
            is MessageVideo -> named(c.video.video, "video.mp4", c.video.fileName)
            is MessageDocument -> named(c.document.document, "document", c.document.fileName)
            is MessageAudio -> named(c.audio.audio, "audio.mp3", c.audio.fileName)
            is MessageVoiceNote -> named(c.voiceNote.voice, "voice.ogg")
            is MessageVideoNote -> named(c.videoNote.video, "round.mp4")
            is MessageAnimation -> named(c.animation.animation, "animation.mp4", c.animation.fileName)
            is MessageSticker -> named(c.sticker.sticker, "sticker.webp")
            else -> null
        }
    }

    private fun readLineAt(spool: RandomAccessFile, offsets: List<Long>, index: Int): String {
        val start = offsets[index]
        val end = if (index + 1 < offsets.size) offsets[index + 1] - 1 else spool.length() - 1
        val buffer = ByteArray((end - start).toInt())
        spool.seek(start)
        spool.readFully(buffer)
        return String(buffer, StandardCharsets.UTF_8)
    }

    private fun writeJson(
        writer: BufferedWriter,
        spool: RandomAccessFile,
        offsets: List<Long>,
        chatId: Long,
        chatTitle: String,
        fromDateSec: Int,
        toDateSec: Int,
        onProgress: (ExportPhase) -> Unit,
    ) {
        writer.write("{\n")
        writer.write("  \"name\": ${JSONObject.quote(chatTitle)},\n")
        writer.write("  \"id\": $chatId,\n")
        writer.write("  \"exported_at\": ${JSONObject.quote(isoDate(System.currentTimeMillis() / 1000))},\n")
        if (fromDateSec > 0) writer.write("  \"period_from\": ${JSONObject.quote(isoDate(fromDateSec.toLong()))},\n")
        if (toDateSec > 0) writer.write("  \"period_to\": ${JSONObject.quote(isoDate(toDateSec.toLong()))},\n")
        writer.write("  \"messages_count\": ${offsets.size},\n")
        writer.write("  \"messages\": [\n")
        for (i in offsets.indices.reversed()) {
            val message = JSONObject(readLineAt(spool, offsets, i))
            message.remove("_fid")
            writer.write("    ")
            writer.write(message.toString())
            if (i > 0) writer.write(",")
            writer.write("\n")
            val written = offsets.size - i
            if (written % 500 == 0) onProgress(ExportPhase.Writing(written, offsets.size))
        }
        writer.write("  ]\n}\n")
        onProgress(ExportPhase.Writing(offsets.size, offsets.size))
    }

    /** Returns the number of embedded files and their total byte size. */
    private suspend fun writeHtml(
        writer: BufferedWriter,
        spool: RandomAccessFile,
        offsets: List<Long>,
        chatTitle: String,
        fromDateSec: Int,
        toDateSec: Int,
        inlineMedia: Boolean,
        onProgress: (ExportPhase) -> Unit,
    ): Pair<Int, Long> {
        val period = buildString {
            if (fromDateSec > 0) append("с ${isoDate(fromDateSec.toLong()).take(10)} ")
            if (toDateSec > 0) append("по ${isoDate(toDateSec.toLong()).take(10)}")
        }.trim()
        writer.write(
            """
            <!DOCTYPE html>
            <html lang="ru"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${escapeHtml(chatTitle)}</title>
            <style>
            body{font-family:system-ui,sans-serif;max-width:720px;margin:0 auto;padding:16px;background:#f4f6f8;color:#111}
            .msg{background:#fff;border-radius:10px;padding:8px 12px;margin:6px 0;box-shadow:0 1px 2px rgba(0,0,0,.08)}
            .from{font-weight:600;color:#1c93e3}
            .date{color:#888;font-size:.8em;margin-left:8px}
            .media{color:#555;font-style:italic}
            img,video{max-width:100%;border-radius:8px;margin-top:6px;display:block}
            audio{width:100%;margin-top:6px}
            .comments{margin:6px 0 2px 14px;border-left:3px solid #dfe6ee;padding-left:10px}
            .comment{padding:4px 0}
            .comments-title{color:#888;font-size:.8em;margin-top:6px}
            h1{font-size:1.3em}
            </style></head><body>
            <h1>${escapeHtml(chatTitle)}</h1>
            <p>Сообщений: ${offsets.size}${if (period.isNotEmpty()) " · $period" else ""}</p>
            """.trimIndent(),
        )
        writer.write("\n")
        var embeddedFiles = 0
        var embeddedBytes = 0L
        for (i in offsets.indices.reversed()) {
            val message = JSONObject(readLineAt(spool, offsets, i))
            writer.write("<div class=\"msg\"><span class=\"from\">")
            writer.write(escapeHtml(message.optString("from")))
            writer.write("</span><span class=\"date\">")
            writer.write(escapeHtml(message.optString("date")))
            writer.write("</span>")

            val (files, bytes) = renderMessageContent(writer, message, inlineMedia)
            embeddedFiles += files
            embeddedBytes += bytes

            val comments = message.optJSONArray("comments")
            if (comments != null && comments.length() > 0) {
                writer.write("<div class=\"comments-title\">Комментарии: ${comments.length()}</div>")
                writer.write("<div class=\"comments\">")
                for (ci in 0 until comments.length()) {
                    val c = comments.getJSONObject(ci)
                    writer.write("<div class=\"comment\"><span class=\"from\">")
                    writer.write(escapeHtml(c.optString("from")))
                    writer.write("</span><span class=\"date\">")
                    writer.write(escapeHtml(c.optString("date")))
                    writer.write("</span>")
                    val (cf, cb) = renderMessageContent(writer, c, inlineMedia)
                    embeddedFiles += cf
                    embeddedBytes += cb
                    writer.write("</div>")
                }
                writer.write("</div>")
            }

            writer.write("</div>\n")
            val written = offsets.size - i
            if (written % 100 == 0) {
                onProgress(ExportPhase.Writing(written, offsets.size, embeddedFiles, embeddedBytes))
            }
        }
        writer.write("</body></html>\n")
        onProgress(ExportPhase.Writing(offsets.size, offsets.size, embeddedFiles, embeddedBytes))
        return embeddedFiles to embeddedBytes
    }

    /** Renders one message's media + text into the page; returns embedded (files, bytes). */
    private suspend fun renderMessageContent(
        writer: BufferedWriter,
        message: JSONObject,
        inlineMedia: Boolean,
    ): Pair<Int, Long> {
        var files = 0
        var bytes = 0L
        val type = message.optString("type")
        val fileId = if (inlineMedia && message.has("_fid")) message.optInt("_fid") else 0
        val sizeBytes = message.optLong("size_bytes", -1)
        if (fileId != 0) {
            if (sizeBytes in 0..MAX_INLINE_BYTES) {
                val b = embedMedia(writer, type, fileId, message.optString("file_name"))
                if (b >= 0) {
                    files++
                    bytes += b
                } else {
                    writeMediaNote(writer, type, message, "не удалось скачать")
                }
            } else {
                writeMediaNote(writer, type, message, "слишком большой для встраивания")
            }
        } else if (message.has("file")) {
            // Folder-mode reference (relative path next to the export).
            val href = escapeHtml(message.optString("file"))
            writer.write("<div class=\"media\"><a href=\"$href\">${escapeHtml(message.optString("file_name", "файл"))}</a></div>")
        } else if (type != "text") {
            val skipped = if (message.has("file_skipped")) message.optString("file_skipped") else null
            writeMediaNote(writer, type, message, skipped)
        }
        val text = message.optString("text")
        if (text.isNotEmpty()) {
            writer.write("<div>")
            writer.write(escapeHtml(text).replace("\n", "<br>"))
            writer.write("</div>")
        }
        return files to bytes
    }

    /**
     * Downloads [fileId], streams it into [writer] as a base64 data URI wrapped in
     * the right media element, then deletes it from the cache. Returns the byte
     * size embedded, or -1 on failure.
     */
    private suspend fun embedMedia(writer: BufferedWriter, type: String, fileId: Int, fileName: String): Long {
        val path = downloadToPath(fileId) ?: return -1
        val source = File(path)
        val mime = mimeFor(type, fileName)
        try {
            when (type) {
                "photo", "sticker" -> {
                    writer.write("<br><img loading=\"lazy\" src=\"data:$mime;base64,")
                    val n = streamBase64(source, writer)
                    writer.write("\">")
                    return n
                }
                "video", "gif", "video_note" -> {
                    writer.write("<br><video controls playsinline src=\"data:$mime;base64,")
                    val n = streamBase64(source, writer)
                    writer.write("\"></video>")
                    return n
                }
                "voice", "audio" -> {
                    writer.write("<br><audio controls src=\"data:$mime;base64,")
                    val n = streamBase64(source, writer)
                    writer.write("\"></audio>")
                    return n
                }
                else -> {
                    val label = escapeHtml(fileName.ifBlank { "файл" })
                    writer.write("<div class=\"media\"><a download=\"$label\" href=\"data:$mime;base64,")
                    val n = streamBase64(source, writer)
                    writer.write("\">$label</a></div>")
                    return n
                }
            }
        } finally {
            client.deleteFile(fileId)
        }
    }

    /** Streams [source] as standard base64 into [writer] without loading it whole. */
    private fun streamBase64(source: File, writer: BufferedWriter): Long {
        val encoder = Base64.getEncoder()
        var total = 0L
        source.inputStream().buffered().use { input ->
            // 3 * 1024 keeps each chunk a multiple of 3 bytes, so no padding appears
            // mid-stream and the concatenated output stays valid base64.
            val buffer = ByteArray(3 * 1024)
            while (true) {
                val read = input.readNBytesCompat(buffer)
                if (read <= 0) break
                total += read
                val slice = if (read == buffer.size) buffer else buffer.copyOf(read)
                writer.write(encoder.encodeToString(slice))
            }
        }
        return total
    }

    private fun java.io.InputStream.readNBytesCompat(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun writeMediaNote(writer: BufferedWriter, type: String, message: JSONObject, reason: String?) {
        writer.write("<div class=\"media\">[")
        writer.write(escapeHtml(mediaLabel(type)))
        val fileName = message.optString("file_name")
        if (fileName.isNotEmpty()) writer.write(": ${escapeHtml(fileName)}")
        val size = message.optLong("size_bytes", -1)
        if (size >= 0) writer.write(", ${size / 1024} КБ")
        if (reason != null) writer.write(" — $reason")
        writer.write("]</div>")
    }

    private fun mediaLabel(type: String): String = when (type) {
        "photo" -> "фото"
        "video" -> "видео"
        "gif" -> "GIF"
        "video_note" -> "видеосообщение"
        "voice" -> "голосовое"
        "audio" -> "аудио"
        "sticker" -> "стикер"
        "document" -> "файл"
        else -> type
    }

    private fun mimeFor(type: String, fileName: String): String = when (type) {
        "photo" -> "image/jpeg"
        "sticker" -> "image/webp"
        "video", "gif", "video_note" -> "video/mp4"
        "voice" -> "audio/ogg"
        "audio" -> "audio/mpeg"
        else -> when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun Message.toJson(senderName: String, senderKey: String): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("date", isoDate(date.toLong()))
        json.put("date_unixtime", date)
        json.put("from", senderName)
        json.put("from_id", senderKey)
        when (val c = content) {
            is MessageText -> {
                json.put("type", "text")
                json.put("text", c.text.text)
            }
            is MessagePhoto -> {
                json.put("type", "photo")
                json.put("size_bytes", c.photo.sizes.maxOfOrNull { it.photo.sizeOrExpected() } ?: 0)
                json.put("text", c.caption.text)
            }
            is MessageVideo -> json.media("video", c.video.video, c.video.fileName, c.caption.text, c.video.duration)
            is MessageDocument -> json.media("document", c.document.document, c.document.fileName, c.caption.text, null)
            is MessageAudio -> json.media("audio", c.audio.audio, c.audio.fileName, c.caption.text, c.audio.duration)
            is MessageVoiceNote -> json.media("voice", c.voiceNote.voice, "", c.caption.text, c.voiceNote.duration)
            is MessageVideoNote -> json.media("video_note", c.videoNote.video, "", "", c.videoNote.duration)
            is MessageAnimation -> json.media("gif", c.animation.animation, c.animation.fileName, c.caption.text, c.animation.duration)
            is MessageSticker -> {
                json.put("type", "sticker")
                json.put("text", c.sticker.emoji)
                json.put("size_bytes", c.sticker.sticker.sizeOrExpected())
            }
            else -> {
                json.put("type", c::class.simpleName?.removePrefix("Message")?.lowercase(Locale.US) ?: "other")
            }
        }
        return json
    }

    private fun JSONObject.media(type: String, file: TdFile, fileName: String, caption: String, duration: Int?) {
        put("type", type)
        if (fileName.isNotEmpty()) put("file_name", fileName)
        put("size_bytes", file.sizeOrExpected())
        duration?.let { put("duration", it) }
        if (caption.isNotEmpty()) put("text", caption)
    }

    private fun TdFile.sizeOrExpected(): Long = if (size > 0) size else expectedSize

    private fun isoDate(unixSeconds: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(unixSeconds * 1000))

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private companion object {
        // Cap per-file inlining so the self-contained HTML stays openable in a browser.
        const val MAX_INLINE_BYTES = 25L * 1024 * 1024
        const val MAX_COMMENTS = 2000
    }
}

data class ExportResult(
    val messages: Int,
    val error: String?,
    val downloadedFiles: Int = 0,
    val downloadedBytes: Long = 0,
)
