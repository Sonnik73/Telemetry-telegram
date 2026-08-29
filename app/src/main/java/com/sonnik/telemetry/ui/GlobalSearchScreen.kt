package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.intel.messageBody
import dev.g000sha256.tdl.dto.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val repo = app.messages
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    var nextOffset by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val chatNames = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    val senderNames = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    suspend fun resolveNames(messages: List<Message>) {
        val chatNeed = messages.map { it.chatId }.toSet() - chatNames.value.keys
        if (chatNeed.isNotEmpty()) {
            val m = chatNames.value.toMutableMap()
            for (id in chatNeed) m[id] = app.chats.getChat(id)?.title ?: id.toString()
            chatNames.value = m
        }
        val senderNeed = messages.map { it.senderId }.toSet()
        val m = senderNames.value.toMutableMap()
        var changed = false
        for (s in senderNeed) {
            val key = s.hashCode().toLong()
            if (!m.containsKey(key)) { m[key] = app.chats.senderName(s); changed = true }
        }
        if (changed) senderNames.value = m
    }

    // Debounced search: run 400ms after the user stops typing.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList(); nextOffset = ""; searched = false; error = null
            return@LaunchedEffect
        }
        delay(400)
        loading = true
        error = null
        repo.searchGlobal(q, "", 50)
            .onSuccess { (messages, next) ->
                results = messages
                nextOffset = next
                resolveNames(messages)
            }
            .onFailure { error = it.message }
        loading = false
        searched = true
    }

    fun loadMore() {
        if (loading || nextOffset.isEmpty()) return
        loading = true
        scope.launch {
            repo.searchGlobal(query.trim(), nextOffset, 50)
                .onSuccess { (messages, next) ->
                    results = results + messages
                    nextOffset = next
                    resolveNames(messages)
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск по всем чатам") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Что искать") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )

            error?.let {
                Text("Ошибка: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
            }

            when {
                loading && results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                searched && results.isEmpty() && error == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium)
                    }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.chatId to it.id }) { m ->
                        ResultCard(
                            chat = chatNames.value[m.chatId] ?: "…",
                            sender = senderNames.value[m.senderId.hashCode().toLong()] ?: "",
                            date = m.date,
                            snippet = messageBody(m.content),
                            onClick = { onOpenChat(m.chatId) },
                        )
                    }
                    if (nextOffset.isNotEmpty()) {
                        item {
                            LaunchedEffect(results.size) { loadMore() }
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(chat: String, sender: String, date: Int, snippet: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(chat, fontWeight = FontWeight.SemiBold)
            if (sender.isNotBlank()) {
                Text(sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            Text(formatDateTime(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
