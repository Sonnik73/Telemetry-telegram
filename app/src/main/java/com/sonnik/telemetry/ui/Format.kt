package com.sonnik.telemetry.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formats a byte count as B / KB / MB / GB / TB with one decimal. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ", "ПБ")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatCount(count: Int): String = String.format(Locale.US, "%,d", count).replace(',', ' ')

fun formatDate(unixSeconds: Int): String {
    if (unixSeconds <= 0) return "—"
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(unixSeconds * 1000L))
}

fun formatDateTime(unixSeconds: Int): String {
    if (unixSeconds <= 0) return "—"
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(unixSeconds * 1000L))
}

/** Human duration in hours/minutes, e.g. "3 ч 07 м" or "12 м". */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0 м"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) String.format(Locale.US, "%d ч %02d м", h, m) else "$m м"
}
