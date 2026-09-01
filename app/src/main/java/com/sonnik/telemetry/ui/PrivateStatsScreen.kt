package com.sonnik.telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatKind
import com.sonnik.telemetry.data.ChatSummary
import com.sonnik.telemetry.stats.StatsEngine

private data class PrivateCount(val chat: ChatSummary, val total: Int?)

/**
 * Statistics across all one-to-one (private) chats: totals plus tops by message
 * count and by unread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateStatsScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val engine = remember { StatsEngine(app.telegram.client) }

    val counts = remember { mutableStateListOf<PrivateCount>() }
    var totalChats by remember { mutableStateOf<Int?>(null) }
    var processed by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var cancelled by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val chats = app.chats.loadAllChats().getOrElse {
            error = it.message
            running = false
            return@LaunchedEffect
        }
        val privates = chats.filter { it.kind == ChatKind.PRIVATE }
        totalChats = privates.size
        for (chat in privates) {
            if (cancelled) break
            counts += PrivateCount(chat, engine.totalMessages(chat.id))
            processed++
        }
        running = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика личных чатов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Статистика личных чатов",
                        listOf(
                            "Опрашивает все личные (один-на-один) чаты и суммирует число сообщений.",
                            "Топ собеседников по числу сообщений и по непрочитанным.",
                            "Тап по строке открывает статистику чата.",
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            error?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }

            if (running) {
                val total = totalChats
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (processed.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Опрошено ${formatCount(processed)} из ${formatCount(total)} личных чатов…")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Загрузка списка чатов…")
                }
                OutlinedButton(onClick = { cancelled = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Остановить (показать что успели)")
                }
            }

            if (counts.isNotEmpty()) {
                PrivateSummary(counts.toList(), partial = cancelled, onOpenChat = onOpenChat)
            }
        }
    }
}

@Composable
private fun PrivateSummary(counts: List<PrivateCount>, partial: Boolean, onOpenChat: (Long) -> Unit) {
    val known = counts.filter { it.total != null }
    val totalMessages = known.sumOf { it.total!!.toLong() }
    val avg = if (known.isNotEmpty()) totalMessages / known.size else 0L

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Итого по личным чатам", style = MaterialTheme.typography.titleMedium)
            if (partial) {
                Text(
                    "Подсчёт остановлен — цифры по опрошенной части.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            StatRow("Личных чатов", formatCount(counts.size))
            StatRow("Сообщений (всего)", formatCount(totalMessages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            StatRow("В среднем на чат", formatCount(avg.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            val unread = counts.sumOf { it.chat.unreadCount.toLong() }
            StatRow("Непрочитанных", formatCount(unread.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        }
    }

    val topMsg = counts.filter { (it.total ?: 0) > 0 }.sortedByDescending { it.total }.take(15)
    if (topMsg.isNotEmpty()) {
        val maxCount = topMsg.first().total!!
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Топ-15 по сообщениям", style = MaterialTheme.typography.titleMedium)
                topMsg.forEachIndexed { index, entry ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onOpenChat(entry.chat.id) },
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${index + 1}. ${entry.chat.title}", modifier = Modifier.weight(1f), maxLines = 1)
                            Text(formatCount(entry.total!!))
                        }
                        Box(
                            Modifier.fillMaxWidth(fraction = (entry.total!!.toFloat() / maxCount).coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                        )
                    }
                    if (index < topMsg.lastIndex) HorizontalDivider()
                }
            }
        }
    }

    val topUnread = counts.filter { it.chat.unreadCount > 0 }.sortedByDescending { it.chat.unreadCount }.take(10)
    if (topUnread.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Топ по непрочитанным", style = MaterialTheme.typography.titleMedium)
                topUnread.forEachIndexed { index, entry ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenChat(entry.chat.id) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${index + 1}. ${entry.chat.title}", modifier = Modifier.weight(1f), maxLines = 1)
                        Text(formatCount(entry.chat.unreadCount))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
