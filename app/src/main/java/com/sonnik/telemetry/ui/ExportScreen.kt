package com.sonnik.telemetry.ui

import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatSummary
import com.sonnik.telemetry.export.ChatExporter
import com.sonnik.telemetry.export.ExportContentType
import com.sonnik.telemetry.export.ExportFormat
import com.sonnik.telemetry.export.ExportPhase
import com.sonnik.telemetry.export.MediaSink
import com.sonnik.telemetry.stats.StatsEngine
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(chatId: Long, onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { StatsEngine(app.telegram.client) }
    val exporter = remember { ChatExporter(app.telegram.client, engine, app.chats, context.cacheDir) }

    var chat by remember { mutableStateOf<ChatSummary?>(null) }
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    var downloadMedia by remember { mutableStateOf(false) }
    var maxMediaMb by remember { mutableStateOf("") }
    var includeComments by remember { mutableStateOf(false) }
    // Which content categories to export; all selected = export everything.
    var selectedTypes by remember { mutableStateOf(ExportContentType.selectable.toSet()) }
    val isChannel = chat?.kind == com.sonnik.telemetry.data.ChatKind.CHANNEL
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<ExportPhase?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        chat = app.chats.getChat(chatId)
    }

    fun exportName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val safeTitle = (chat?.title ?: "chat")
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .replace(' ', '_')
            .ifBlank { "chat" }
        return "${safeTitle}_$stamp"
    }

    fun runExport(output: OutputStream, mediaSink: MediaSink?, inlineMedia: Boolean) {
        val zone = ZoneId.systemDefault()
        val from = fromText.trim().takeIf { it.isNotEmpty() }?.let(::parseDateOrNull)
        val to = toText.trim().takeIf { it.isNotEmpty() }?.let(::parseDateOrNull)
        status = null
        isError = false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    exporter.export(
                        chatId = chatId,
                        chatTitle = chat?.title ?: "Chat $chatId",
                        format = format,
                        fromDateSec = from?.atStartOfDay(zone)?.toEpochSecond()?.toInt() ?: 0,
                        toDateSec = to?.plusDays(1)?.atStartOfDay(zone)?.toEpochSecond()?.toInt()?.minus(1) ?: 0,
                        estimatedTotal = null,
                        output = output,
                        mediaSink = mediaSink,
                        inlineMedia = inlineMedia,
                        includeComments = includeComments && isChannel,
                        contentTypes = selectedTypes,
                        maxMediaBytes = maxMediaMb.trim().toLongOrNull()
                            ?.takeIf { it > 0 }
                            ?.let { it * 1024 * 1024 },
                        onProgress = { phase = it },
                    )
                }
                if (result.error != null) {
                    status = "Ошибка экспорта: ${result.error}"
                    isError = true
                } else {
                    status = buildString {
                        append("Готово: выгружено ${formatCount(result.messages)} сообщений")
                        if (result.downloadedFiles > 0) {
                            append(", медиа: ${formatCount(result.downloadedFiles)} файлов (${formatBytes(result.downloadedBytes)})")
                        }
                    }
                    isError = false
                }
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
                isError = true
            } finally {
                phase = null
            }
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(format.mimeType),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val stream = context.contentResolver.openOutputStream(uri)
        if (stream == null) {
            status = "Не удалось открыть файл для записи"
            isError = true
        } else {
            // Single-file HTML export embeds media inline as base64.
            runExport(stream, mediaSink = null, inlineMedia = downloadMedia && format == ExportFormat.HTML)
        }
    }

    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tree = DocumentFile.fromTreeUri(context, uri)
        val folder = tree?.createDirectory(exportName())
        val chatFile = folder?.createFile(format.mimeType, "chat.${format.extension}")
        val filesDir = folder?.createDirectory("files")
        val stream = chatFile?.uri?.let { context.contentResolver.openOutputStream(it) }
        if (folder == null || filesDir == null || stream == null) {
            status = "Не удалось создать папку экспорта"
            isError = true
            return@rememberLauncherForActivityResult
        }
        val sink = MediaSink { name ->
            val extension = name.substringAfterLast('.', "")
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.US))
                ?: "application/octet-stream"
            filesDir.createFile(mime, name)?.uri?.let { context.contentResolver.openOutputStream(it) }
        }
        runExport(stream, sink, inlineMedia = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экспорт: ${chat?.title ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge means adjustResize no longer shrinks the window, so the
                // scroll area must consume the IME inset itself; this both keeps the
                // bottom fields above the keyboard and lets a focused field scroll into view.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Формат", style = MaterialTheme.typography.titleMedium)
                    ExportFormat.entries.forEach { candidate ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = format == candidate, onClick = { format = candidate }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = format == candidate, onClick = { format = candidate })
                            Text(
                                when (candidate) {
                                    ExportFormat.JSON -> "JSON — машиночитаемый, как экспорт Telegram Desktop"
                                    ExportFormat.HTML -> "HTML — удобно читать в браузере"
                                },
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = downloadMedia, onValueChange = { downloadMedia = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = downloadMedia, onCheckedChange = { downloadMedia = it })
                        Text("Скачивать медиафайлы (фото, видео, голосовые, документы)")
                    }
                    if (downloadMedia) {
                        Text(
                            when (format) {
                                ExportFormat.HTML ->
                                    "HTML будет самодостаточным: медиа встраиваются прямо в файл, " +
                                        "он открывается и проигрывается в любом браузере без папок. " +
                                        "Файлы крупнее 25 МБ (обычно большие видео) не встраиваются — " +
                                        "для полного архива видео берите JSON. Экспорт будет дольше."
                                ExportFormat.JSON ->
                                    "Понадобится выбрать папку: рядом с chat.json появится подпапка " +
                                        "files/ со всеми медиа. Экспорт будет дольше, объём — как " +
                                        "суммарный размер медиа."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = maxMediaMb,
                            onValueChange = { new -> maxMediaMb = new.filter { it.isDigit() } },
                            label = { Text("Не выгружать медиа больше, МБ") },
                            placeholder = { Text("пусто = без лимита") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Файлы крупнее лимита не скачиваются — в экспорт попадут только их " +
                                "данные (тип, имя, размер) с пометкой. Удобно, чтобы не тянуть тяжёлые видео.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (isChannel) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = includeComments, onValueChange = { includeComments = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = includeComments, onCheckedChange = { includeComments = it })
                            Text("Выгружать комментарии к постам (с медиа)")
                        }
                        if (includeComments) {
                            Text(
                                "К каждому посту подтянутся комментарии из группы обсуждений вместе с их " +
                                    "медиа. Экспорт будет заметно дольше.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Что выгружать", style = MaterialTheme.typography.titleMedium)
                    val allSelected = selectedTypes.size == ExportContentType.selectable.size
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = allSelected,
                                onValueChange = { checked ->
                                    selectedTypes = if (checked) ExportContentType.selectable.toSet() else emptySet()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                selectedTypes = if (checked) ExportContentType.selectable.toSet() else emptySet()
                            },
                        )
                        Text("Все", style = MaterialTheme.typography.titleSmall)
                    }
                    ExportContentType.selectable.forEach { type ->
                        val checked = type in selectedTypes
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    onValueChange = { on ->
                                        selectedTypes = if (on) selectedTypes + type else selectedTypes - type
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selectedTypes = if (on) selectedTypes + type else selectedTypes - type
                                },
                            )
                            Text(type.label)
                        }
                    }
                    Text(
                        "Снимите галочки с ненужных типов — например оставьте только «Фото» " +
                            "и «Видео». Служебные сообщения выгружаются вместе с текстом.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Период (пусто = вся история)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Для выгрузки одного дня укажите его в обоих полях.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromText,
                            onValueChange = { fromText = it },
                            label = { Text("С даты (дд.мм.гггг)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = toText,
                            onValueChange = { toText = it },
                            label = { Text("По дату") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            val currentPhase = phase
            if (currentPhase == null) {
                Button(
                    onClick = {
                        // JSON + media goes to a folder with a files/ subdir; everything
                        // else is a single document (HTML embeds media inline).
                        if (downloadMedia && format == ExportFormat.JSON) {
                            openTree.launch(null)
                        } else {
                            createDocument.launch("${exportName()}.${format.extension}")
                        }
                    },
                    enabled = chat != null &&
                        selectedTypes.isNotEmpty() &&
                        (fromText.isBlank() || parseDateOrNull(fromText) != null) &&
                        (toText.isBlank() || parseDateOrNull(toText) != null),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Экспортировать")
                }
            } else {
                when (currentPhase) {
                    is ExportPhase.Scanning -> {
                        val total = currentPhase.estimatedTotal
                        if (total != null && total > 0) {
                            LinearProgressIndicator(
                                progress = { (currentPhase.processed.toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Text(
                            buildString {
                                append("Читаем историю: ${formatCount(currentPhase.processed)} сообщений")
                                if (currentPhase.downloadedFiles > 0) {
                                    append(" · медиа: ${formatCount(currentPhase.downloadedFiles)} (${formatBytes(currentPhase.downloadedBytes)})")
                                }
                                append("…")
                            },
                        )
                    }
                    is ExportPhase.Writing -> {
                        LinearProgressIndicator(
                            progress = {
                                if (currentPhase.total > 0) {
                                    (currentPhase.written.toFloat() / currentPhase.total).coerceIn(0f, 1f)
                                } else 1f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            buildString {
                                append("Записываем файл: ${formatCount(currentPhase.written)} из ${formatCount(currentPhase.total)}")
                                if (currentPhase.embeddedFiles > 0) {
                                    append(" · встроено медиа: ${formatCount(currentPhase.embeddedFiles)} (${formatBytes(currentPhase.embeddedBytes)})")
                                }
                                append("…")
                            },
                        )
                    }
                }
            }

            status?.let {
                Text(
                    it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
