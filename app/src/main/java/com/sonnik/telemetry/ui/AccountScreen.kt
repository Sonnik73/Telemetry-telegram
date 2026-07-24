package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.StorageStatistics
import dev.g000sha256.tdl.dto.StorageStatisticsFast
import dev.g000sha256.tdl.dto.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val client = app.telegram.client

    var me by remember { mutableStateOf<User?>(null) }
    var fast by remember { mutableStateOf<StorageStatisticsFast?>(null) }
    var storage by remember { mutableStateOf<StorageStatistics?>(null) }
    var chatTitles by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        me = (client.getMe() as? TdlResult.Success)?.result
        fast = (client.getStorageStatisticsFast() as? TdlResult.Success)?.result
        val stats = (client.getStorageStatistics(chatLimit = 10) as? TdlResult.Success)?.result
        storage = stats
        if (stats != null) {
            val titles = HashMap<Long, String>()
            for (byChat in stats.byChat) {
                if (byChat.chatId == 0L) continue
                titles[byChat.chatId] = app.chats.getChat(byChat.chatId)?.title ?: "Chat ${byChat.chatId}"
            }
            chatTitles = titles
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аккаунт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loading) {
                CircularProgressIndicator()
            }

            me?.let { user ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            listOf(user.firstName, user.lastName).filter(String::isNotBlank).joinToString(" "),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        user.usernames?.activeUsernames?.firstOrNull()?.let { Text("@$it") }
                        Text("+${user.phoneNumber}")
                        Text("ID: ${user.id}", style = MaterialTheme.typography.bodySmall)
                        if (user.isPremium) Text("Telegram Premium", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            fast?.let { stats ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Локальное хранилище", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Кэш файлов (${formatCount(stats.fileCount)} шт.)")
                            Text(formatBytes(stats.filesSize))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("База данных")
                            Text(formatBytes(stats.databaseSize))
                        }
                    }
                }
            }

            storage?.takeIf { it.byChat.isNotEmpty() }?.let { stats ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Топ чатов по размеру кэша", style = MaterialTheme.typography.titleMedium)
                        stats.byChat.sortedByDescending { it.size }.take(10).forEach { byChat ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (byChat.chatId == 0L) "Прочее" else chatTitles[byChat.chatId] ?: "Chat ${byChat.chatId}",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(formatBytes(byChat.size))
                            }
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Всего")
                            Text(formatBytes(stats.size), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            Button(
                onClick = { app.telegram.logOut() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти из аккаунта")
            }
        }
    }
}
