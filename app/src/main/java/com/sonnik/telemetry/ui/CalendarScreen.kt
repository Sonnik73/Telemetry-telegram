package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<EventItem>?>(null) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            val now = System.currentTimeMillis()
            val cutoff = now / 1000 - 24 * 3600
            val rows = withContext(Dispatchers.IO) { app.intel.store.recentCached(3000) }
            val nameCache = HashMap<Long, String>()
            val titleCache = HashMap<Long, String>()
            val out = ArrayList<EventItem>()
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
            events = out.sortedBy { it.whenEpoch }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

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
                            "Находит в недавно полученных сообщениях упоминания дат и времени и собирает их в список ближайших событий.",
                            "Понимает «сегодня/завтра/послезавтра», «5 января», даты вида 25.12 и 25.12.2026, а также время ЧЧ:ММ.",
                            "Это эвристика — возможны пропуски и ложные срабатывания.",
                            "Нажмите на событие, чтобы открыть чат.",
                        ),
                    )
                    IconButton(onClick = { load() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val data = events
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize()) {}
                data.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Ближайших событий не найдено. Даты берутся из недавно полученных сообщений.",
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
                                Text(e.snippet, style = MaterialTheme.typography.bodyMedium)
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
