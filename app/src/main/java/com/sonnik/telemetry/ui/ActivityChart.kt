package com.sonnik.telemetry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private enum class Bucket { DAY, WEEK, MONTH }

private data class ChartBucket(val start: LocalDate, val count: Int)

/**
 * Single-series bar chart of message activity. Days are bucketed into weeks or
 * months when the range is too wide for per-day bars; tapping a bar shows its
 * exact value below the chart.
 */
@Composable
fun ActivityChart(perDay: Map<LocalDate, Int>, modifier: Modifier = Modifier) {
    if (perDay.isEmpty()) return

    val (buckets, bucketKind) = remember(perDay) { bucketize(perDay) }
    var selected by remember(perDay) { mutableStateOf<Int?>(null) }

    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.onSurface

    val dayFormat = DateTimeFormatter.ofPattern("dd.MM.yy")
    val monthFormat = DateTimeFormatter.ofPattern("MM.yyyy")
    val maxCount = buckets.maxOf { it.count }

    Column(modifier = modifier) {
        Text(
            "макс. ${formatCount(maxCount)} " + when (bucketKind) {
                Bucket.DAY -> "в день"
                Bucket.WEEK -> "в неделю"
                Bucket.MONTH -> "в месяц"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(vertical = 4.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        val index = ((offset.x / size.width) * buckets.size).toInt()
                        selected = index.coerceIn(0, buckets.size - 1)
                    }
                },
        ) {
            val n = buckets.size
            val gap = if (size.width / n >= 4f) 2.dp.toPx() else 0f
            val barWidth = (size.width - gap * (n - 1)) / n
            val radius = minOf(4.dp.toPx(), barWidth / 2f)
            val chartHeight = size.height - 1.dp.toPx()

            buckets.forEachIndexed { index, bucket ->
                if (bucket.count == 0) return@forEachIndexed
                val barHeight = (bucket.count.toFloat() / maxCount) * chartHeight
                val left = index * (barWidth + gap)
                val top = chartHeight - barHeight
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, left + barWidth, chartHeight),
                            topLeft = CornerRadius(radius),
                            topRight = CornerRadius(radius),
                        ),
                    )
                }
                drawPath(path, color = barColor)
                if (selected == index) {
                    drawPath(path, color = selectionColor, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
            drawLine(
                color = axisColor,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            val format = if (bucketKind == Bucket.MONTH) monthFormat else dayFormat
            Text(
                buckets.first().start.format(format),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                buckets.last().start.format(format),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        selected?.let { index ->
            val bucket = buckets[index]
            val label = when (bucketKind) {
                Bucket.DAY -> bucket.start.format(dayFormat)
                Bucket.WEEK -> "неделя с ${bucket.start.format(dayFormat)}"
                Bucket.MONTH -> bucket.start.format(monthFormat)
            }
            Text(
                "$label — ${formatCount(bucket.count)} сообщ.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun bucketize(perDay: Map<LocalDate, Int>): Pair<List<ChartBucket>, Bucket> {
    val first = perDay.keys.min()
    val last = perDay.keys.max()
    val spanDays = ChronoUnit.DAYS.between(first, last) + 1
    val kind = when {
        spanDays <= 62 -> Bucket.DAY
        spanDays <= 550 -> Bucket.WEEK
        else -> Bucket.MONTH
    }

    fun bucketStart(date: LocalDate): LocalDate = when (kind) {
        Bucket.DAY -> date
        Bucket.WEEK -> date.minusDays((date.dayOfWeek.value - 1).toLong())
        Bucket.MONTH -> date.withDayOfMonth(1)
    }

    fun next(date: LocalDate): LocalDate = when (kind) {
        Bucket.DAY -> date.plusDays(1)
        Bucket.WEEK -> date.plusWeeks(1)
        Bucket.MONTH -> date.plusMonths(1)
    }

    val sums = HashMap<LocalDate, Int>()
    for ((day, count) in perDay) {
        sums.merge(bucketStart(day), count, Int::plus)
    }

    val buckets = ArrayList<ChartBucket>()
    var cursor = bucketStart(first)
    val lastStart = bucketStart(last)
    while (!cursor.isAfter(lastStart)) {
        buckets += ChartBucket(cursor, sums[cursor] ?: 0)
        cursor = next(cursor)
    }
    return buckets to kind
}
