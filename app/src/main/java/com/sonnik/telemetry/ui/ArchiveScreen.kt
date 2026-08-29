package com.sonnik.telemetry.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.intel.ArchiveEvent
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val changed by app.intel.changed.collectAsState()

    var events by remember { mutableStateOf<List<ArchiveEvent>>(emptyList()) }
    val names = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    val chatNames = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    var query by remember { mutableStateOf("") }
    var chatFilter by remember { mutableStateOf<Long?>(null) }
    var alerts by remember { mutableStateOf(app.intel.alertsEnabled()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        alerts = granted
        app.intel.setAlertsEnabled(granted)
    }

    fun toggleAlerts(on: Boolean) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            alerts = on
            app.intel.setAlertsEnabled(on)
        }
    }

    LaunchedEffect(changed) {
        val list = app.intel.store.events(limit = 1000)
        events = list
        // Resolve sender names once per distinct id.
        val need = list.map { it.senderId }.toSet() - names.value.keys
        if (need.isNotEmpty()) {
            val resolved = names.value.toMutableMap()
            for (id in need) {
                resolved[id] = app.chats.senderName(
                    if (id > 0) MessageSenderUser(id) else MessageSenderChat(id),
                )
            }
            names.value = resolved
        }
        // Resolve chat titles for the chat filter.
        val needChats = list.map { it.chatId }.toSet() - chatNames.value.keys
        if (needChats.isNotEmpty()) {
            val resolved = chatNames.value.toMutableMap()
            for (id in needChats) {
                resolved[id] = app.chats.getChat(id)?.title
                    ?: app.chats.senderName(if (id > 0) MessageSenderUser(id) else MessageSenderChat(id))
            }
            chatNames.value = resolved
        }
    }

    val q = query.trim()
    val filtered = events.filter { e ->
        (chatFilter == null || e.chatId == chatFilter) &&
            (q.isEmpty() ||
                e.oldBody.contains(q, ignoreCase = true) ||
                e.newBody.contains(q, ignoreCase = true) ||
                (names.value[e.senderId]?.contains(q, ignoreCase = true) == true))
    }
    // Chats present in the log, ordered by their resolved title.
    val chats = events.map { it.chatId }.distinct()
        .sortedBy { chatNames.value[it]?.lowercase() ?: "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив: удалённое и правки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Архив: удалённое и правки",
                        listOf(
                            "Фоновый сборщик кэширует входящие сообщения и ловит их удаление и редактирование.",
                            "Показывает текст сообщений, удалённых «у всех», и историю правок (было → стало).",
                            "Поиск по тексту и фильтр по чату сверху.",
                            "Колокольчик — включить пуш-уведомления о каждом удалении/правке в реальном времени.",
                            "Работает, пока запущен трекер (фоновый сервис).",
                        ),
                    )
                    IconButton(onClick = { toggleAlerts(!alerts) }) {
                        Icon(
                            if (alerts) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = if (alerts) "Уведомления включены" else "Уведомления выключены",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (events.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Пока пусто. Приложение ловит удаления и правки в реальном времени — " +
                        "оставьте его работать (фоновый режим включается вместе с трекером).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск по тексту") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChatFilterMenu(
                    label = chatFilter?.let { chatNames.value[it] ?: "…" } ?: "Все чаты",
                    chats = chats,
                    chatNames = chatNames.value,
                    selected = chatFilter,
                    onSelect = { chatFilter = it },
                )
                Text(
                    "Найдено: ${filtered.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered) { e ->
                        EventCard(e, names.value[e.senderId] ?: "…", chatNames.value[e.chatId])
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatFilterMenu(
    label: String,
    chats: List<Long>,
    chatNames: Map<Long, String>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Чат: $label")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Все чаты") },
                onClick = { onSelect(null); expanded = false },
            )
            chats.forEach { id ->
                DropdownMenuItem(
                    text = { Text(chatNames[id] ?: id.toString()) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EventCard(e: ArchiveEvent, sender: String, chatName: String?) {
    val deleted = e.kind == "deleted"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sender, fontWeight = FontWeight.SemiBold)
                Text(
                    if (deleted) "удалено" else "изменено",
                    color = if (deleted) MaterialTheme.colorScheme.error else Color(0xFFB26A00),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (chatName != null) {
                Text(chatName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (deleted) {
                Text(e.oldBody)
            } else {
                Text(e.oldBody, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("→ ${e.newBody}")
            }
            Text(formatDateTime(e.at.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
