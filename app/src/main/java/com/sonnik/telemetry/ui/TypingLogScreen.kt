package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
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
import kotlinx.coroutines.launch

private data class TypingRow(
    val chatId: Long,
    val senderName: String,
    val chatTitle: String,
    val action: String,
    val at: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingLogScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<TypingRow>?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            val events = app.intel.store.typingEvents(300)
            val nameCache = HashMap<Long, String>()
            val titleCache = HashMap<Long, String>()
            val list = events.map { e ->
                val name = nameCache.getOrPut(e.senderId) {
                    runCatching { app.chats.senderName(MessageSenderUser(e.senderId)) }.getOrNull() ?: "Контакт"
                }
                val title = titleCache.getOrPut(e.chatId) {
                    runCatching {
                        app.chats.getChat(e.chatId)?.title
                            ?: app.chats.senderName(MessageSenderChat(e.chatId))
                    }.getOrNull() ?: "чат"
                }
                TypingRow(e.chatId, name, title, e.action, e.at)
            }
            rows = list
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Кто печатает") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Детектор «печатает…»",
                        listOf(
                            "Фоновый сборщик ловит, кто и когда начал печатать, записывать голосовое или отправлять медиа — во всех чатах, где вы состоите, даже не открывая их.",
                            "Работает, только пока запущен фоновый трекер (значок в шторке).",
                            "Повторные события одного человека в одном чате объединяются (раз в несколько секунд).",
                            "Нажмите на запись, чтобы открыть чат.",
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
            val data = rows
            when {
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Загрузка…", style = MaterialTheme.typography.bodyMedium)
                }
                data.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Пока ничего не поймано. События появляются, когда контакты печатают или шлют медиа, а трекер запущен.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.size) { index ->
                        val r = data[index]
                        Card(Modifier.fillMaxWidth().clickable { onOpenChat(r.chatId) }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(r.senderName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${r.action} · ${r.chatTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    formatDateTime(r.at.toInt()),
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
