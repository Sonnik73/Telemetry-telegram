package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.presence.Overlap
import com.sonnik.telemetry.presence.PresenceAnalysis
import com.sonnik.telemetry.presence.WatchedUser

/**
 * Compares the online history of two tracked contacts and shows when they were
 * online at the same time. Works purely on sessions the tracker already recorded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoPresenceScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val watched = remember { app.presence.store.watchedUsers() }

    var first by remember { mutableStateOf<WatchedUser?>(null) }
    var second by remember { mutableStateOf<WatchedUser?>(null) }
    var firstOpen by remember { mutableStateOf(false) }
    var secondOpen by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(7) }
    var result by remember { mutableStateOf<List<Overlap>?>(null) }

    fun compare() {
        val a = first ?: return
        val b = second ?: return
        val since = System.currentTimeMillis() / 1000 - days * 24 * 3600L
        val sessionsA = app.presence.store.sessions(a.userId, since).sortedBy { it.startSec }
        val sessionsB = app.presence.store.sessions(b.userId, since).sortedBy { it.startSec }
        result = PresenceAnalysis.overlaps(sessionsA, sessionsB)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Со-присутствие") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Со-присутствие двух контактов",
                        listOf(
                            "Показывает, когда двое отслеживаемых контактов были в сети одновременно.",
                            "Считается по записям онлайн-трекера, поэтому оба человека должны быть в списке отслеживания, и данные копятся только пока трекер работает.",
                            "Совпадение по времени не означает, что люди общались между собой, — это лишь пересечение периодов активности.",
                            "Точное время онлайна доступно, только если человек не скрыл его в приватности.",
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (watched.isEmpty()) {
                Text(
                    "В онлайн-трекере нет контактов. Добавьте хотя бы двоих, чтобы сравнивать.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Кого сравниваем", fontWeight = FontWeight.SemiBold)

                    OutlinedButton(onClick = { firstOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(first?.title ?: "Первый контакт")
                    }
                    DropdownMenu(expanded = firstOpen, onDismissRequest = { firstOpen = false }) {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            watched.forEach { user ->
                                DropdownMenuItem(
                                    text = { Text(user.title) },
                                    onClick = { first = user; firstOpen = false; result = null },
                                )
                            }
                        }
                    }

                    OutlinedButton(onClick = { secondOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(second?.title ?: "Второй контакт")
                    }
                    DropdownMenu(expanded = secondOpen, onDismissRequest = { secondOpen = false }) {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            watched.forEach { user ->
                                DropdownMenuItem(
                                    text = { Text(user.title) },
                                    onClick = { second = user; secondOpen = false; result = null },
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 7, 30).forEach { d ->
                            OutlinedButton(onClick = { days = d; result = null }) {
                                Text(if (days == d) "• $d дн" else "$d дн")
                            }
                        }
                    }

                    Button(
                        onClick = { compare() },
                        enabled = first != null && second != null && first?.userId != second?.userId,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Сравнить") }

                    if (first != null && first?.userId == second?.userId) {
                        Text(
                            "Выберите двух разных людей.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            val overlaps = result
            if (overlaps != null) {
                val total = overlaps.sumOf { it.durationSec }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Результат за $days дн.", style = MaterialTheme.typography.titleMedium)
                        Text("Пересечений: ${overlaps.size}")
                        Text("Вместе в сети: ${formatDuration(total)}")
                        if (overlaps.isEmpty()) {
                            Text(
                                "Одновременно в сети не были — либо данных трекера пока мало.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (overlaps.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Когда совпадали", style = MaterialTheme.typography.titleMedium)
                            overlaps.sortedByDescending { it.startSec }.take(50).forEachIndexed { index, o ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatDateTime(o.startSec.toInt()), style = MaterialTheme.typography.bodySmall)
                                    Text(formatDuration(o.durationSec), style = MaterialTheme.typography.bodySmall)
                                }
                                if (index < overlaps.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
