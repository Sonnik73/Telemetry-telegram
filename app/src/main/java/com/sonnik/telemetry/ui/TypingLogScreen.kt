package com.sonnik.telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.intel.TypingEvent
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TypingRow(
    val chatId: Long,
    val senderId: Long,
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
    var filterQuery by remember { mutableStateOf("") }
    var alertUsers by remember { mutableStateOf(app.intel.typingAlertUsers()) }

    var liveEvents by remember { mutableStateOf<List<TypingRow>>(emptyList()) }
    val liveNameCache = remember { HashMap<Long, String>() }
    val liveChatCache = remember { HashMap<Long, String>() }

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
                TypingRow(e.chatId, e.senderId, name, title, e.action, e.at)
            }
            liveNameCache.putAll(nameCache)
            liveChatCache.putAll(titleCache)
            rows = list
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    LaunchedEffect(Unit) {
        app.intel.liveTyping.collect { event ->
            val name = liveNameCache.getOrPut(event.senderId) {
                runCatching { app.chats.senderName(MessageSenderUser(event.senderId)) }.getOrNull() ?: "Контакт"
            }
            val title = liveChatCache.getOrPut(event.chatId) {
                runCatching { app.chats.getChat(event.chatId)?.title ?: "" }.getOrNull() ?: "чат"
            }
            val row = TypingRow(event.chatId, event.senderId, name, title, event.action, event.at)
            liveEvents = (listOf(row) + liveEvents.filter {
                !(it.senderId == event.senderId && it.chatId == event.chatId)
            }).take(20)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            val cutoff = System.currentTimeMillis() / 1000 - 15
            liveEvents = liveEvents.filter { it.at > cutoff }
        }
    }

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
                            "Вверху — кто печатает прямо сейчас (индикатор гаснет через 15 с).",
                            "Можно включить пуш-уведомление для выбранных контактов (колокольчик).",
                            "Поиск фильтрует журнал по имени контакта.",
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
            if (liveEvents.isNotEmpty()) {
                Text(
                    "Сейчас",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                )
                liveEvents.forEach { live ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChat(live.chatId) }.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(Color(0xFF2E7D32)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(live.senderName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("${live.action} · ${live.chatTitle}", style = MaterialTheme.typography.bodySmall)
                            }
                            val isAlert = live.senderId in alertUsers
                            IconButton(onClick = {
                                if (isAlert) app.intel.removeTypingAlertUser(live.senderId)
                                else app.intel.addTypingAlertUser(live.senderId)
                                alertUsers = app.intel.typingAlertUsers()
                            }) {
                                Icon(
                                    if (isAlert) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = if (isAlert) "Отключить пуш" else "Включить пуш",
                                    tint = if (isAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                label = { Text("Фильтр по имени") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )

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
                else -> {
                    val q = filterQuery.trim().lowercase()
                    val filtered = if (q.isEmpty()) data else data.filter {
                        it.senderName.lowercase().contains(q) || it.chatTitle.lowercase().contains(q)
                    }
                    Text(
                        "Записей: ${filtered.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered.size) { index ->
                            val r = filtered[index]
                            Card(Modifier.fillMaxWidth().clickable { onOpenChat(r.chatId) }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
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
                                    val isAlert = r.senderId in alertUsers
                                    IconButton(onClick = {
                                        if (isAlert) app.intel.removeTypingAlertUser(r.senderId)
                                        else app.intel.addTypingAlertUser(r.senderId)
                                        alertUsers = app.intel.typingAlertUsers()
                                    }) {
                                        Icon(
                                            if (isAlert) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                            contentDescription = if (isAlert) "Отключить пуш" else "Включить пуш",
                                            tint = if (isAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
