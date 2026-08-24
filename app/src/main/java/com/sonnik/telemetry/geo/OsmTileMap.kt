package com.sonnik.telemetry.geo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TILE = 256.0

/**
 * A minimal slippy-map from OpenStreetMap raster tiles on a Compose Canvas — no
 * map SDK. Follows [targetLat]/[targetLon] (the live position) until the user
 * pans or pinches, after which it holds the manual camera; the location button
 * re-enables following. Draws [track] as a polyline and the target as a heading
 * marker with an accuracy ring.
 */
@Composable
fun OsmTileMap(
    targetLat: Double,
    targetLon: Double,
    initialZoom: Int,
    track: List<GeoPoint>,
    headingDeg: Int,
    accuracyMeters: Double,
    modifier: Modifier = Modifier,
) {
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    var following by remember { mutableStateOf(true) }
    var camLat by remember { mutableStateOf(targetLat) }
    var camLon by remember { mutableStateOf(targetLon) }
    var zoomF by remember { mutableFloatStateOf(initialZoom.toFloat()) }
    val zoom = zoomF.roundToInt().coerceIn(3, 19)

    // While following, the camera tracks the live position.
    LaunchedEffect(targetLat, targetLon, following) {
        if (following) {
            camLat = targetLat
            camLon = targetLon
        }
    }

    val effLat = if (following) targetLat else camLat
    val effLon = if (following) targetLon else camLon

    // Prefetch tiles for the current viewport.
    LaunchedEffect(size, zoom, effLat, effLon) {
        val sz = size
        if (sz.width == 0 || sz.height == 0) return@LaunchedEffect
        val world = TILE * (1 shl zoom)
        val leftX = (effLon + 180.0) / 360.0 * world - sz.width / 2.0
        val topY = latToWorldY(effLat, world) - sz.height / 2.0
        val n = 1 shl zoom
        for (tx in floor(leftX / TILE).toInt()..floor((leftX + sz.width) / TILE).toInt()) {
            for (ty in floor(topY / TILE).toInt().coerceAtLeast(0)..floor((topY + sz.height) / TILE).toInt().coerceAtMost(n - 1)) {
                val wrappedX = ((tx % n) + n) % n
                val key = "$zoom/$wrappedX/$ty"
                if (tiles.containsKey(key) || !TileCache.pending.add(key)) continue
                launch {
                    val bmp = TileCache.get(key) ?: downloadTile(zoom, wrappedX, ty)?.also { TileCache.put(key, it) }
                    TileCache.pending.remove(key)
                    if (bmp != null) tiles[key] = bmp
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        following = false
                        val world = TILE * (1 shl zoomF.roundToInt().coerceIn(3, 19))
                        // Pan: pixels → geo. Dragging moves the map with the finger.
                        camLon = (if (following) targetLon else camLon) - pan.x / world * 360.0
                        val curLat = if (following) targetLat else camLat
                        val newWorldY = latToWorldY(curLat, world) - pan.y
                        camLat = worldYToLat(newWorldY, world)
                        camLon = camLon.coerceIn(-179.9, 179.9)
                        // Pinch zoom.
                        if (gestureZoom != 1f) {
                            zoomF = (zoomF + log2(gestureZoom)).coerceIn(3f, 19f)
                        }
                    }
                },
        ) {
            val z = zoom
            val world = TILE * (1 shl z)
            val leftX = (effLon + 180.0) / 360.0 * world - this.size.width / 2.0
            val topY = latToWorldY(effLat, world) - this.size.height / 2.0

            fun screen(lat: Double, lon: Double): Offset {
                val wx = (lon + 180.0) / 360.0 * world
                val wy = latToWorldY(lat, world)
                return Offset((wx - leftX).toFloat(), (wy - topY).toFloat())
            }

            val n = 1 shl z
            for (tx in floor(leftX / TILE).toInt()..floor((leftX + this.size.width) / TILE).toInt()) {
                for (ty in floor(topY / TILE).toInt()..floor((topY + this.size.height) / TILE).toInt()) {
                    if (ty < 0 || ty >= n) continue
                    val wrappedX = ((tx % n) + n) % n
                    val bmp = tiles["$z/$wrappedX/$ty"] ?: continue
                    drawImage(bmp, topLeft = Offset((tx * TILE - leftX).toFloat(), (ty * TILE - topY).toFloat()))
                }
            }

            if (track.size >= 2) {
                val path = Path()
                track.forEachIndexed { i, p ->
                    val o = screen(p.lat, p.lon)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, color = Color(0xFF1877CC), style = Stroke(width = 5f))
            }

            val marker = screen(targetLat, targetLon)
            val metersPerPixel = 156543.03392 * cos(Math.toRadians(targetLat)) / (1 shl z)
            if (accuracyMeters > 0 && metersPerPixel > 0) {
                val r = (accuracyMeters / metersPerPixel).toFloat()
                if (r in 1f..2000f) {
                    drawCircle(Color(0x331877CC), radius = r, center = marker)
                    drawCircle(Color(0x551877CC), radius = r, center = marker, style = Stroke(width = 2f))
                }
            }
            rotate(degrees = headingDeg.toFloat(), pivot = marker) {
                val arrow = Path().apply {
                    moveTo(marker.x, marker.y - 22f)
                    lineTo(marker.x - 11f, marker.y + 12f)
                    lineTo(marker.x, marker.y + 4f)
                    lineTo(marker.x + 11f, marker.y + 12f)
                    close()
                }
                drawPath(arrow, color = Color(0xFFD32F2F))
            }
            drawCircle(Color.White, radius = 7f, center = marker)
            drawCircle(Color(0xFFD32F2F), radius = 7f, center = marker, style = Stroke(width = 3f))
        }

        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilledTonalIconButton(onClick = { zoomF = (zoomF + 1f).coerceAtMost(19f) }) {
                Icon(Icons.Default.Add, contentDescription = "Приблизить")
            }
            FilledTonalIconButton(onClick = { zoomF = (zoomF - 1f).coerceAtLeast(3f) }) {
                Icon(Icons.Default.Remove, contentDescription = "Отдалить")
            }
            FilledTonalIconButton(onClick = { following = true }) {
                Icon(Icons.Default.MyLocation, contentDescription = "К точке")
            }
        }
    }
}

private fun latToWorldY(lat: Double, world: Double): Double {
    val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world
}

private fun worldYToLat(y: Double, world: Double): Double {
    val nY = PI * (1.0 - 2.0 * (y / world))
    return Math.toDegrees(atan(sinh(nY))).coerceIn(-85.05112878, 85.05112878)
}

private suspend fun downloadTile(z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "TelemetryApp/1.0 (Telegram stats; personal use)")
        conn.inputStream.use { input ->
            BitmapFactory.decodeStream(input)?.asImageBitmap()
        }
    }.getOrNull()
}

/** Process-wide LRU tile cache shared across map instances. */
private object TileCache {
    private const val MAX = 256
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>?): Boolean = size > MAX
    }
    val pending = java.util.Collections.synchronizedSet(HashSet<String>())

    @Synchronized fun get(key: String): ImageBitmap? = cache[key]

    @Synchronized fun put(key: String, bmp: ImageBitmap) {
        cache[key] = bmp
    }
}
