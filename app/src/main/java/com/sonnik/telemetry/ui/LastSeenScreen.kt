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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.sonnik.telemetry.data.SeenKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastSeenScreen(onBack: () -> Unit, onOpenDossier: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<LastSeenEntry>?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    fun load() {
        if (loading) return
        loading = true
        done = 0
        total = 0
        scope.launch {
            entries = app.chats.contactsLastSeen { d, t -> done = d; total = t }
            loading = false
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "Контактов: ${entries!!.size} · сейчас в сети: $online",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(entries!!, key = { it.userId }) { e ->
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
                                        Text(e.name, fontWeight = FontWeight.Medium)
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
