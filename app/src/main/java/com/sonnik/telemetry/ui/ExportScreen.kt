package com.sonnik.telemetry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatSummary
import com.sonnik.telemetry.export.ChatExporter
import com.sonnik.telemetry.export.ExportFormat
import com.sonnik.telemetry.export.ExportPhase
import com.sonnik.telemetry.stats.StatsEngine
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { StatsEngine(app.telegram.client) }
    val exporter = remember { ChatExporter(engine, app.chats, context.cacheDir) }

    var chat by remember { mutableStateOf<ChatSummary?>(null) }
    var format by remember { mutableStateOf(ExportFormat.JSON) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<ExportPhase?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        chat = app.chats.getChat(chatId)
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(format.mimeType),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val zone = ZoneId.systemDefault()
        val from = fromText.trim().takeIf { it.isNotEmpty() }?.let(::parseDateOrNull)
        val to = toText.trim().takeIf { it.isNotEmpty() }?.let(::parseDateOrNull)
        status = null
        isError = false
        scope.launch {
            try {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    status = "Не удалось открыть файл для записи"
                    isError = true
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    exporter.export(
                        chatId = chatId,
                        chatTitle = chat?.title ?: "Chat $chatId",
                        format = format,
                        fromDateSec = from?.atStartOfDay(zone)?.toEpochSecond()?.toInt() ?: 0,
                        toDateSec = to?.plusDays(1)?.atStartOfDay(zone)?.toEpochSecond()?.toInt()?.minus(1) ?: 0,
                        estimatedTotal = null,
                        output = stream,
                        onProgress = { phase = it },
                    )
                }
                if (result.error != null) {
                    status = "Ошибка экспорта: ${result.error}"
                    isError = true
                } else {
                    status = "Готово: выгружено ${formatCount(result.messages)} сообщений"
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
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        val safeTitle = (chat?.title ?: "chat")
                            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                            .trim()
                            .replace(' ', '_')
                            .ifBlank { "chat" }
                        createDocument.launch("${safeTitle}_$stamp.${format.extension}")
                    },
                    enabled = chat != null &&
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
                        Text("Читаем историю: ${formatCount(currentPhase.processed)} сообщений…")
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
                        Text("Записываем файл: ${formatCount(currentPhase.written)} из ${formatCount(currentPhase.total)}…")
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
