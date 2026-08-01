package com.sonnik.telemetry.geo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TILE = 256.0

/**
 * A minimal slippy-map rendered from OpenStreetMap raster tiles, drawn on a
 * Compose Canvas — no map SDK, only HttpURLConnection + BitmapFactory. Centered
 * on [centerLat]/[centerLon]; draws the recorded [track] as a polyline and the
 * latest position as a heading marker with an accuracy ring.
 */
@Composable
fun OsmTileMap(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    track: List<GeoPoint>,
    headingDeg: Int,
    accuracyMeters: Double,
    modifier: Modifier = Modifier,
) {
    val tiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Fetch whatever tiles the current viewport needs; cache is process-global.
    // Re-runs whenever the viewport, zoom or centre changes.
    LaunchedEffect(size, zoom, centerLat, centerLon) {
        val sz = size
        if (sz.width == 0 || sz.height == 0) return@LaunchedEffect
        val z = zoom
        val world = TILE * (1 shl z)
        val cx = (centerLon + 180.0) / 360.0 * world
        val cy = latToWorldY(centerLat, world)
        val leftX = cx - sz.width / 2.0
        val topY = cy - sz.height / 2.0
        val firstX = floor(leftX / TILE).toInt()
        val lastX = floor((leftX + sz.width) / TILE).toInt()
        val firstY = floor(topY / TILE).toInt().coerceAtLeast(0)
        val lastY = floor((topY + sz.height) / TILE).toInt().coerceAtMost((1 shl z) - 1)
        val n = 1 shl z
        for (tx in firstX..lastX) {
            for (ty in firstY..lastY) {
                val wrappedX = ((tx % n) + n) % n
                val key = "$z/$wrappedX/$ty"
                if (tiles.containsKey(key) || !TileCache.pending.add(key)) continue
                launch {
                    val bmp = TileCache.get(key) ?: downloadTile(z, wrappedX, ty)?.also { TileCache.put(key, it) }
                    TileCache.pending.remove(key)
                    if (bmp != null) tiles[key] = bmp
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it },
    ) {
        val z = zoom
        val world = TILE * (1 shl z)
        val cx = (centerLon + 180.0) / 360.0 * world
        val cy = latToWorldY(centerLat, world)
        val leftX = cx - this.size.width / 2.0
        val topY = cy - this.size.height / 2.0

        fun screen(lat: Double, lon: Double): Offset {
            val wx = (lon + 180.0) / 360.0 * world
            val wy = latToWorldY(lat, world)
            return Offset((wx - leftX).toFloat(), (wy - topY).toFloat())
        }

        // Tiles
        val firstX = floor(leftX / TILE).toInt()
        val lastX = floor((leftX + this.size.width) / TILE).toInt()
        val firstY = floor(topY / TILE).toInt()
        val lastY = floor((topY + this.size.height) / TILE).toInt()
        val n = 1 shl z
        for (tx in firstX..lastX) {
            for (ty in firstY..lastY) {
                if (ty < 0 || ty >= n) continue
                val wrappedX = ((tx % n) + n) % n
                val bmp = tiles["$z/$wrappedX/$ty"] ?: continue
                val sx = (tx * TILE - leftX).toFloat()
                val sy = (ty * TILE - topY).toFloat()
                drawImage(bmp, topLeft = Offset(sx, sy))
            }
        }

        // Track polyline
        if (track.size >= 2) {
            val path = Path()
            track.forEachIndexed { i, p ->
                val o = screen(p.lat, p.lon)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, color = Color(0xFF1877CC), style = Stroke(width = 5f))
        }

        // Accuracy ring + marker at center
        val marker = screen(centerLat, centerLon)
        val metersPerPixel = 156543.03392 * cos(Math.toRadians(centerLat)) / (1 shl z)
        if (accuracyMeters > 0 && metersPerPixel > 0) {
            val r = (accuracyMeters / metersPerPixel).toFloat()
            if (r in 1f..2000f) {
                drawCircle(Color(0x331877CC), radius = r, center = marker)
                drawCircle(Color(0x551877CC), radius = r, center = marker, style = Stroke(width = 2f))
            }
        }
        // Heading arrow (0° = north/up)
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
}

private fun latToWorldY(lat: Double, world: Double): Double {
    val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world
}

private suspend fun downloadTile(z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        // OSM tile policy requires a descriptive User-Agent.
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
