package com.sonnik.telemetry.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.WatchCandidate
import com.sonnik.telemetry.presence.PresenceService
import com.sonnik.telemetry.presence.WatchedUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(onBack: () -> Unit, onOpenUser: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val context = LocalContext.current
    val tracker = app.presence
    val changed by tracker.changed.collectAsState()

    var watched by remember { mutableStateOf<List<WatchedUser>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        tracker.start()
    }

    // Reload the list whenever a status change is signalled.
    LaunchedEffect(changed) {
        watched = tracker.store.watchedUsers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Онлайн-трекер") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Онлайн-трекер",
                        listOf(
                            "Наблюдение за присутствием выбранных контактов (чего нет в офиц. клиенте).",
                            "Пуш-уведомление, когда человек появляется в сети.",
                            "Статистика: время «в сети» за 7 дней, число и длительность сессий, активность по часам.",
                            "Работает в фоне (иконка в шторке), пока в списке есть хотя бы один контакт; переживает перезагрузку.",
                            "Точное время доступно, только если человек не скрыл его в приватности Telegram.",
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить контакт")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Наблюдение работает в фоне, пока в списке есть хотя бы один контакт. " +
                    "Точное время «в сети» видно, только если человек не скрыл его в настройках приватности.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            if (watched.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нажмите + и выберите контакт для наблюдения")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(watched, key = { it.userId }) { user ->
                        WatchedRow(
                            user = user,
                            onClick = { onOpenUser(user.userId) },
                            onRemove = {
                                tracker.unwatch(user.userId)
                                watched = tracker.store.watchedUsers()
                                if (watched.isEmpty()) PresenceService.stop(context)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showPicker) {
        ContactPickerDialog(
            onDismiss = { showPicker = false },
            onPick = { candidate ->
                showPicker = false
                tracker.watch(candidate.userId, candidate.title)
                PresenceService.start(context)
                watched = tracker.store.watchedUsers()
            },
        )
    }
}

@Composable
private fun WatchedRow(user: WatchedUser, onClick: () -> Unit, onRemove: () -> Unit) {
    val statusText = when {
        user.online -> "в сети"
        user.lastSeen > 0 -> "был(а) ${formatDateTime(user.lastSeen)}"
        user.lastChangeAt > 0L -> "не в сети"
        else -> "статус скрыт или ещё не получен"
    }
    ListItem(
        headlineContent = { Text(user.title) },
        supportingContent = {
            Text(
                statusText,
                color = if (user.online) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (user.online) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Убрать")
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ContactPickerDialog(onDismiss: () -> Unit, onPick: (WatchCandidate) -> Unit) {
    val app = TelemetryApp.instance
    var candidates by remember { mutableStateOf<List<WatchCandidate>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        app.chats.privateUsers()
            .onSuccess { candidates = it }
            .onFailure { error = it.message }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        title = { Text("Выберите контакт") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    error != null -> Text("Ошибка: $error", color = MaterialTheme.colorScheme.error)
                    candidates == null -> Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    else -> {
                        val visible = candidates!!.filter { it.title.contains(query.trim(), ignoreCase = true) }
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(visible, key = { it.userId }) { candidate ->
                                Text(
                                    candidate.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(candidate) }
                                        .padding(vertical = 12.dp),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
    )
}
