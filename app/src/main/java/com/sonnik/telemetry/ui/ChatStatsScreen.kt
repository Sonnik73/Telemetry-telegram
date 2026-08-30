package com.sonnik.telemetry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
                    InfoButton(
                        "Статистика чата",
                        listOf(
                            "Быстрые счётчики по типам сообщений — мгновенно, силами сервера.",
                            "Полное сканирование: объём медиа по категориям, топ участников, активность по дням, топ слов и эмодзи.",
                            "Экспорт всей статистики в CSV.",
                            "Автоскачивание новых медиа этого чата в выбранную папку.",
                            "Значки сверху: 🖼 галерея медиа, 💬 диалог со скрытым чтением, ⬇ экспорт истории.",
                        ),
                    )
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

            AutoDownloadCard(chatId)

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

    if (stats.topWords.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Топ слов", style = MaterialTheme.typography.titleMedium)
                stats.topWords.take(20).forEachIndexed { index, w ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${w.text}")
                        Text(formatCount(w.count))
                    }
                }
            }
        }
    }

    if (stats.topEmoji.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Топ эмодзи", style = MaterialTheme.typography.titleMedium)
                stats.topEmoji.take(20).forEach { e ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.text, style = MaterialTheme.typography.titleMedium)
                        Text(formatCount(e.count))
                    }
                }
            }
        }
    }

    AnalyticsCard(stats)

    CsvExportButton(stats)
}

@Composable
private fun AnalyticsCard(stats: DeepStats) {
    val a = stats.analytics
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Аналитика переписки", style = MaterialTheme.typography.titleMedium)
            AnalyticsRow("Ваши сообщения", formatCount(a.outgoingCount))
            AnalyticsRow("Входящие", formatCount(a.incomingCount))
            AnalyticsRow("Ваше среднее время ответа", formatDurationOrDash(a.myReplyAvgSec))
            AnalyticsRow("Ответ собеседника (среднее)", formatDurationOrDash(a.theirReplyAvgSec))
            AnalyticsRow("Кто чаще начинает", "вы ${a.myInitiations} · собеседник ${a.theirInitiations}")
            HorizontalDivider()
            Text("Активность по дням недели", style = MaterialTheme.typography.titleSmall)
            val labels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
            val maxDay = (a.weekdayCounts.maxOrNull() ?: 0).coerceAtLeast(1)
            labels.forEachIndexed { i, label ->
                val count = a.weekdayCounts.getOrElse(i) { 0 }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { count.toFloat() / maxDay },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(formatCount(count), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDurationOrDash(sec: Long): String {
    if (sec <= 0) return "—"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> "${h} ч ${m} мин"
        m > 0 -> "${m} мин"
        else -> "${s} сек"
    }
}

@Composable
private fun AutoDownloadCard(chatId: Long) {
    val app = TelemetryApp.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = app.mediaAuto.store
    var enabled by remember { mutableStateOf(store.isEnabled(chatId)) }
    var hasFolder by remember { mutableStateOf(store.folderUri() != null) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist access across restarts so background download keeps working.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        store.setFolderUri(uri.toString())
        hasFolder = true
        store.setEnabled(chatId, true)
        enabled = true
        app.mediaAuto.start()
        app.presence.start()
        com.sonnik.telemetry.presence.PresenceService.start(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Автоскачивание медиа", style = MaterialTheme.typography.titleMedium)
            Text(
                "Новые фото, видео и файлы из этого чата будут автоматически сохраняться в " +
                    "выбранную папку (в фоне, пока работает трекер).",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (enabled) "Включено" else "Выключено")
                androidx.compose.material3.Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on && !hasFolder) {
                            pickFolder.launch(null)
                        } else {
                            store.setEnabled(chatId, on)
                            enabled = on
                            if (on) {
                                app.mediaAuto.start()
                                app.presence.start()
                                com.sonnik.telemetry.presence.PresenceService.start(context)
                            }
                        }
                    },
                )
            }
            OutlinedButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (hasFolder) "Сменить папку" else "Выбрать папку")
            }
        }
    }
}

@Composable
private fun CsvExportButton(stats: DeepStats) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(buildStatsCsv(stats).toByteArray(Charsets.UTF_8)) }
        }.fold({ "Статистика сохранена в CSV" }, { "Ошибка: ${it.message}" })
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { launcher.launch("stats.csv") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text("  Экспорт статистики в CSV")
        }
        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

/** Builds a UTF-8 CSV summarizing the deep-scan statistics. */
private fun buildStatsCsv(stats: DeepStats): String {
    fun esc(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder()
    sb.append("section,label,value\n")
    sb.append("итого,просканировано сообщений,${stats.scannedMessages}\n")
    sb.append("итого,текстовых сообщений,${stats.textMessages}\n")
    sb.append("итого,символов текста,${stats.textCharacters}\n")
    sb.append("итого,объём медиа (байт),${stats.totalMediaBytes}\n")
    stats.media.forEach { (name, b) -> sb.append("медиа,${esc(name)},${b.count};${b.bytes}\n") }
    stats.topSenders.forEach { sb.append("участник,${esc(it.name)},${it.messages}\n") }
    stats.topWords.forEach { sb.append("слово,${esc(it.text)},${it.count}\n") }
    stats.topEmoji.forEach { sb.append("эмодзи,${esc(it.text)},${it.count}\n") }
    stats.perDay.toSortedMap().forEach { (day, n) -> sb.append("день,$day,$n\n") }
    return sb.toString()
}
