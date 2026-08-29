package com.sonnik.telemetry.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.intel.KeywordHit
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KeywordsScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    val changed by app.intel.changed.collectAsState()

    var words by remember { mutableStateOf(app.intel.keywords()) }
    var input by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<KeywordHit>>(emptyList()) }
    val chatNames = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    val senderNames = remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val sb = StringBuilder("keyword,chat,sender,date,text\n")
                fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                hits.forEach { h ->
                    sb.append(esc(h.keyword)).append(",")
                        .append(esc(chatNames.value[h.chatId] ?: h.chatId.toString())).append(",")
                        .append(esc(senderNames.value[h.senderId] ?: "")).append(",")
                        .append(esc(formatDateTime(h.at.toInt()))).append(",")
                        .append(esc(h.body)).append("\n")
                }
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun ensureBackground() {
        app.intel.start()
        app.presence.start()
        com.sonnik.telemetry.presence.PresenceService.start(context)
    }

    fun addWord() {
        val w = input.trim()
        if (w.isEmpty()) return
        app.intel.addKeyword(w)
        words = app.intel.keywords()
        input = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ensureBackground()
    }

    LaunchedEffect(changed) {
        val list = app.intel.store.keywordHits(limit = 500)
        hits = list
        val chatNeed = list.map { it.chatId }.toSet() - chatNames.value.keys
        if (chatNeed.isNotEmpty()) {
            val m = chatNames.value.toMutableMap()
            for (id in chatNeed) m[id] = app.chats.getChat(id)?.title ?: id.toString()
            chatNames.value = m
        }
        val senderNeed = list.map { it.senderId }.toSet() - senderNames.value.keys
        if (senderNeed.isNotEmpty()) {
            val m = senderNames.value.toMutableMap()
            for (id in senderNeed) {
                m[id] = app.chats.senderName(if (id > 0) MessageSenderUser(id) else MessageSenderChat(id))
            }
            senderNames.value = m
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отслеживание слов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { if (hits.isNotEmpty()) exportCsv.launch("keywords.csv") }, enabled = hits.isNotEmpty()) {
                        Icon(Icons.Default.Download, contentDescription = "Экспорт в CSV")
                    }
                    InfoButton(
                        "Отслеживание ключевых слов",
                        listOf(
                            "Фоновый сборщик проверяет новые сообщения во всех чатах и каналах, где вы состоите.",
                            "При совпадении с ключевым словом приходит пуш-уведомление и запись попадает в журнал ниже.",
                            "Совпадение — по вхождению без учёта регистра, в тексте и подписях к медиа.",
                            "Работает, пока запущен трекер (фоновый сервис).",
                            "Тап по записи открывает нужный чат.",
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Новое слово или фраза") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { addWord() }, enabled = input.isNotBlank()) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (words.isEmpty()) {
                    Text(
                        "Пока нет слов. Добавьте слово — и приложение будет присылать уведомление, " +
                            "когда оно встретится в новых сообщениях.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        words.forEach { w ->
                            AssistChip(
                                onClick = { app.intel.removeKeyword(w); words = app.intel.keywords() },
                                label = { Text(w) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Удалить") },
                            )
                        }
                    }
                }
            }

            Text(
                "Найдено совпадений: ${hits.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(hits, key = { it.messageId to it.at }) { hit ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenChat(hit.chatId) }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "«${hit.keyword}»",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val where = listOfNotNull(
                                chatNames.value[hit.chatId],
                                senderNames.value[hit.senderId]?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (where.isNotEmpty()) {
                                Text(where, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(hit.body, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            Text(formatDateTime(hit.at.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
