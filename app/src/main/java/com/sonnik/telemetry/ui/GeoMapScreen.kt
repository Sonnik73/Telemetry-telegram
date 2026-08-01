package com.sonnik.telemetry.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.geo.GeoPoint
import com.sonnik.telemetry.geo.GeoShare
import com.sonnik.telemetry.geo.OsmTileMap
import com.sonnik.telemetry.geo.TrackStats
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoMapScreen(chatId: Long, messageId: Long, onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val context = LocalContext.current
    val active by app.geo.active.collectAsState()
    val share: GeoShare? = active.firstOrNull { it.chatId == chatId && it.messageId == messageId }

    var zoom by remember { mutableIntStateOf(15) }
    var track by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var stats by remember { mutableStateOf<TrackStats?>(null) }

    // Refresh the recorded track whenever a new point lands (share.updatedAt bumps).
    LaunchedEffect(share?.updatedAt) {
        val t = app.geo.store.track(chatId, messageId)
        track = t
        stats = app.geo.store.stats(t)
    }

    val lat = share?.lat ?: track.lastOrNull()?.lat ?: 0.0
    val lon = share?.lon ?: track.lastOrNull()?.lon ?: 0.0
    val heading = share?.heading ?: track.lastOrNull()?.heading ?: 0
    val accuracy = share?.accuracy ?: track.lastOrNull()?.accuracy ?: 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(share?.title ?: "Трансляция геопозиции") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                if (lat != 0.0 || lon != 0.0) {
                    OsmTileMap(
                        centerLat = lat,
                        centerLon = lon,
                        zoom = zoom,
                        track = track,
                        headingDeg = heading,
                        accuracyMeters = accuracy,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Ждём первую точку…")
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(onClick = { zoom = (zoom + 1).coerceAtMost(19) }) {
                        Icon(Icons.Default.Add, contentDescription = "Приблизить")
                    }
                    IconButton(onClick = { zoom = (zoom - 1).coerceAtLeast(3) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Отдалить")
                    }
                }
            }

            val live = share != null
            Text(
                if (live) "● В эфире" else "Трансляция завершена — показан записанный трек",
                color = if (live) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val s = stats
                    StatLine("Координаты", "%.5f, %.5f".format(lat, lon))
                    StatLine("Точность", if (accuracy > 0) "±${accuracy.toInt()} м" else "—")
                    StatLine("Направление", "$heading°")
                    if (s != null) {
                        StatLine("Точек в треке", formatCount(s.points))
                        StatLine("Пройдено", formatDistance(s.distanceMeters))
                        StatLine("Средняя скорость", "%.1f км/ч".format(s.avgSpeedKmh))
                        StatLine("Макс. скорость", "%.1f км/ч".format(s.maxSpeedKmh))
                        StatLine("Длительность", formatDurationSec(s.durationSec))
                    }
                }
            }

            FilledTonalButton(
                onClick = {
                    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть в картах")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) String.format(Locale.US, "%.2f км", meters / 1000) else "${meters.toInt()} м"

private fun formatDurationSec(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h ч $m м" else "$m м"
}
