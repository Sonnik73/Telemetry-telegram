package com.sonnik.telemetry.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CapturedRow(
    val id: Long,
    val type: String,
    val path: String,
    val caption: String,
    val at: Long,
    val senderName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedMediaScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<CapturedRow>?>(null) }
    var enabled by remember { mutableStateOf(app.intel.captureEnabled()) }

    fun load() {
        scope.launch {
            val items = app.intel.store.capturedMedia(500)
            val nameCache = HashMap<Long, String>()
            rows = items.map { m ->
                val name = nameCache.getOrPut(m.senderId) {
                    runCatching {
                        app.chats.senderName(
                            if (m.senderId > 0) MessageSenderUser(m.senderId) else MessageSenderChat(m.senderId),
                        )
                    }.getOrNull() ?: "Контакт"
                }
                CapturedRow(m.id, m.type, m.path, m.caption, m.at, name)
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Перехваченные медиа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Перехват одноразовых медиа",
                        listOf(
                            "Когда включено, приложение автоматически сохраняет входящие «одноразовые» (самоуничтожающиеся) фото, видео и голосовые — до того, как они исчезнут.",
                            "Копии хранятся в приватной памяти приложения; отсюда их можно открыть или сохранить в галерею.",
                            "Работает, только пока запущен фоновый трекер.",
                            "Секретные чаты не поддерживаются (они отключены в приложении).",
                        ),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            app.intel.setCaptureEnabled(it)
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val data = rows
            when {
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
                }
                data.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (enabled) {
                            "Пока ничего не перехвачено. Одноразовые фото/видео сохранятся сюда автоматически, когда придут."
                        } else {
                            "Перехват выключен. Включите переключатель вверху, чтобы сохранять одноразовые медиа."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(data.size) { index ->
                        CapturedCard(data[index])
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturedCard(row: CapturedRow) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(row.path) { mutableStateOf<ImageBitmap?>(null) }
    var saved by remember(row.path) { mutableStateOf(false) }

    val encrypted = row.path.endsWith(".enc")

    LaunchedEffect(row.path) {
        if (row.type == "photo") {
            bitmap = withContext(Dispatchers.IO) {
                if (encrypted) {
                    val tmp = File(context.cacheDir, "cap_prev_${row.id}")
                    runCatching {
                        com.sonnik.telemetry.security.FileCrypto.decryptToFile(context, File(row.path), tmp)
                        decodeFile(tmp.absolutePath)
                    }.getOrNull().also { tmp.delete() }
                } else {
                    decodeFile(row.path)
                }
            }
        }
    }

    fun open() {
        scope.launch {
            val f = withContext(Dispatchers.IO) { sharePlainFile(context, row) } ?: return@launch
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeFor(row.type))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }

    val mime = mimeFor(row.type)
    val defaultName = File(row.path).name.removeSuffix(".enc")
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        if (encrypted) {
                            com.sonnik.telemetry.security.FileCrypto.decryptToStream(context, File(row.path), out)
                        } else {
                            File(row.path).inputStream().use { it.copyTo(out) }
                        }
                    }
                }.isSuccess
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.senderName, fontWeight = FontWeight.SemiBold)
            Text(
                "${typeLabel(row.type)} · ${formatDateTime(row.at.toInt())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(8.dp)).clickable { open() },
                    contentScale = ContentScale.Fit,
                )
            }
            if (row.caption.isNotBlank()) Text(row.caption, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open() }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Открыть")
                }
                IconButton(onClick = { saveLauncher.launch(defaultName) }) {
                    Icon(Icons.Default.Download, contentDescription = "Сохранить")
                }
                if (saved) Text("сохранено", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun mimeFor(type: String): String = when (type) {
    "photo" -> "image/jpeg"
    "voice" -> "audio/ogg"
    else -> "video/mp4"
}

private fun typeLabel(type: String): String = when (type) {
    "video" -> "🎬 Видео (одноразовое)"
    "voice" -> "🎤 Голосовое (одноразовое)"
    "videonote" -> "⭕ Кружок (одноразовый)"
    "gif" -> "GIF (одноразовый)"
    else -> "🖼 Фото (одноразовое)"
}

/** Prepares a decrypted (or copied) plaintext file in the shared cache for viewing. */
private fun sharePlainFile(context: android.content.Context, row: CapturedRow): File? {
    val dir = File(context.cacheDir, "shared_media").apply { mkdirs() }
    val name = File(row.path).name.removeSuffix(".enc")
    val out = File(dir, name)
    return runCatching {
        if (row.path.endsWith(".enc")) {
            com.sonnik.telemetry.security.FileCrypto.decryptToFile(context, File(row.path), out)
        } else {
            File(row.path).inputStream().use { i -> out.outputStream().use { i.copyTo(it) } }
            out
        }
    }.getOrNull()
}
