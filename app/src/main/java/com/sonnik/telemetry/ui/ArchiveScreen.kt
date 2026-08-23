package com.sonnik.telemetry.ui

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

    LaunchedEffect(changed) {
        val list = app.intel.store.events(limit = 300)
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
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив: удалённое и правки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(events) { e -> EventCard(e, names.value[e.senderId] ?: "…") }
        }
    }
}

@Composable
private fun EventCard(e: ArchiveEvent, sender: String) {
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
