package com.sonnik.telemetry.presence

import java.util.Calendar

/** Aggregated view of when a contact tends to be online. */
data class PresenceProfile(
    val totalSeconds: Long,
    val sessionCount: Int,
    /** Seconds online per hour of day (24 entries). */
    val byHour: LongArray,
) {
    /** The hours the contact is most often online, best first. */
    fun bestHours(count: Int = 3): List<Int> =
        byHour.indices.sortedByDescending { byHour[it] }.filter { byHour[it] > 0 }.take(count)
}

/** One window where two contacts were online at the same time. */
data class Overlap(val startSec: Long, val endSec: Long) {
    val durationSec: Long get() = (endSec - startSec).coerceAtLeast(0)
}

object PresenceAnalysis {

    /** Spreads each session across the hours of the day it covers. */
    fun profile(sessions: List<OnlineSession>): PresenceProfile {
        val byHour = LongArray(24)
        var total = 0L
        for (session in sessions) {
            var cursor = session.startSec
            while (cursor < session.endSec) {
                val cal = Calendar.getInstance().apply { timeInMillis = cursor * 1000 }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                // End of the current hour, or of the session — whichever comes first.
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val hourEnd = cal.timeInMillis / 1000 + 3600
                val slice = minOf(hourEnd, session.endSec) - cursor
                byHour[hour] += slice
                total += slice
                cursor += slice.coerceAtLeast(1)
            }
        }
        return PresenceProfile(total, sessions.size, byHour)
    }

    /**
     * Windows where both contacts were online simultaneously — the intersection of
     * their session lists. Both lists are assumed sorted by start time.
     */
    fun overlaps(a: List<OnlineSession>, b: List<OnlineSession>): List<Overlap> {
        val result = ArrayList<Overlap>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            val start = maxOf(a[i].startSec, b[j].startSec)
            val end = minOf(a[i].endSec, b[j].endSec)
            if (end > start) result += Overlap(start, end)
            if (a[i].endSec < b[j].endSec) i++ else j++
        }
        return result
    }
}
