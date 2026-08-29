package com.sonnik.telemetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.geo.GeoShare

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoScreen(onBack: () -> Unit, onOpenShare: (Long, Long) -> Unit) {
    val app = TelemetryApp.instance
    val geo = app.geo
    val active by geo.active.collectAsState()

    LaunchedEffect(Unit) { geo.start() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Геотрекинг") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Геотрекинг",
                        listOf(
                            "Когда контакт транслирует геопозицию в чате, здесь видно активные трансляции.",
                            "Тап открывает карту (OpenStreetMap) с точкой, направлением и кругом точности — обновляется сама.",
                            "Пишется трек: маршрут линией, расстояние, средняя и макс. скорость, длительность; экспорт в GPX.",
                            "Запустить трансляцию за человека нельзя — только смотреть ту, что он включил сам.",
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                "Здесь появляются контакты, которые прямо сейчас транслируют геопозицию в чатах. " +
                    "Точка и маршрут обновляются сами, пока трансляция активна. Запустить трансляцию за " +
                    "человека нельзя — только смотреть ту, что он включил.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            if (active.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет активных трансляций геопозиции")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(active, key = { "${it.chatId}:${it.messageId}" }) { share ->
                        ShareRow(share = share, onClick = { onOpenShare(share.chatId, share.messageId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareRow(share: GeoShare, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(share.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                "%.5f, %.5f · обновлено %s".format(share.lat, share.lon, formatDateTime(share.updatedAt.toInt())),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
