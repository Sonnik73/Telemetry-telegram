package com.sonnik.telemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.sonnik.telemetry.data.ScanCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val CLUSTER_COLORS = listOf(
    Color(0xFF1976D2),
    Color(0xFF388E3C),
    Color(0xFFD32F2F),
    Color(0xFFF57C00),
    Color(0xFF7B1FA2),
    Color(0xFF00796B),
    Color(0xFFC2185B),
    Color(0xFF5D4037),
    Color(0xFF455A64),
    Color(0xFFAFB42B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactGraphScreen(onBack: () -> Unit, onOpenDossier: (Long) -> Unit) {
    val app = TelemetryApp.instance
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var graph by remember { mutableStateOf<ContactGraph?>(null) }
    var layout by remember { mutableStateOf<Array<Offset>?>(null) }
    var clusters by remember { mutableStateOf<IntArray?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var cacheTime by remember { mutableLongStateOf(0L) }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var searchQuery by remember { mutableStateOf("") }
    var hideIsolated by remember { mutableStateOf(false) }

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
            val comp = withContext(Dispatchers.Default) { computeClusters(g) }
            graph = g
            layout = pos
            clusters = comp
            ScanCache.graph = g
            ScanCache.graphTime = System.currentTimeMillis()
            cacheTime = ScanCache.graphTime
            loading = false
        }
    }

    // Show cached graph immediately on entry; only auto-build if nothing is cached.
    LaunchedEffect(Unit) {
        val cached = ScanCache.graph
        if (cached != null) {
            val pos = withContext(Dispatchers.Default) { computeLayout(cached) }
            val comp = withContext(Dispatchers.Default) { computeClusters(cached) }
            graph = cached
            layout = pos
            clusters = comp
            cacheTime = ScanCache.graphTime
        } else {
            load()
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
                            "Кластеры раскрашены по цветам — видно, какие группы людей пересекаются.",
                            "Тап по точке — открывает досье контакта.",
                            "Поиск подсвечивает контакт на графе.",
                            "«Скрыть одиночек» — убирает контакты без связей.",
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
            val comp = clusters
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
                    val nodeDefaultColor = MaterialTheme.colorScheme.primary
                    val isolatedColor = MaterialTheme.colorScheme.outline
                    val highlightColor = Color(0xFFFFD600)

                    val searchLower = searchQuery.trim().lowercase()
                    val highlightIdx = if (searchLower.isNotEmpty()) {
                        g.nodes.indexOfFirst { it.name.lowercase().contains(searchLower) }
                    } else -1

                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Поиск") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = hideIsolated,
                                onClick = { hideIsolated = !hideIsolated },
                                label = { Text("одиночки") },
                            )
                        }
                        Text(
                            buildString {
                                append("Контактов: ${g.nodes.size} · связей: ${g.edges.size}")
                                val age = ScanCache.ageLabel(cacheTime)
                                if (age.isNotEmpty()) append(" · обновлено $age")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )

                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(g, pos) {
                                    detectTapGestures { tapOffset ->
                                        val cs = size
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
                                            (cs.width - 2 * margin) / spanX,
                                            (cs.height - 2 * margin) / spanY,
                                        )
                                        val cx = cs.width / 2f
                                        val cy = cs.height / 2f
                                        val midX = (minX + maxX) / 2f
                                        val midY = (minY + maxY) / 2f
                                        var bestIdx = -1
                                        var bestDist = Float.MAX_VALUE
                                        for (i in g.nodes.indices) {
                                            if (hideIsolated && g.nodes[i].degree == 0) continue
                                            val sx = cx + (pos[i].x - midX) * fit * scale + pan.x
                                            val sy = cy + (pos[i].y - midY) * fit * scale + pan.y
                                            val dx = tapOffset.x - sx
                                            val dy = tapOffset.y - sy
                                            val d = sqrt(dx * dx + dy * dy)
                                            if (d < bestDist) { bestDist = d; bestIdx = i }
                                        }
                                        val tapRadius = 40f * max(scale, 0.5f).coerceAtMost(2f)
                                        if (bestIdx >= 0 && bestDist < tapRadius) {
                                            onOpenDossier(g.nodes[bestIdx].userId)
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, panChange, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.02f, 500f)
                                        pan += panChange
                                    }
                                },
                        ) {
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
                                if (hideIsolated && (g.nodes[a].degree == 0 || g.nodes[b].degree == 0)) continue
                                drawLine(edgeColor, screen(pos[a]), screen(pos[b]), strokeWidth = 1.5f)
                            }
                            val paint = android.graphics.Paint().apply {
                                color = labelColor.toArgb()
                                textSize = 26f * max(scale, 0.6f).coerceAtMost(1.4f)
                                isAntiAlias = true
                            }
                            val highlightPaint = android.graphics.Paint().apply {
                                color = highlightColor.toArgb()
                                textSize = 30f * max(scale, 0.6f).coerceAtMost(1.6f)
                                isAntiAlias = true
                                isFakeBoldText = true
                            }
                            for (i in g.nodes.indices) {
                                val node = g.nodes[i]
                                if (hideIsolated && node.degree == 0) continue
                                val s = screen(pos[i])
                                val connected = node.degree > 0
                                val isHighlight = i == highlightIdx
                                val nodeColor = when {
                                    isHighlight -> highlightColor
                                    !connected -> isolatedColor
                                    comp != null -> CLUSTER_COLORS[comp[i] % CLUSTER_COLORS.size]
                                    else -> nodeDefaultColor
                                }
                                val radius = if (isHighlight) {
                                    (10f + node.degree.coerceAtMost(12)) * max(scale, 0.5f).coerceAtMost(1.6f)
                                } else {
                                    (5f + node.degree.coerceAtMost(12)) * max(scale, 0.5f).coerceAtMost(1.6f)
                                }
                                drawCircle(nodeColor, radius, s)
                                if ((connected || isHighlight) && scale > 0.5f) {
                                    drawIntoCanvas { c ->
                                        c.nativeCanvas.drawText(
                                            node.name,
                                            s.x + radius + 3f,
                                            s.y + 8f,
                                            if (isHighlight) highlightPaint else paint,
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

private fun min2(a: Float, b: Float): Float = if (a < b) a else b

private fun computeClusters(graph: ContactGraph): IntArray {
    val n = graph.nodes.size
    if (n == 0) return IntArray(0)
    val parent = IntArray(n) { it }
    val rank = IntArray(n)
    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (c != r) { val next = parent[c]; parent[c] = r; c = next }
        return r
    }
    fun union(a: Int, b: Int) {
        val ra = find(a); val rb = find(b)
        if (ra == rb) return
        if (rank[ra] < rank[rb]) parent[ra] = rb
        else if (rank[ra] > rank[rb]) parent[rb] = ra
        else { parent[rb] = ra; rank[ra]++ }
    }
    for ((a, b) in graph.edges) union(a, b)
    val compId = HashMap<Int, Int>()
    var nextId = 0
    return IntArray(n) {
        val root = find(it)
        compId.getOrPut(root) { nextId++ }
    }
}

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
