package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.LastSeenEntry
import com.sonnik.telemetry.data.ScanCache
import com.sonnik.telemetry.data.SeenKind
import com.sonnik.telemetry.presence.PresenceAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastSeenScreen(onBack: () -> Unit, onOpenDossier: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<LastSeenEntry>?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var onlyOnline by remember { mutableStateOf(false) }
    var cacheTime by remember { mutableLongStateOf(0L) }
    // Best hours to reach a contact, for those tracked by the presence tracker.
    var bestHours by remember { mutableStateOf<Map<Long, List<Int>>>(emptyMap()) }

    fun load() {
        if (loading) return
        loading = true
        done = 0
        total = 0
        scope.launch {
            entries = app.chats.contactsLastSeen { d, t -> done = d; total = t }
            ScanCache.lastSeen = entries
            ScanCache.lastSeenTime = System.currentTimeMillis()
            cacheTime = ScanCache.lastSeenTime
            bestHours = withContext(Dispatchers.IO) {
                val since = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
                app.presence.store.watchedUsers().associate { user ->
                    val sessions = runCatching { app.presence.store.sessions(user.userId, since) }
                        .getOrDefault(emptyList())
                    user.userId to PresenceAnalysis.profile(sessions).bestHours(2)
                }
            }
            loading = false
        }
    }

    // Show the last scan immediately on entry; refresh re-polls.
    LaunchedEffect(Unit) {
        if (entries == null) {
            val cached = ScanCache.lastSeen
            if (cached != null) {
                entries = cached
                cacheTime = ScanCache.lastSeenTime
                bestHours = withContext(Dispatchers.IO) {
                    val since = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
                    app.presence.store.watchedUsers().associate { user ->
                        val sessions = runCatching { app.presence.store.sessions(user.userId, since) }
                            .getOrDefault(emptyList())
                        user.userId to PresenceAnalysis.profile(sessions).bestHours(2)
                    }
                }
            } else {
                load()
            }
        }
    }

    // Keep the list live: TDLib pushes status changes as they happen.
    LaunchedEffect(Unit) {
        app.telegram.client.userStatusUpdates.collect { update ->
            val current = entries ?: return@collect
            val index = current.indexOfFirst { it.userId == update.userId }
            if (index < 0) return@collect
            val (kind, was) = app.chats.classifyStatus(update.status)
            entries = current.toMutableList().also {
                it[index] = it[index].copy(kind = kind, wasOnline = was)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Последний онлайн") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Последний онлайн контактов",
                        listOf(
                            "Единый список всех контактов с временем последнего захода, отсортированный «кто был недавно».",
                            "Точное время видно только у тех, кто не скрыл «был(а) в сети» в настройках приватности; у остальных — «недавно», «на неделе», «в этом месяце».",
                            "Нажмите на контакт, чтобы открыть досье.",
                            "«Обновить» — опросить статусы заново.",
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
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        if (total > 0) {
                            Text("Опрос: $done / $total", style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress = { done.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                            )
                        }
                    }
                }
                entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, contentDescription = "Загрузить") }
                }
                entries!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет контактов.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
                }
                else -> {
                    val online = entries!!.count { it.kind == SeenKind.ONLINE }
                    val shown = if (onlyOnline) entries!!.filter { it.kind == SeenKind.ONLINE } else entries!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    buildString {
                                        append("Контактов: ${entries!!.size} · в сети: $online")
                                        val age = ScanCache.ageLabel(cacheTime)
                                        if (age.isNotEmpty()) append(" · $age")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilterChip(
                                    selected = onlyOnline,
                                    onClick = { onlyOnline = !onlyOnline },
                                    label = { Text("только в сети") },
                                )
                            }
                        }
                        items(shown, key = { it.userId }) { e ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { onOpenDossier(e.userId) }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            Modifier.size(10.dp).clip(CircleShape)
                                                .background(if (e.kind == SeenKind.ONLINE) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant),
                                        )
                                        Column {
                                            Text(e.name, fontWeight = FontWeight.Medium)
                                            bestHours[e.userId]?.takeIf { it.isNotEmpty() }?.let { hours ->
                                                Text(
                                                    "обычно в сети: " + hours.joinToString(", ") { "%02d:00".format(it) },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        lastSeenLabel(e),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (e.kind == SeenKind.ONLINE) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun lastSeenLabel(e: LastSeenEntry): String = when (e.kind) {
    SeenKind.ONLINE -> "в сети"
    SeenKind.OFFLINE -> "был(а): ${formatDateTime(e.wasOnline)}"
    SeenKind.RECENTLY -> "недавно"
    SeenKind.LAST_WEEK -> "на этой неделе"
    SeenKind.LAST_MONTH -> "в этом месяце"
    SeenKind.LONG_AGO -> "давно / скрыто"
}
