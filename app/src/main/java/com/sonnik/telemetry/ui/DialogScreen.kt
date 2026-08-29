package com.sonnik.telemetry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogScreen(chatId: Long, onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val repo = app.messages
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }
    var loading by remember { mutableStateOf(true) }
    var stealth by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val stealthState = rememberUpdatedState(stealth)
    var loadingMore by remember { mutableStateOf(false) }
    var reachedTop by remember { mutableStateOf(false) }
    var primed by remember { mutableStateOf(false) }

    fun sortedInsert(msg: Message) {
        if (messages.any { it.id == msg.id }) return
        val idx = messages.indexOfFirst { it.date > msg.date }
        if (idx < 0) messages.add(msg) else messages.add(idx, msg)
    }

    LaunchedEffect(chatId) {
        title = app.chats.getChat(chatId)?.title ?: "Диалог"
        repo.loadHistory(chatId, fromMessageId = 0, limit = 60)
            .onSuccess { history -> history.sortedBy { it.date }.forEach { sortedInsert(it) } }
        loading = false
    }

    // Real-time incoming/outgoing messages for this chat.
    LaunchedEffect(chatId) {
        app.telegram.client.newMessageUpdates.collect { update ->
            if (update.message.chatId == chatId) {
                sortedInsert(update.message)
                if (!stealthState.value && !update.message.isOutgoing) {
                    repo.markRead(chatId, longArrayOf(update.message.id))
                }
            }
        }
    }

    // Entering non-stealth marks everything read and opens the chat; leaving closes it.
    LaunchedEffect(stealth, loading) {
        if (!stealth && !loading) {
            repo.openChat(chatId)
            repo.markRead(chatId, messages.filter { !it.isOutgoing }.map { it.id }.toLongArray())
        }
    }
    DisposableEffect(chatId) {
        onDispose { scope.launch { repo.closeChat(chatId) } }
    }

    // Scroll to the bottom on initial load and whenever a newer message arrives at
    // the end — keyed on the newest id, so prepending older history does NOT jump.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            primed = true
        }
    }

    // Load older history when the user scrolls to the very top.
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atTop, primed) {
        if (atTop && primed && !loadingMore && !reachedTop && !loading && messages.isNotEmpty()) {
            loadingMore = true
            val oldest = messages.first().id
            val before = messages.size
            repo.loadHistory(chatId, fromMessageId = oldest, limit = 40)
                .onSuccess { older -> older.forEach { sortedInsert(it) } }
            if (messages.size == before) reachedTop = true
            loadingMore = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (stealth) "скрытый режим — читаете невидимо" else "обычный режим — сообщения помечаются прочитанными",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (stealth) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { stealth = !stealth }) {
                        Icon(
                            if (stealth) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Переключить скрытый режим",
                            tint = if (stealth) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge: lift the reply field above the keyboard when it opens.
                .imePadding(),
        ) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (loadingMore) {
                    item(key = "loading_more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
            }

            if (stealth) {
                Surface(tonalElevation = 2.dp) {
                    Text(
                        "Скрытый режим: собеседник не видит, что вы читаете. Чтобы ответить — выключите режим (значок глаза).",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Сообщение…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                    )
                    IconButton(
                        enabled = !sending && input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            input = ""
                            sending = true
                            scope.launch {
                                repo.sendText(chatId, text)
                                sending = false
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val outgoing = msg.isOutgoing
    val bubbleColor = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val att = mediaAttachment(msg.content)
            if (att != null) {
                MediaView(att)
                val caption = captionOf(msg)
                if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(bodyOf(msg), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                formatDateTime(msg.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private fun captionOf(msg: Message): String = when (val c = msg.content) {
    is MessagePhoto -> c.caption.text
    is MessageVideo -> c.caption.text
    is MessageAnimation -> c.caption.text
    is MessageDocument -> c.caption.text
    is MessageAudio -> c.caption.text
    is MessageVoiceNote -> c.caption.text
    else -> ""
}

private fun bodyOf(msg: Message): String = when (val c = msg.content) {
    is MessageText -> c.text.text
    is MessagePhoto -> "🖼 Фото" + captionSuffix(c.caption.text)
    is MessageVideo -> "🎬 Видео" + captionSuffix(c.caption.text)
    is MessageDocument -> "📎 ${c.document.fileName.ifBlank { "файл" }}"
    is MessageAudio -> "🎵 ${c.audio.title.ifBlank { "аудио" }}"
    is MessageVoiceNote -> "🎤 Голосовое"
    is MessageVideoNote -> "⭕ Видеосообщение"
    is MessageAnimation -> "GIF"
    is MessageSticker -> "${c.sticker.emoji} стикер"
    else -> "[${c::class.simpleName?.removePrefix("Message") ?: "сообщение"}]"
}

private fun captionSuffix(caption: String): String = if (caption.isNotBlank()) ": $caption" else ""
