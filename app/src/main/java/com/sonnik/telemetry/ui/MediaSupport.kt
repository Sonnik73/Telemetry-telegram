package com.sonnik.telemetry.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaKind { PHOTO, STICKER, VIDEO, GIF, VIDEO_NOTE, VOICE, AUDIO, DOCUMENT }

/** Everything the dialog needs to preview a media message and save its file. */
data class MediaAttachment(
    val kind: MediaKind,
    val fullFileId: Int,
    val previewFileId: Int,
    val minithumb: ByteArray?,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val showsImage: Boolean,
)

private fun sizeOrExpected(size: Long, expected: Long) = if (size > 0) size else expected

/** Builds a [MediaAttachment] from a message's content, or null if it has no media. */
fun mediaAttachment(content: MessageContent): MediaAttachment? = when (content) {
    is MessagePhoto -> {
        // Prefer a mid/large size that isn't huge, else the largest available.
        val best = content.photo.sizes.filter { it.width in 1..1600 }.maxByOrNull { it.width }
            ?: content.photo.sizes.maxByOrNull { it.width }
        best?.let {
            MediaAttachment(
                MediaKind.PHOTO, it.photo.id, it.photo.id, content.photo.minithumbnail?.data,
                "photo_${it.photo.id}.jpg", "image/jpeg", sizeOrExpected(it.photo.size, it.photo.expectedSize), true,
            )
        }
    }
    is MessageSticker -> MediaAttachment(
        MediaKind.STICKER, content.sticker.sticker.id, content.sticker.sticker.id, null,
        "sticker_${content.sticker.id}.webp", "image/webp",
        sizeOrExpected(content.sticker.sticker.size, content.sticker.sticker.expectedSize), true,
    )
    is MessageVideo -> MediaAttachment(
        MediaKind.VIDEO, content.video.video.id, content.video.thumbnail?.file?.id ?: 0,
        content.video.minithumbnail?.data, content.video.fileName.ifBlank { "video_${content.video.video.id}.mp4" },
        content.video.mimeType.ifBlank { "video/mp4" }, sizeOrExpected(content.video.video.size, content.video.video.expectedSize), true,
    )
    is MessageAnimation -> MediaAttachment(
        MediaKind.GIF, content.animation.animation.id, content.animation.thumbnail?.file?.id ?: 0,
        content.animation.minithumbnail?.data, content.animation.fileName.ifBlank { "gif_${content.animation.animation.id}.mp4" },
        content.animation.mimeType.ifBlank { "video/mp4" }, sizeOrExpected(content.animation.animation.size, content.animation.animation.expectedSize), true,
    )
    is MessageVideoNote -> MediaAttachment(
        MediaKind.VIDEO_NOTE, content.videoNote.video.id, content.videoNote.thumbnail?.file?.id ?: 0,
        content.videoNote.minithumbnail?.data, "round_${content.videoNote.video.id}.mp4", "video/mp4",
        sizeOrExpected(content.videoNote.video.size, content.videoNote.video.expectedSize), true,
    )
    is MessageVoiceNote -> MediaAttachment(
        MediaKind.VOICE, content.voiceNote.voice.id, 0, null,
        "voice_${content.voiceNote.voice.id}.ogg", content.voiceNote.mimeType.ifBlank { "audio/ogg" },
        sizeOrExpected(content.voiceNote.voice.size, content.voiceNote.voice.expectedSize), false,
    )
    is MessageAudio -> MediaAttachment(
        MediaKind.AUDIO, content.audio.audio.id, 0, content.audio.albumCoverMinithumbnail?.data,
        content.audio.fileName.ifBlank { "audio_${content.audio.audio.id}.mp3" },
        content.audio.mimeType.ifBlank { "audio/mpeg" }, sizeOrExpected(content.audio.audio.size, content.audio.audio.expectedSize), false,
    )
    is MessageDocument -> MediaAttachment(
        MediaKind.DOCUMENT, content.document.document.id, content.document.thumbnail?.file?.id ?: 0,
        content.document.minithumbnail?.data, content.document.fileName.ifBlank { "file_${content.document.document.id}" },
        content.document.mimeType.ifBlank { "application/octet-stream" },
        sizeOrExpected(content.document.document.size, content.document.document.expectedSize),
        content.document.thumbnail != null,
    )
    else -> null
}

@Composable
fun MediaView(att: MediaAttachment) {
    val app = TelemetryApp.instance
    val repo = app.messages
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var preview by remember(att.fullFileId) { mutableStateOf<ImageBitmap?>(att.minithumb?.let { decode(it) }) }
    var saving by remember(att.fullFileId) { mutableStateOf(false) }
    var saved by remember(att.fullFileId) { mutableStateOf(false) }
    var opening by remember(att.fullFileId) { mutableStateOf(false) }
    var openError by remember(att.fullFileId) { mutableStateOf(false) }

    // Downloads the full file, copies it into the shared cache and hands it to an
    // external viewer/player (system gallery, video/audio player, document viewer).
    fun open() {
        if (opening) return
        opening = true
        openError = false
        scope.launch {
            val file = withContext(Dispatchers.IO) { prepareShareFile(context, att) }
            val ok = file != null && runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, att.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }.isSuccess
            opening = false
            openError = !ok
        }
    }

    // Load a sharper preview (photo full size or thumbnail) over the blurred minithumb.
    LaunchedEffect(att.previewFileId) {
        if (att.showsImage && att.previewFileId != 0) {
            val path = repo.localPath(att.previewFileId)
            if (path != null) decodeFile(path)?.let { preview = it }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(att.mimeType),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val path = repo.localPath(att.fullFileId) ?: return@withContext false
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        File(path).inputStream().use { it.copyTo(out) }
                    }
                }.isSuccess
            }
            saving = false
            saved = ok
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (att.showsImage && preview != null) {
            Box(Modifier.clickable { open() }) {
                Image(
                    bitmap = preview!!,
                    contentDescription = null,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
                if (att.kind == MediaKind.VIDEO || att.kind == MediaKind.GIF || att.kind == MediaKind.VIDEO_NOTE) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                    )
                }
                if (opening) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                }
            }
        } else if (!att.showsImage) {
            Row(
                modifier = Modifier.clickable { open() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (opening) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (att.kind == MediaKind.VOICE || att.kind == MediaKind.AUDIO) Icons.Default.PlayArrow
                        else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                    )
                }
                Column {
                    Text(att.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(humanSize(att.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { open() }, enabled = !opening) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Открыть")
            }
            IconButton(onClick = { if (!saving) saveLauncher.launch(att.fileName) }) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = "Сохранить файл")
                }
            }
            Text(
                when {
                    openError -> "не удалось открыть"
                    saved -> "сохранено"
                    opening -> "открываю…"
                    else -> "${humanSize(att.sizeBytes)} · открыть / сохранить"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (openError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Copies the downloaded media into the shared cache dir; returns the file or null. */
private suspend fun prepareShareFile(context: Context, att: MediaAttachment): File? {
    val repo = TelemetryApp.instance.messages
    val path = repo.localPath(att.fullFileId) ?: return null
    return runCatching {
        val dir = File(context.cacheDir, "shared_media").apply { mkdirs() }
        val safe = att.fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "media_${att.fullFileId}" }
        val out = File(dir, safe)
        File(path).inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }.getOrNull()
}

private fun decode(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

private fun decodeFile(path: String): ImageBitmap? =
    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    if (bytes < 1024) return "$bytes Б"
    val units = arrayOf("КБ", "МБ", "ГБ")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}
