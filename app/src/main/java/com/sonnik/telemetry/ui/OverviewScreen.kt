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
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatKind
import com.sonnik.telemetry.data.ChatSummary
import com.sonnik.telemetry.stats.StatsEngine

private data class ChatCount(val chat: ChatSummary, val total: Int?)

/**
 * Account-wide summary: iterates every chat and asks the server for its total
 * message count, then aggregates by chat kind and ranks the busiest chats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val engine = remember { StatsEngine(app.telegram.client) }

    val counts = remember { mutableStateListOf<ChatCount>() }
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
        totalChats = chats.size
        for (chat in chats) {
            if (cancelled) break
            counts += ChatCount(chat, engine.totalMessages(chat.id))
            processed++
        }
        running = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сводка по аккаунту") },
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
            error?.let {
                Text("Ошибка: $it", color = MaterialTheme.colorScheme.error)
            }

            if (running) {
                val total = totalChats
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (processed.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Опрошено ${formatCount(processed)} из ${formatCount(total)} чатов…")
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Загрузка списка чатов…")
                }
                OutlinedButton(onClick = { cancelled = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Остановить (показать что успели)")
                }
            }

            if (counts.isNotEmpty()) {
                SummaryCards(counts.toList(), partial = cancelled, onOpenChat = onOpenChat)
            }
        }
    }
}

@Composable
private fun SummaryCards(counts: List<ChatCount>, partial: Boolean, onOpenChat: (Long) -> Unit) {
    val known = counts.filter { it.total != null }
    val totalMessages = known.sumOf { it.total!!.toLong() }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Итого", style = MaterialTheme.typography.titleMedium)
            if (partial) {
                Text(
                    "Подсчёт остановлен — цифры по опрошенной части чатов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Чатов")
                Text(formatCount(counts.size))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Сообщений (всего)")
                Text(formatCount(totalMessages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
            }
            if (known.size < counts.size) {
                Text(
                    "Для ${counts.size - known.size} чатов сервер не отдаёт счётчик — они не в сумме.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("По типам чатов", style = MaterialTheme.typography.titleMedium)
            ChatKind.entries.forEach { kind ->
                val ofKind = counts.filter { it.chat.kind == kind }
                if (ofKind.isEmpty()) return@forEach
                val messages = ofKind.sumOf { (it.total ?: 0).toLong() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        when (kind) {
                            ChatKind.PRIVATE -> "Личные"
                            ChatKind.SECRET -> "Секретные"
                            ChatKind.GROUP -> "Группы"
                            ChatKind.CHANNEL -> "Каналы"
                        } + " (${formatCount(ofKind.size)})",
                    )
                    Text(formatCount(messages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
                }
            }
        }
    }

    val top = counts.filter { (it.total ?: 0) > 0 }
        .sortedByDescending { it.total }
        .take(20)
    if (top.isNotEmpty()) {
        val maxCount = top.first().total!!
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Топ-20 чатов по сообщениям", style = MaterialTheme.typography.titleMedium)
                top.forEachIndexed { index, entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(entry.chat.id) },
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${index + 1}. ${entry.chat.title}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(formatCount(entry.total!!))
                        }
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = (entry.total!!.toFloat() / maxCount).coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                        )
                    }
                    if (index < top.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
