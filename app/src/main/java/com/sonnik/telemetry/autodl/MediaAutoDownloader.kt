package com.sonnik.telemetry.autodl

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.sonnik.telemetry.td.TelegramClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.File as TdFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watches incoming messages and, for chats the user enabled, downloads their
 * media into a chosen folder (SAF tree) in the background — a media auto-backup.
 * Runs for the whole process; the presence foreground service keeps it alive.
 */
class MediaAutoDownloader(
    private val context: Context,
    private val telegram: TelegramClient,
    val store: MediaAutoStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            telegram.client.newMessageUpdates.collect { update ->
                val m = update.message
                if (!store.isEnabled(m.chatId)) return@collect
                runCatching { saveMedia(m) }
            }
        }
    }

    private suspend fun saveMedia(message: Message) {
        val folderUri = store.folderUri()?.let(Uri::parse) ?: return
        val (file, name) = mediaOf(message) ?: return
        val path = downloadToPath(file.id) ?: return
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return
        val sub = root.findFile("chat_${message.chatId}") ?: root.createDirectory("chat_${message.chatId}") ?: return
        if (sub.findFile(name) != null) return // already saved
        val ext = name.substringAfterLast('.', "")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US))
            ?: "application/octet-stream"
        val target = sub.createFile(mime, name) ?: return
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            File(path).inputStream().use { it.copyTo(out) }
        }
    }

    private suspend fun downloadToPath(fileId: Int): String? {
        val downloaded = when (val r = telegram.client.downloadFile(fileId, priority = 1, offset = 0, limit = 0, synchronous = true)) {
            is TdlResult.Success -> r.result
            is TdlResult.Failure -> return null
        }
        val path = downloaded.local.path
        return if (downloaded.local.isDownloadingCompleted && path.isNotEmpty()) path else null
    }

    private fun mediaOf(message: Message): Pair<TdFile, String>? {
        fun named(file: TdFile, fallback: String, fileName: String = ""): Pair<TdFile, String> {
            val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            return file to if (safe.isNotEmpty()) "${message.id}_$safe" else "${message.id}_$fallback"
        }
        return when (val c = message.content) {
            is MessagePhoto -> c.photo.sizes.maxByOrNull { it.width }?.let { named(it.photo, "photo.jpg") }
            is MessageVideo -> named(c.video.video, "video.mp4", c.video.fileName)
            is MessageDocument -> named(c.document.document, "document", c.document.fileName)
            is MessageAudio -> named(c.audio.audio, "audio.mp3", c.audio.fileName)
            is MessageVoiceNote -> named(c.voiceNote.voice, "voice.ogg")
            is MessageVideoNote -> named(c.videoNote.video, "round.mp4")
            is MessageAnimation -> named(c.animation.animation, "animation.mp4", c.animation.fileName)
            else -> null
        }
    }
}
