package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatSummary
import com.sonnik.telemetry.stats.DeepStats
import com.sonnik.telemetry.stats.QuickCounts
import com.sonnik.telemetry.stats.ScanProgress
import com.sonnik.telemetry.stats.StatsEngine
import com.sonnik.telemetry.stats.StatsEngine.Companion.parseSenderKey
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

internal fun parseDateOrNull(text: String): LocalDate? =
    try {
        LocalDate.parse(text.trim(), DATE_FORMAT)
    } catch (_: DateTimeParseException) {
        null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStatsScreen(
    chatId: Long,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onOpenDialog: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val app = TelemetryApp.instance
    val engine = remember { StatsEngine(app.telegram.client) }
    val scope = rememberCoroutineScope()

    var chat by remember { mutableStateOf<ChatSummary?>(null) }
    var quick by remember { mutableStateOf<QuickCounts?>(null) }
    var deep by remember { mutableStateOf<DeepStats?>(null) }
    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) {
        chat = app.chats.getChat(chatId)
        quick = engine.quickCounts(chatId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chat?.title ?: "Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Медиа чата")
                    }
                    IconButton(onClick = onOpenDialog) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Открыть диалог")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Download, contentDescription = "Экспорт")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge: consume the IME inset so the date fields stay above the keyboard.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QuickCountsCard(quick)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Полное сканирование", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Проходит всю историю и считает объём медиафайлов, топ участников и активность. " +
                            "Для больших чатов может занять несколько минут.",
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
                    dateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (scanJob == null) {
                        Button(
                            onClick = {
                                dateError = null
                                val zone = ZoneId.systemDefault()
                                val from = fromText.trim().takeIf { it.isNotEmpty() }?.let { text ->
                                    parseDateOrNull(text) ?: run {
                                        dateError = "Неверная дата: $text"
                                        return@Button
                                    }
                                }
                                val to = toText.trim().takeIf { it.isNotEmpty() }?.let { text ->
                                    parseDateOrNull(text) ?: run {
                                        dateError = "Неверная дата: $text"
                                        return@Button
                                    }
                                }
                                deep = null
                                scanJob = scope.launch {
                                    try {
                                        val raw = engine.deepScan(
                                            chatId = chatId,
                                            fromDateSec = from?.atStartOfDay(zone)?.toEpochSecond()?.toInt() ?: 0,
                                            toDateSec = to?.plusDays(1)?.atStartOfDay(zone)?.toEpochSecond()?.toInt()?.minus(1) ?: 0,
                                            estimatedTotal = quick?.total,
                                            onProgress = { progress = it },
                                        )
                                        deep = engine.resolveSenderNames(raw) { key ->
                                            parseSenderKey(key)?.let { app.chats.senderName(it) } ?: key
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        dateError = "Сканирование прервано ошибкой: ${e.message ?: e::class.simpleName}"
                                    } finally {
                                        scanJob = null
                                        progress = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (deep == null) "Сканировать" else "Сканировать заново")
                        }
                    } else {
                        val p = progress
                        if (p?.estimatedTotal != null && p.estimatedTotal > 0) {
                            LinearProgressIndicator(
                                progress = { (p.processed.toFloat() / p.estimatedTotal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            "Обработано ${formatCount(p?.processed ?: 0)} сообщений" +
                                (p?.reachedDate?.takeIf { it > 0 }?.let { " · дошли до ${formatDate(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { scanJob?.cancel() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Остановить")
                        }
                    }
                }
            }

            deep?.let { DeepStatsCards(it) }
        }
    }
}

@Composable
private fun QuickCountsCard(quick: QuickCounts?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Сообщения по типам", style = MaterialTheme.typography.titleMedium)
            if (quick == null) {
                CircularProgressIndicator()
                return@Column
            }
            StatRow("Всего сообщений", quick.total)
            StatRow("Фото", quick.photos)
            StatRow("Видео", quick.videos)
            StatRow("Файлы", quick.documents)
            StatRow("Музыка", quick.audio)
            StatRow("Голосовые", quick.voiceNotes)
            StatRow("Видеосообщения", quick.videoNotes)
            StatRow("GIF", quick.animations)
            StatRow("Ссылки", quick.links)
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value?.let(::formatCount) ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeepStatsCards(stats: DeepStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Объём медиафайлов", style = MaterialTheme.typography.titleMedium)
            if (stats.partial) {
                Text(
                    "Скан не завершён${stats.error?.let { ": $it" } ?: ""} — данные частичные.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            stats.media.entries.sortedByDescending { it.value.bytes }.forEach { (name, bucket) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$name (${formatCount(bucket.count)})")
                    Text(formatBytes(bucket.bytes))
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Итого (${formatCount(stats.totalMediaCount)})")
                Text(formatBytes(stats.totalMediaBytes), style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    if (stats.perDay.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Активность по дням", style = MaterialTheme.typography.titleMedium)
                ActivityChart(stats.perDay)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Активность", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Просканировано сообщений")
                Text(formatCount(stats.scannedMessages))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Текстовых сообщений")
                Text(formatCount(stats.textMessages))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Символов текста")
                Text(formatCount(stats.textCharacters.toInt()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Первое сообщение")
                Text(formatDate(stats.firstMessageDate))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Последнее сообщение")
                Text(formatDate(stats.lastMessageDate))
            }
            val busiest = stats.perDay.maxByOrNull { it.value }
            if (busiest != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Самый активный день")
                    Text("${busiest.key.format(DATE_FORMAT)} · ${formatCount(busiest.value)}")
                }
            }
            if (stats.perDay.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("В среднем в день (по активным)")
                    Text(formatCount(stats.scannedMessages / stats.perDay.size))
                }
            }
        }
    }

    if (stats.topSenders.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Топ участников", style = MaterialTheme.typography.titleMedium)
                stats.topSenders.forEachIndexed { index, sender ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${sender.name}")
                        Text(formatCount(sender.messages))
                    }
                }
            }
        }
    }
}
