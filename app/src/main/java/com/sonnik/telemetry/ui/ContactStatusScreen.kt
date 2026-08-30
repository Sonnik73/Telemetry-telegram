package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ContactStatus
import com.sonnik.telemetry.data.ContactStatusKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactStatusScreen(onBack: () -> Unit, onOpenDossier: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var scanning by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<ContactStatus>?>(null) }

    fun scan() {
        if (scanning) return
        scanning = true
        done = 0; total = 0
        scope.launch {
            val list = app.chats.scanContacts { d, t -> done = d; total = t }
            results = list
            scanning = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Контакты: статус") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Статус контактов",
                        listOf(
                            "«Удалённые аккаунты» — контакт удалил свой Telegram.",
                            "«Возможно удалили/заблокировали вас» — вы есть у них в контактах, а вас у них нет: " +
                                "человек убрал вас из контактов или заблокировал. Это эвристика, не 100% доказательство.",
                            "Нажмите «Обновить», чтобы опросить адресную книгу. Тап по контакту — досье.",
                        ),
                    )
                    IconButton(onClick = { scan() }, enabled = !scanning) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                scanning -> {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { if (total > 0) done.toFloat() / total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Проверяю контакты: $done из $total…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                results == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Проверьте, кто из контактов удалил аккаунт или убрал вас из контактов.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            IconButton(onClick = { scan() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Проверить")
                            }
                        }
                    }
                }
                else -> {
                    val deleted = results!!.filter { it.kind == ContactStatusKind.DELETED }
                    val dropped = results!!.filter { it.kind == ContactStatusKind.NOT_IN_THEIR_CONTACTS }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "Всего контактов: ${results!!.size} · удалённых: ${deleted.size} · " +
                                    "вас нет в их контактах: ${dropped.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (dropped.isNotEmpty()) {
                            item { SectionTitle("Возможно удалили/заблокировали вас") }
                            items(dropped, key = { "d${it.userId}" }) { c ->
                                ContactStatusCard(c, MaterialTheme.colorScheme.error) { onOpenDossier(c.userId) }
                            }
                        }
                        if (deleted.isNotEmpty()) {
                            item { SectionTitle("Удалённые аккаунты") }
                            items(deleted, key = { "x${it.userId}" }) { c ->
                                ContactStatusCard(c, MaterialTheme.colorScheme.onSurfaceVariant) { onOpenDossier(c.userId) }
                            }
                        }
                        if (dropped.isEmpty() && deleted.isEmpty()) {
                            item {
                                Text(
                                    "Все контакты на месте: никто не удалил аккаунт и все держат вас в контактах.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ContactStatusCard(contact: ContactStatus, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold)
            Text(
                when (contact.kind) {
                    ContactStatusKind.DELETED -> "Аккаунт удалён"
                    ContactStatusKind.NOT_IN_THEIR_CONTACTS -> "Вас нет в их контактах"
                    ContactStatusKind.OK -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}
