package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ChatKind
import com.sonnik.telemetry.data.ChatSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var chats by remember { mutableStateOf<List<ChatSummary>?>(null) }
    var selected by remember { mutableStateOf<ChatSummary?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ConfirmTarget?>(null) }
    var beforeDate by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }

    // Parses the optional "delete only before this date" field (дд.мм.гггг).
    fun cutoffDate(): Int? {
        val text = beforeDate.trim()
        if (text.isBlank()) return null
        return runCatching {
            val parts = text.split(".", "-", "/")
            val cal = java.util.Calendar.getInstance()
            cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt(), 0, 0, 0)
            (cal.timeInMillis / 1000).toInt()
        }.getOrNull()
    }

    LaunchedEffect(Unit) {
        chats = app.chats.loadAllChats().getOrNull().orEmpty()
    }

    fun runDeleteOne(chat: ChatSummary) {
        running = true
        status = "Удаляю мои сообщения в «${chat.title}»…"
        scope.launch {
            val n = app.chats.deleteMyMessages(chat.id, cutoffDate()) { done -> status = "Удалено: $done…" }
            running = false
            status = if (n < 0) "Готово: удаление отправлено на сервер." else "Готово: удалено сообщений — $n."
        }
    }

    fun runDeleteAll() {
        running = true
        status = "Готовлю список чатов…"
        scope.launch {
            val all = chats ?: app.chats.loadAllChats().getOrNull().orEmpty()
            var totalDeleted = 0
            all.forEachIndexed { index, c ->
                status = "(${index + 1}/${all.size}) «${c.title}»… всего удалено: $totalDeleted"
                val n = app.chats.deleteMyMessages(c.id, cutoffDate()) { }
                if (n > 0) totalDeleted += n
            }
            running = false
            status = "Готово. Удалено сообщений (без учёта серверных пакетов): $totalDeleted."
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Чистильщик следов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Чистильщик следов",
                        listOf(
                            "Массово удаляет ВАШИ сообщения — «у всех» (revoke) — в выбранном чате или во всех сразу.",
                            "В группах/каналах используется серверное удаление по отправителю; в личных чатах — постранично ваши исходящие сообщения.",
                            "Удаляются только ваши сообщения. Действие необратимо.",
                            "Удаление «во всех чатах» может занять время и затрагивает всю историю.",
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Удаляет только ваши собственные сообщения, «у всех». Действие необратимо — используйте осознанно.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = beforeDate,
                onValueChange = { beforeDate = it },
                label = { Text("Удалять только старше даты (необязательно)") },
                placeholder = { Text("дд.мм.гггг") },
                singleLine = true,
                enabled = !running,
                supportingText = {
                    Text(
                        if (beforeDate.isBlank()) {
                            "Пусто — удалять всю переписку."
                        } else if (cutoffDate() == null) {
                            "Не разобрал дату — укажите в формате дд.мм.гггг."
                        } else {
                            "Будут удалены сообщения раньше этой даты."
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("В одном чате", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { menuOpen = true }, enabled = !running && chats != null) {
                        Text(selected?.title ?: "Выберите чат")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                            chats.orEmpty().forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.title + kindSuffix(c.kind)) },
                                    onClick = {
                                        selected = c
                                        menuOpen = false
                                        preview = null
                                        scope.launch {
                                            preview = "Считаю…"
                                            val n = app.chats.countMyMessages(c.id)
                                            preview = if (n == null) {
                                                "Не удалось посчитать — сервер не отдаёт счётчик."
                                            } else {
                                                "Ваших сообщений в этом чате: ≈$n"
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    preview?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { selected?.let { confirm = ConfirmTarget.One(it) } },
                        enabled = !running && selected != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Удалить мои сообщения здесь") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Во всех чатах", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Пройдёт по всем чатам и удалит все ваши сообщения. Это надолго и необратимо.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { confirm = ConfirmTarget.All },
                        enabled = !running && chats != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Удалить мои сообщения везде") }
                }
            }

            if (running) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            } else {
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    val target = confirm
    if (target != null) {
        val scope2 = if (cutoffDate() != null) " Только сообщения раньше ${beforeDate.trim()}." else ""
        val (title, body) = when (target) {
            is ConfirmTarget.One -> "Удалить в «${target.chat.title}»?" to
                ("Ваши сообщения в этом чате будут удалены у всех.$scope2 Отменить нельзя." +
                    (preview?.let { "\n\n$it" } ?: ""))
            ConfirmTarget.All -> "Удалить во всех чатах?" to
                "Ваши сообщения во всех чатах будут удалены у всех.$scope2 " +
                "Это необратимо и может занять много времени."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (target) {
                        is ConfirmTarget.One -> runDeleteOne(target.chat)
                        ConfirmTarget.All -> runDeleteAll()
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

private sealed interface ConfirmTarget {
    data class One(val chat: ChatSummary) : ConfirmTarget
    data object All : ConfirmTarget
}

private fun kindSuffix(kind: ChatKind): String = when (kind) {
    ChatKind.PRIVATE -> ""
    ChatKind.GROUP -> " (группа)"
    ChatKind.CHANNEL -> " (канал)"
    ChatKind.SECRET -> " (секрет)"
}
