package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.sonnik.telemetry.data.ContactStoryGroup
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryFeedScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<ContactStoryGroup>?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    fun load() {
        if (loading) return
        loading = true
        done = 0
        total = 0
        scope.launch {
            groups = app.chats.contactStories { d, t -> done = d; total = t }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Истории контактов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Истории контактов",
                        listOf(
                            "Показывает активные истории ваших контактов и позволяет скачать фото/видео из них.",
                            "Сохранение работает и для историй, в которых автор запретил пересылку — приложение скачивает медиа напрямую через TDLib.",
                            "Истории видны, только пока не истекли (обычно 24 часа).",
                            "Просмотр здесь не помечает историю как «просмотренную» у автора.",
                            "Нажмите «Обновить», чтобы опросить контакты заново.",
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
                            Text("Опрос контактов: $done / $total", style = MaterialTheme.typography.labelMedium)
                            LinearProgressIndicator(
                                progress = { done.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                            )
                        }
                    }
                }
                groups == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Посмотрите и скачайте истории, которые публикуют ваши контакты.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, contentDescription = "Загрузить") }
                    }
                }
                groups!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Сейчас ни у кого из контактов нет активных историй.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "Контактов с историями: ${groups!!.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(groups!!, key = { it.chatId }) { group ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(group.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "историй: ${group.stories.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    for (story in group.stories) {
                                        val att = storyAttachment(story.content)
                                        if (att != null) {
                                            MediaView(att)
                                        } else {
                                            Text(
                                                "неподдерживаемый тип истории",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (story.caption.isNotBlank()) {
                                            Text(story.caption, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            formatDateTime(story.date),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
