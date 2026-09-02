package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.intel.ArchiveEvent
import com.sonnik.telemetry.intel.ContactChange
import dev.g000sha256.tdl.TdlResult

private data class Dossier(
    val name: String,
    val username: String,
    val phone: String,
    val premium: Boolean,
    val bio: String,
    val birthdate: String,
    val avatarCount: Int,
    val commonGroups: Int,
    val online: Boolean,
    val lastSeen: Int,
    val changes: List<ContactChange>,
    val events: List<ArchiveEvent>,
    // Everything else the app has collected about this person.
    val capturedCount: Int,
    val typingCount: Int,
    val lastTyping: com.sonnik.telemetry.intel.TypingEvent?,
    val onlineSeconds: Long,
    val onlineSessions: Int,
    val bestHours: List<Int>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DossierScreen(userId: Long, onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val client = app.telegram.client
    var dossier by remember { mutableStateOf<Dossier?>(null) }

    LaunchedEffect(userId) {
        val user = (client.getUser(userId) as? TdlResult.Success)?.result
        val full = (client.getUserFullInfo(userId) as? TdlResult.Success)?.result
        val photos = (client.getUserProfilePhotos(userId, 0, 1) as? TdlResult.Success)?.result
        val birth = full?.birthdate
        val presence = app.presence.store.watchedUsers().firstOrNull { it.userId == userId }
        val typing = app.intel.store.typingSummary(userId)
        // Presence profile over the last week, if this contact is tracked.
        val weekAgo = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
        val sessions = runCatching { app.presence.store.sessions(userId, weekAgo) }.getOrDefault(emptyList())
        val profile = com.sonnik.telemetry.presence.PresenceAnalysis.profile(sessions)
        dossier = Dossier(
            name = user?.let { listOf(it.firstName, it.lastName).filter(String::isNotBlank).joinToString(" ") }?.ifBlank { "ID $userId" } ?: "ID $userId",
            username = user?.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" } ?: "",
            phone = user?.phoneNumber?.let { if (it.isNotBlank()) "+$it" else "" } ?: "",
            premium = user?.isPremium ?: false,
            bio = full?.bio?.text ?: "",
            birthdate = birth?.let { b -> "${b.day}.${b.month}" + if (b.year > 0) ".${b.year}" else "" } ?: "",
            avatarCount = photos?.totalCount ?: 0,
            commonGroups = full?.groupInCommonCount ?: 0,
            online = presence?.online ?: false,
            lastSeen = presence?.lastSeen ?: 0,
            changes = app.intel.store.contactChanges(userId, 50),
            events = app.intel.store.events(limit = 50, userId = userId),
            capturedCount = app.intel.store.capturedCountBySender(userId),
            typingCount = typing.first,
            lastTyping = typing.second,
            onlineSeconds = profile.totalSeconds,
            onlineSessions = profile.sessionCount,
            bestHours = profile.bestHours(),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dossier?.name ?: "Досье") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        val d = dossier
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.name, style = MaterialTheme.typography.titleLarge)
                    if (d.username.isNotEmpty()) Text(d.username, color = MaterialTheme.colorScheme.primary)
                    if (d.phone.isNotEmpty()) Info("Телефон", d.phone)
                    if (d.bio.isNotEmpty()) Info("Bio", d.bio)
                    if (d.birthdate.isNotEmpty()) Info("Дата рождения", d.birthdate)
                    if (d.premium) Text("Telegram Premium", color = MaterialTheme.colorScheme.primary)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Данные", style = MaterialTheme.typography.titleMedium)
                    Info("Аватарок в профиле", d.avatarCount.toString())
                    Info("Общих групп", d.commonGroups.toString())
                    Info(
                        "Статус",
                        when {
                            d.online -> "в сети"
                            d.lastSeen > 0 -> "был ${formatDateTime(d.lastSeen)}"
                            else -> "нет данных (добавьте в онлайн-трекер)"
                        },
                    )
                }
            }

            // Everything the app's own collectors have gathered about this person.
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Собрано приложением", style = MaterialTheme.typography.titleMedium)
                    Info("Перехвачено одноразовых медиа", d.capturedCount.toString())
                    Info("Пойманных удалений/правок", d.events.size.toString())
                    Info("Смен профиля в журнале", d.changes.size.toString())
                    Info(
                        "Событий «печатает»",
                        if (d.typingCount == 0) "0" else "${d.typingCount} · последнее: " +
                            "${d.lastTyping?.action ?: ""} ${d.lastTyping?.let { formatDateTime(it.at.toInt()) } ?: ""}",
                    )
                    if (d.onlineSessions > 0) {
                        Info("В сети за 7 дней", formatDuration(d.onlineSeconds))
                        Info("Сессий онлайн", d.onlineSessions.toString())
                        if (d.bestHours.isNotEmpty()) {
                            Info(
                                "Лучшее время написать",
                                d.bestHours.joinToString(", ") { "%02d:00–%02d:00".format(it, (it + 1) % 24) },
                            )
                        }
                    }
                }
            }

            if (d.changes.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("История изменений профиля", style = MaterialTheme.typography.titleMedium)
                        d.changes.forEach { ch ->
                            Text(
                                "${formatDateTime(ch.at.toInt())}: ${ch.field}" +
                                    if (ch.field != "фото профиля") " «${ch.oldValue}» → «${ch.newValue}»" else " изменено",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (d.events.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Удалённое и правки от контакта", style = MaterialTheme.typography.titleMedium)
                        d.events.forEach { e ->
                            Text(
                                "${formatDateTime(e.at.toInt())} · ${if (e.kind == "deleted") "удалено" else "изменено"}: ${e.oldBody}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
