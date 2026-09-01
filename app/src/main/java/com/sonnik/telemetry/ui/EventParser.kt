package com.sonnik.telemetry.ui

import java.util.Calendar

/** A date/time reference extracted from a message. epoch is in seconds. */
data class ParsedWhen(val epoch: Long, val hasTime: Boolean)

private val TIME = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
private val NUMERIC_DATE = Regex("""\b(\d{1,2})[.\-/](\d{1,2})(?:[.\-/](\d{2,4}))?\b""")
private val MONTH_DATE = Regex(
    """\b(\d{1,2})\s+(янв|фев|мар|апр|мая|май|июн|июл|авг|сен|окт|ноя|дек)[а-я]*""",
)
private val MONTH_STEMS = listOf(
    "янв", "фев", "мар", "апр", "ма", "июн", "июл", "авг", "сен", "окт", "ноя", "дек",
)

/**
 * Best-effort extraction of a future date/time mentioned in [text] (Russian).
 * Recognizes «сегодня/завтра/послезавтра», «5 января», and dd.mm(.yyyy); a HH:MM
 * time is attached when present. Returns null when nothing convincing is found.
 */
fun parseWhen(text: String, nowMillis: Long): ParsedWhen? {
    val lower = text.lowercase()
    val time = TIME.find(lower)
    val hour = time?.groupValues?.get(1)?.toIntOrNull()
    val minute = time?.groupValues?.get(2)?.toIntOrNull()
    val hasTime = hour != null && minute != null

    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMillis
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    var day = -1
    var month = -1 // 0-based
    var year = -1
    var relativeOffset = -1

    when {
        lower.contains("послезавтра") -> relativeOffset = 2
        lower.contains("завтра") -> relativeOffset = 1
        lower.contains("сегодня") -> relativeOffset = 0
    }

    if (relativeOffset < 0) {
        val mm = MONTH_DATE.find(lower)
        if (mm != null) {
            day = mm.groupValues[1].toIntOrNull() ?: -1
            val stem = mm.groupValues[2]
            month = monthFromStem(stem)
        } else {
            val nd = NUMERIC_DATE.find(lower)
            if (nd != null) {
                val d = nd.groupValues[1].toIntOrNull() ?: -1
                val m = nd.groupValues[2].toIntOrNull() ?: -1
                val yRaw = nd.groupValues[3]
                // A bare dd.mm is too noisy (version numbers etc.) unless a year or a
                // time is also present.
                if (d in 1..31 && m in 1..12 && (yRaw.isNotEmpty() || hasTime)) {
                    day = d
                    month = m - 1
                    if (yRaw.isNotEmpty()) {
                        year = yRaw.toInt().let { if (it < 100) 2000 + it else it }
                    }
                }
            }
        }
    }

    when {
        relativeOffset >= 0 -> cal.add(Calendar.DAY_OF_YEAR, relativeOffset)
        day in 1..31 && month in 0..11 -> {
            if (year > 0) cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
        }
        else -> return null
    }

    if (hasTime) {
        cal.set(Calendar.HOUR_OF_DAY, hour!!)
        cal.set(Calendar.MINUTE, minute!!)
    } else {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
    }

    // If an explicit date (no year given) already passed, assume next year.
    if (relativeOffset < 0 && year <= 0 && cal.timeInMillis < nowMillis - 2L * 24 * 3600 * 1000) {
        cal.add(Calendar.YEAR, 1)
    }

    return ParsedWhen(cal.timeInMillis / 1000, hasTime)
}

private fun monthFromStem(stem: String): Int {
    // "мая"/"май" both map to May (index 4); match the longest stem first.
    MONTH_STEMS.forEachIndexed { index, s ->
        if (stem.startsWith(s)) return index
    }
    return -1
}
