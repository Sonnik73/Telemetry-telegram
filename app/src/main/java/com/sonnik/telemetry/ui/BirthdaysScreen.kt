package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.TdlResult
import java.time.LocalDate
import java.util.Locale

private data class BirthdayEntry(
    val userId: Long,
    val name: String,
    val day: Int,
    val month: Int,
    val year: Int,
    val daysUntil: Int,
)

private val MONTHS = arrayOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysScreen(onBack: () -> Unit, onOpenDossier: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val client = app.telegram.client

    var entries by remember { mutableStateOf<List<BirthdayEntry>>(emptyList()) }
    var total by remember { mutableStateOf<Int?>(null) }
    var processed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val ids = (client.getContacts() as? TdlResult.Success)?.result?.userIds?.toList() ?: emptyList()
        total = ids.size
        val today = LocalDate.now()
        val found = ArrayList<BirthdayEntry>()
        for (id in ids) {
            val full = client.getUserFullInfo(id)
            val birthdate = (full as? TdlResult.Success)?.result?.birthdate
            if (birthdate != null && birthdate.month in 1..12 && birthdate.day in 1..31) {
                val name = when (val u = client.getUser(id)) {
                    is TdlResult.Success -> listOf(u.result.firstName, u.result.lastName).filter(String::isNotBlank).joinToString(" ").ifBlank { "ID $id" }
                    is TdlResult.Failure -> "ID $id"
                }
                found += BirthdayEntry(id, name, birthdate.day, birthdate.month, birthdate.year, daysUntil(today, birthdate.day, birthdate.month))
                entries = found.sortedBy { it.daysUntil }
            }
            processed++
        }
        running = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дни рождения контактов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (running) {
                val t = total
                if (t != null && t > 0) {
                    LinearProgressIndicator(
                        progress = { (processed.toFloat() / t).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    "Опрошено $processed из ${total ?: 0} контактов…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Text(
                "Дата рождения видна, только если контакт указал её и не скрыл в приватности.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.userId }) { e ->
                    ListItem(
                        headlineContent = { Text(e.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append("${e.day} ${MONTHS[e.month - 1]}")
                                    if (e.year > 0) append(" ${e.year} г.")
                                    append(" · ")
                                    append(
                                        when (e.daysUntil) {
                                            0 -> "сегодня 🎉"
                                            1 -> "завтра"
                                            else -> "через ${e.daysUntil} дн."
                                        },
                                    )
                                },
                            )
                        },
                        modifier = Modifier.clickable { onOpenDossier(e.userId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun daysUntil(today: LocalDate, day: Int, month: Int): Int {
    return try {
        var next = LocalDate.of(today.year, month, minOf(day, LocalDate.of(today.year, month, 1).lengthOfMonth()))
        if (next.isBefore(today)) {
            next = LocalDate.of(today.year + 1, month, minOf(day, LocalDate.of(today.year + 1, month, 1).lengthOfMonth()))
        }
        (next.toEpochDay() - today.toEpochDay()).toInt()
    } catch (_: Exception) {
        9999
    }
}
