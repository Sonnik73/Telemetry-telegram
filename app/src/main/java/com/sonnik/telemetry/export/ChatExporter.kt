package com.sonnik.telemetry.export

import com.sonnik.telemetry.data.ChatRepository
import com.sonnik.telemetry.stats.StatsEngine
import com.sonnik.telemetry.stats.StatsEngine.Companion.key
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
import java.util.Date
import java.util.Locale
import org.json.JSONObject

enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    HTML("html", "text/html"),
}

sealed interface ExportPhase {
    data class Scanning(val processed: Int, val estimatedTotal: Int?) : ExportPhase
    data class Writing(val written: Int, val total: Int) : ExportPhase
}

/**
 * Streams chat history into an export file. History arrives newest-to-oldest,
 * so messages are first spooled to a temp JSONL file and then written to the
 * destination in chronological order using recorded line offsets.
 */
class ChatExporter(
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
        onProgress: (ExportPhase) -> Unit,
    ): ExportResult {
        val tempFile = File.createTempFile("export", ".jsonl", cacheDir)
        val senderNames = HashMap<String, String>()
        val offsets = ArrayList<Long>()
        var bytesWritten = 0L
        try {
            tempFile.outputStream().buffered().use { spool ->
                val stats = engine.deepScan(
                    chatId = chatId,
                    fromDateSec = fromDateSec,
                    toDateSec = toDateSec,
                    estimatedTotal = estimatedTotal,
                    onProgress = { onProgress(ExportPhase.Scanning(it.processed, it.estimatedTotal)) },
                    onMessage = { message ->
                        val senderKey = message.senderId.key()
                        val senderName = senderNames.getOrPut(senderKey) {
                            repository.senderName(message.senderId)
                        }
                        val line = message.toJson(senderName, senderKey).toString()
                            .toByteArray(StandardCharsets.UTF_8)
                        offsets += bytesWritten
                        spool.write(line)
                        spool.write('\n'.code)
                        bytesWritten += line.size + 1
                    },
                )
                if (stats.error != null) {
                    return ExportResult(0, error = stats.error)
                }
            }

            val total = offsets.size
            OutputStreamWriter(output, StandardCharsets.UTF_8).buffered().use { writer ->
                RandomAccessFile(tempFile, "r").use { spool ->
                    when (format) {
                        ExportFormat.JSON -> writeJson(writer, spool, offsets, chatId, chatTitle, fromDateSec, toDateSec, onProgress)
                        ExportFormat.HTML -> writeHtml(writer, spool, offsets, chatTitle, fromDateSec, toDateSec, onProgress)
                    }
                }
            }
            return ExportResult(total, error = null)
        } finally {
            tempFile.delete()
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
            writer.write("    ")
            writer.write(readLineAt(spool, offsets, i))
            if (i > 0) writer.write(",")
            writer.write("\n")
            val written = offsets.size - i
            if (written % 500 == 0) onProgress(ExportPhase.Writing(written, offsets.size))
        }
        writer.write("  ]\n}\n")
        onProgress(ExportPhase.Writing(offsets.size, offsets.size))
    }

    private fun writeHtml(
        writer: BufferedWriter,
        spool: RandomAccessFile,
        offsets: List<Long>,
        chatTitle: String,
        fromDateSec: Int,
        toDateSec: Int,
        onProgress: (ExportPhase) -> Unit,
    ) {
        val period = buildString {
            if (fromDateSec > 0) append("с ${isoDate(fromDateSec.toLong()).take(10)} ")
            if (toDateSec > 0) append("по ${isoDate(toDateSec.toLong()).take(10)}")
        }.trim()
        writer.write(
            """
            <!DOCTYPE html>
            <html lang="ru"><head><meta charset="utf-8">
            <title>${escapeHtml(chatTitle)}</title>
            <style>
            body{font-family:system-ui,sans-serif;max-width:720px;margin:0 auto;padding:16px;background:#f4f6f8;color:#111}
            .msg{background:#fff;border-radius:10px;padding:8px 12px;margin:6px 0;box-shadow:0 1px 2px rgba(0,0,0,.08)}
            .from{font-weight:600;color:#1c93e3}
            .date{color:#888;font-size:.8em;margin-left:8px}
            .media{color:#555;font-style:italic}
            h1{font-size:1.3em}
            </style></head><body>
            <h1>${escapeHtml(chatTitle)}</h1>
            <p>Сообщений: ${offsets.size}${if (period.isNotEmpty()) " · $period" else ""}</p>
            """.trimIndent(),
        )
        writer.write("\n")
        for (i in offsets.indices.reversed()) {
            val message = JSONObject(readLineAt(spool, offsets, i))
            writer.write("<div class=\"msg\"><span class=\"from\">")
            writer.write(escapeHtml(message.optString("from")))
            writer.write("</span><span class=\"date\">")
            writer.write(escapeHtml(message.optString("date")))
            writer.write("</span>")
            val type = message.optString("type")
            if (type != "text") {
                writer.write("<div class=\"media\">[")
                writer.write(escapeHtml(type))
                val fileName = message.optString("file_name")
                if (fileName.isNotEmpty()) writer.write(": ${escapeHtml(fileName)}")
                val size = message.optLong("size_bytes", -1)
                if (size >= 0) writer.write(", ${size / 1024} KB")
                writer.write("]</div>")
            }
            val text = message.optString("text")
            if (text.isNotEmpty()) {
                writer.write("<div>")
                writer.write(escapeHtml(text).replace("\n", "<br>"))
                writer.write("</div>")
            }
            writer.write("</div>\n")
            val written = offsets.size - i
            if (written % 500 == 0) onProgress(ExportPhase.Writing(written, offsets.size))
        }
        writer.write("</body></html>\n")
        onProgress(ExportPhase.Writing(offsets.size, offsets.size))
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
}

data class ExportResult(val messages: Int, val error: String?)
