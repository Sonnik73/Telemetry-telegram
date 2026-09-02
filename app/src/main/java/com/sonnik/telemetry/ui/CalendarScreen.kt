package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class EventItem(
    val chatId: Long,
    val chatTitle: String,
    val senderName: String,
    val whenEpoch: Long,
    val hasTime: Boolean,
    val snippet: String,
    val messageId: Long = 0L,
    val senderId: Long = 0L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var deepScanning by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<EventItem>?>(null) }
    var scanDone by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf(app.intel.calendarReminders()) }

    fun loadFromCache() {
        if (loading) return
        loading = true
        scope.launch {
            val now = System.currentTimeMillis()
            val cutoff = now / 1000 - 24 * 3600
            val nameCache = HashMap<Long, String>()
            val titleCache = HashMap<Long, String>()

            val out = ArrayList<EventItem>()

            val rows = withContext(Dispatchers.IO) { app.intel.store.recentCached(3000) }
            for (r in rows) {
                val parsed = parseWhen(r.body, now) ?: continue
                if (parsed.epoch < cutoff) continue
                val sender = nameCache.getOrPut(r.senderId) {
                    runCatching {
                        app.chats.senderName(
                            if (r.senderId > 0) MessageSenderUser(r.senderId) else MessageSenderChat(r.senderId),
                        )
                    }.getOrNull() ?: "Контакт"
                }
                val title = titleCache.getOrPut(r.chatId) {
                    runCatching { app.chats.getChat(r.chatId)?.title }.getOrNull() ?: "чат"
                }
                out += EventItem(r.chatId, title, sender, parsed.epoch, parsed.hasTime, r.body.take(160))
            }

            val stored = withContext(Dispatchers.IO) { app.intel.store.calendarEvents(futureOnly = true) }
            for (e in stored) {
                if (out.any { it.chatId == e.chatId && it.whenEpoch == e.eventEpoch }) continue
                val sender = nameCache.getOrPut(e.senderId) {
                    runCatching {
                        app.chats.senderName(
                            if (e.senderId > 0) MessageSenderUser(e.senderId) else MessageSenderChat(e.senderId),
                        )
                    }.getOrNull() ?: "Контакт"
                }
                val title = titleCache.getOrPut(e.chatId) {
                    runCatching { app.chats.getChat(e.chatId)?.title }.getOrNull() ?: "чат"
                }
                out += EventItem(e.chatId, title, sender, e.eventEpoch, e.hasTime, e.snippet)
            }

            events = out.distinctBy { "${it.chatId}:${it.whenEpoch}" }.sortedBy { it.whenEpoch }
            loading = false
        }
    }

    fun deepScan() {
        if (deepScanning) return
        deepScanning = true
        scanDone = 0
        scanTotal = 0
        scope.launch {
            val chats = app.chats.loadAllChats().getOrNull() ?: run { deepScanning = false; return@launch }
            val recent = chats.sortedByDescending { it.lastMessageDate }.take(30)
            scanTotal = recent.size
            val now = System.currentTimeMillis()
            val sevenDaysAgo = (now / 1000 - 7 * 86400).toInt()

            for (chat in recent) {
                val messages = app.messages.loadHistory(chat.id, 0, 100).getOrNull().orEmpty()
                for (m in messages) {
                    if (m.date < sevenDaysAgo) continue
                    val text = (m.content as? MessageText)?.text?.text
                        ?: continue
                    val parsed = parseWhen(text, now) ?: continue
                    if (parsed.epoch < now / 1000 - 86400) continue
                    val sid = when (val s = m.senderId) {
                        is MessageSenderUser -> s.userId
                        is MessageSenderChat -> s.chatId
                        else -> 0L
                    }
                    withContext(Dispatchers.IO) {
                        app.intel.store.recordCalendarEvent(
                            chat.id, sid, m.id, parsed.epoch, parsed.hasTime, text.take(200),
                        )
                    }
                }
                scanDone++
            }
            deepScanning = false
            loadFromCache()
        }
    }

    LaunchedEffect(Unit) { loadFromCache() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Календарь из чатов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Календарь из чатов",
                        listOf(
                            "Находит в сообщениях упоминания дат и времени и собирает их в список ближайших событий.",
                            "«Глубокий поиск» — сканирует реальную историю 30 самых активных чатов за последние 7 дней.",
                            "«Напоминания» — push-уведомление за час до события.",
                            "Понимает «сегодня/завтра/послезавтра», «5 января», даты вида 25.12 и 25.12.2026, а также время ЧЧ:ММ.",
                        ),
                    )
                    IconButton(onClick = { deepScan() }, enabled = !deepScanning) {
                        Icon(Icons.Default.ManageSearch, contentDescription = "Глубокий поиск")
                    }
                    IconButton(onClick = { loadFromCache() }, enabled = !loading && !deepScanning) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = reminders,
                    onClick = {
                        reminders = !reminders
                        app.intel.setCalendarReminders(reminders)
                    },
                    label = { Text("напоминания") },
                )
            }

            if (deepScanning && scanTotal > 0) {
                LinearProgressIndicator(
                    progress = { scanDone.toFloat() / scanTotal },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Text(
                    "Сканирование чатов: $scanDone / $scanTotal",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            val data = events
            when {
                loading && data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize()) {}
                data.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Ближайших событий не найдено. Нажмите 🔍 «глубокий поиск», чтобы просканировать историю чатов.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.size) { index ->
                        val e = data[index]
                        Card(Modifier.fillMaxWidth().clickable { onOpenChat(e.chatId) }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(formatEventWhen(e.whenEpoch, e.hasTime), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text(e.snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                Text(
                                    "${e.senderName} · ${e.chatTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEventWhen(epochSeconds: Long, hasTime: Boolean): String {
    val pattern = if (hasTime) "EEE, d MMMM yyyy, HH:mm" else "EEE, d MMMM yyyy"
    return SimpleDateFormat(pattern, Locale("ru")).format(Date(epochSeconds * 1000))
}
