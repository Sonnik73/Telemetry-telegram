package com.sonnik.telemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.presence.OnlineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private data class TrackerStats(
    val title: String,
    val online: Boolean,
    val lastSeen: Int,
    val sessions7d: List<OnlineSession>,
    val hourSeconds: LongArray,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerStatsScreen(userId: Long, onBack: () -> Unit, onOpenDossier: () -> Unit) {
    val app = TelemetryApp.instance
    var stats by remember { mutableStateOf<TrackerStats?>(null) }

    LaunchedEffect(userId) {
        stats = withContext(Dispatchers.IO) { computeStats(userId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stats?.title ?: "Статистика присутствия") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDossier) {
                        Icon(Icons.Default.Badge, contentDescription = "Досье")
                    }
                },
            )
        },
    ) { padding ->
        val current = stats
        if (current == null) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сейчас", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Статус")
                        Text(
                            if (current.online) "в сети" else "не в сети",
                            color = if (current.online) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!current.online && current.lastSeen > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Был(а) в сети")
                            Text(formatDateTime(current.lastSeen))
                        }
                    }
                }
            }

            val totalSec = current.sessions7d.sumOf { it.durationSec }
            val count = current.sessions7d.size
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("За 7 дней (с момента наблюдения)", style = MaterialTheme.typography.titleMedium)
                    StatRow("Всего в сети", formatDuration(totalSec))
                    StatRow("Сессий онлайн", formatCount(count))
                    StatRow("Средняя сессия", if (count > 0) formatDuration(totalSec / count) else "—")
                    StatRow("В среднем в день", formatDuration(totalSec / 7))
                }
            }

            if (current.hourSeconds.any { it > 0 }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Когда обычно в сети (по часам)", style = MaterialTheme.typography.titleMedium)
                        HourChart(current.hourSeconds)
                    }
                }
            }

            if (current.sessions7d.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Последние сессии", style = MaterialTheme.typography.titleMedium)
                        current.sessions7d.sortedByDescending { it.startSec }.take(20).forEach { session ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDateTime(session.startSec.toInt()))
                                Text(formatDuration(session.durationSec))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HourChart(hourSeconds: LongArray) {
    val max = hourSeconds.max().coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(vertical = 4.dp),
        ) {
            val n = 24
            val gap = 2.dp.toPx()
            val barWidth = (size.width - gap * (n - 1)) / n
            val chartHeight = size.height - 1.dp.toPx()
            for (hour in 0 until n) {
                val h = (hourSeconds[hour].toFloat() / max) * chartHeight
                val left = hour * (barWidth + gap)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, chartHeight - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = CornerRadius(minOf(3.dp.toPx(), barWidth / 2f)),
                )
            }
            drawLine(
                color = axisColor,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall)
            Text("6", style = MaterialTheme.typography.labelSmall)
            Text("12", style = MaterialTheme.typography.labelSmall)
            Text("18", style = MaterialTheme.typography.labelSmall)
            Text("23", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun computeStats(userId: Long): TrackerStats {
    val app = TelemetryApp.instance
    val store = app.presence.store
    val user = store.watchedUsers().firstOrNull { it.userId == userId }
    val since = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
    val sessions = store.sessions(userId, since)

    // Distribute each session's duration across hour-of-day buckets.
    val zone = ZoneId.systemDefault()
    val hourSeconds = LongArray(24)
    for (session in sessions) {
        var cursor = session.startSec
        while (cursor < session.endSec) {
            val zdt = Instant.ofEpochSecond(cursor).atZone(zone)
            val hour = zdt.hour
            val nextHour = zdt.plusHours(1).withMinute(0).withSecond(0).withNano(0).toEpochSecond()
            val sliceEnd = minOf(session.endSec, nextHour)
            hourSeconds[hour] += (sliceEnd - cursor)
            cursor = sliceEnd
        }
    }

    return TrackerStats(
        title = store.watchedTitle(userId) ?: "Контакт",
        online = user?.online ?: false,
        lastSeen = user?.lastSeen ?: 0,
        sessions7d = sessions,
        hourSeconds = hourSeconds,
    )
}

