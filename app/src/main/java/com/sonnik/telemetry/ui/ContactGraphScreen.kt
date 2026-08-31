package com.sonnik.telemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sonnik.telemetry.TelemetryApp
import com.sonnik.telemetry.data.ContactGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactGraphScreen(onBack: () -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var graph by remember { mutableStateOf<ContactGraph?>(null) }
    var layout by remember { mutableStateOf<Array<Offset>?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    fun load() {
        if (loading) return
        loading = true
        done = 0
        total = 0
        scale = 1f
        pan = Offset.Zero
        scope.launch {
            val g = app.chats.buildContactGraph { d, t -> done = d; total = t }
            val pos = withContext(Dispatchers.Default) { computeLayout(g) }
            graph = g
            layout = pos
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Граф связей") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    InfoButton(
                        "Граф связей контактов",
                        listOf(
                            "Строит сеть ваших контактов: две точки соединены, если у них есть хотя бы одна общая с вами группа.",
                            "Так видно кластеры знакомств — кто с кем пересекается.",
                            "Двигайте пальцем, масштабируйте щипком.",
                            "Построение опрашивает общие группы по каждому контакту, поэтому занимает время.",
                        ),
                    )
                    IconButton(onClick = { load() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Построить заново")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val g = graph
            val pos = layout
            when {
                loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Строю граф…", style = MaterialTheme.typography.bodyMedium)
                    if (total > 0) {
                        Text("Опрос контактов: $done / $total", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                        )
                    }
                }
                g == null || pos == null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Постройте граф связей ваших контактов.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                    IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, contentDescription = "Построить") }
                }
                g.nodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет контактов для построения.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    val labelColor = MaterialTheme.colorScheme.onSurface
                    val edgeColor = MaterialTheme.colorScheme.outlineVariant
                    val nodeColor = MaterialTheme.colorScheme.primary
                    val isolatedColor = MaterialTheme.colorScheme.outline
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTransformGestures { _, panChange, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.2f, 6f)
                                pan += panChange
                            }
                        },
                    ) {
                        // Fit the computed layout to the canvas, then apply user pan/zoom.
                        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
                        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                        for (p in pos) {
                            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
                            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
                        }
                        val spanX = max(maxX - minX, 1f)
                        val spanY = max(maxY - minY, 1f)
                        val margin = 60f
                        val fit = min2(
                            (size.width - 2 * margin) / spanX,
                            (size.height - 2 * margin) / spanY,
                        )
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val midX = (minX + maxX) / 2f
                        val midY = (minY + maxY) / 2f
                        fun screen(p: Offset): Offset {
                            val x = cx + (p.x - midX) * fit * scale + pan.x
                            val y = cy + (p.y - midY) * fit * scale + pan.y
                            return Offset(x, y)
                        }

                        for ((a, b) in g.edges) {
                            drawLine(edgeColor, screen(pos[a]), screen(pos[b]), strokeWidth = 1.5f)
                        }
                        val paint = android.graphics.Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 26f * max(scale, 0.6f).coerceAtMost(1.4f)
                            isAntiAlias = true
                        }
                        for (i in g.nodes.indices) {
                            val node = g.nodes[i]
                            val s = screen(pos[i])
                            val connected = node.degree > 0
                            val radius = (5f + node.degree.coerceAtMost(12)) * max(scale, 0.5f).coerceAtMost(1.6f)
                            drawCircle(if (connected) nodeColor else isolatedColor, radius, s)
                            if (connected && scale > 0.5f) {
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawText(node.name, s.x + radius + 3f, s.y + 8f, paint)
                                }
                            }
                        }
                    }
                    // Summary chip overlay.
                    Text(
                        "Контактов: ${g.nodes.size} · связей: ${g.edges.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun min2(a: Float, b: Float): Float = if (a < b) a else b

/**
 * Fruchterman–Reingold style force-directed layout: edges pull, all nodes repel.
 * Runs a fixed number of iterations; positions are centered around the origin.
 */
private fun computeLayout(graph: ContactGraph): Array<Offset> {
    val n = graph.nodes.size
    if (n == 0) return emptyArray()
    val area = 1_000_000f
    val k = sqrt(area / n)
    val x = FloatArray(n)
    val y = FloatArray(n)
    val ring = 400f
    for (i in 0 until n) {
        val a = 2.0 * Math.PI * i / n
        x[i] = (cos(a) * ring).toFloat()
        y[i] = (sin(a) * ring).toFloat()
    }
    val dx = FloatArray(n)
    val dy = FloatArray(n)
    val iterations = if (n > 250) 120 else 200
    var temp = ring
    repeat(iterations) {
        for (i in 0 until n) { dx[i] = 0f; dy[i] = 0f }
        // Repulsion between every pair.
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var ddx = x[i] - x[j]
                var ddy = y[i] - y[j]
                var dist = sqrt(ddx * ddx + ddy * ddy)
                if (dist < 0.01f) { ddx = 0.1f; ddy = 0.1f; dist = 0.15f }
                val force = k * k / dist
                val ux = ddx / dist; val uy = ddy / dist
                dx[i] += ux * force; dy[i] += uy * force
                dx[j] -= ux * force; dy[j] -= uy * force
            }
        }
        // Attraction along edges.
        for ((a, b) in graph.edges) {
            var ddx = x[a] - x[b]
            var ddy = y[a] - y[b]
            var dist = sqrt(ddx * ddx + ddy * ddy)
            if (dist < 0.01f) dist = 0.01f
            val force = dist * dist / k
            val ux = ddx / dist; val uy = ddy / dist
            dx[a] -= ux * force; dy[a] -= uy * force
            dx[b] += ux * force; dy[b] += uy * force
        }
        // Apply displacement, capped by the cooling temperature, with a gentle pull to center.
        for (i in 0 until n) {
            val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i])
            if (len > 0.01f) {
                val capped = if (len > temp) temp else len
                x[i] += dx[i] / len * capped
                y[i] += dy[i] / len * capped
            }
            x[i] *= 0.995f
            y[i] *= 0.995f
        }
        temp *= 0.97f
    }
    return Array(n) { Offset(x[it], y[it]) }
}
