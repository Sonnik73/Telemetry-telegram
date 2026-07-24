package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatKind
import com.sonnik.telemetry.data.ChatSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onOpenChat: (Long) -> Unit, onOpenAccount: () -> Unit, onOpenOverview: () -> Unit) {
    val repository = TelemetryApp.instance.chats

    var chats by remember { mutableStateOf<List<ChatSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        chats = null
        error = null
        repository.loadAllChats()
            .onSuccess { chats = it }
            .onFailure { error = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = onOpenOverview) {
                        Icon(Icons.Default.BarChart, contentDescription = "Сводка по аккаунту")
                    }
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Аккаунт")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по названию") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ошибка: $error", color = MaterialTheme.colorScheme.error)
                }
                chats == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Загрузка чатов…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
                else -> {
                    val visible = chats!!.filter { it.title.contains(query.trim(), ignoreCase = true) }
                    Text(
                        "Всего: ${visible.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { chat ->
                            ChatRow(chat = chat, onClick = { onOpenChat(chat.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatSummary, onClick: () -> Unit) {
    val kindLabel = when (chat.kind) {
        ChatKind.PRIVATE -> "Личный чат"
        ChatKind.SECRET -> "Секретный чат"
        ChatKind.GROUP -> "Группа"
        ChatKind.CHANNEL -> "Канал"
    }
    val details = buildList {
        add(kindLabel)
        chat.memberCount?.let { add("${formatCount(it)} участн.") }
        if (chat.lastMessageDate > 0) add(formatDate(chat.lastMessageDate))
    }
    ListItem(
        headlineContent = { Text(chat.title) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = {
            if (chat.unreadCount > 0) Text(formatCount(chat.unreadCount))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
