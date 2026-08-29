package com.sonnik.telemetry.ui

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.dto.Message
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(chatId: Long, onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val repo = app.messages
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<MediaAttachment>>(emptyList()) }
    var nextFrom by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableStateOf(0 to 0) }

    suspend fun loadPage() {
        if (loading || reachedEnd) return
        loading = true
        val result = repo.searchMedia(chatId, nextFrom, 60)
        result.onSuccess { (messages, next) ->
            val atts = messages.mapNotNull { mediaAttachment(it.content) }
            items = items + atts
            nextFrom = next
            if (next == 0L || messages.isEmpty()) reachedEnd = true
        }.onFailure { status = "Ошибка: ${it.message}" }
        loading = false
    }

    LaunchedEffect(chatId) { loadPage() }

    val saveTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        val folder = tree.createDirectory("media_$chatId") ?: tree
        saving = true
        status = null
        scope.launch {
            var done = 0
            var ok = 0
            val total = items.size
            for (att in items) {
                saveProgress = done to total
                val saved = withContext(Dispatchers.IO) {
                    val path = repo.localPath(att.fullFileId) ?: return@withContext false
                    val ext = att.fileName.substringAfterLast('.', "")
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US))
                        ?: att.mimeType
                    val target = folder.createFile(mime, att.fileName) ?: return@withContext false
                    runCatching {
                        context.contentResolver.openOutputStream(target.uri)?.use { out ->
                            File(path).inputStream().use { it.copyTo(out) }
                        }
                    }.isSuccess
                }
                if (saved) ok++
                done++
            }
            saveProgress = done to total
            saving = false
            status = "Сохранено: $ok из $total"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Медиа чата") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Галерея медиа чата",
                        listOf(
                            "Сетка всех фото и видео чата, подгружается по мере прокрутки.",
                            "Тап по элементу открывает его во внешнем просмотрщике/плеере.",
                            "Кнопка ⬇ сверху — сохранить все загруженные медиа в выбранную папку.",
                        ),
                    )
                    IconButton(
                        onClick = { if (!saving && items.isNotEmpty()) saveTree.launch(null) },
                        enabled = !saving && items.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Сохранить всё")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (saving) {
                Text(
                    "Сохраняю: ${saveProgress.first} из ${saveProgress.second}…",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp))
            }
            if (items.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Медиа не найдено", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    items(items, key = { it.fullFileId }) { att ->
                        GalleryCell(att) {
                            scope.launch { openMediaExternally(context, att) }
                        }
                    }
                    if (!reachedEnd) {
                        item {
                            LaunchedEffect(items.size) { loadPage() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCell(att: MediaAttachment, onClick: () -> Unit) {
    val repo = TelemetryApp.instance.messages
    var image by remember(att.fullFileId) {
        mutableStateOf(att.minithumb?.let { decode(it) })
    }
    LaunchedEffect(att.previewFileId) {
        if (att.previewFileId != 0) {
            val path = repo.localPath(att.previewFileId)
            if (path != null) decodeFile(path)?.let { image = it }
        }
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(Color(0x22808080))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = image
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (att.kind == MediaKind.VIDEO || att.kind == MediaKind.GIF || att.kind == MediaKind.VIDEO_NOTE) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}
