package com.sonnik.telemetry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.BuildConfig
import com.sonnik.telemetry.security.SecureCache
import com.sonnik.telemetry.ui.theme.ThemeController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val mode by ThemeController.mode
    var wiped by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Настройки",
                        listOf(
                            "Тема оформления: как в системе, всегда светлая или всегда тёмная.",
                            "Изменения применяются сразу ко всему приложению.",
                            "Приватность: временные незашифрованные копии медиа (нужны для открытия во внешних приложениях) стираются при каждом запуске, а кнопкой — прямо сейчас.",
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Тема оформления", style = MaterialTheme.typography.titleMedium)
                    ThemeOption("Как в системе", ThemeController.SYSTEM, mode) { ThemeController.set(context, it) }
                    ThemeOption("Светлая", ThemeController.LIGHT, mode) { ThemeController.set(context, it) }
                    ThemeOption("Тёмная", ThemeController.DARK, mode) { ThemeController.set(context, it) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Приватность", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "При открытии медиа во внешнем приложении создаётся временная " +
                            "незашифрованная копия. Копии удаляются при каждом запуске приложения; " +
                            "здесь их можно стереть прямо сейчас.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            val freed = SecureCache.wipeSharedMedia(context)
                            wiped = "Удалено временных файлов: ${freed / 1024} КБ"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Стереть временные копии") }
                    wiped?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(
                "Telemetry · версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeOption(label: String, value: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected == value, onClick = { onSelect(value) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}
