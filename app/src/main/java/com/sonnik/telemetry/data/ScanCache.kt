package com.sonnik.telemetry.data

object ScanCache {

    @Volatile var graph: ContactGraph? = null
    @Volatile var graphTime: Long = 0L

    @Volatile var lastSeen: List<LastSeenEntry>? = null
    @Volatile var lastSeenTime: Long = 0L

    data class PrivateStatsCache(val counts: List<Pair<ChatSummary, Int?>>, val partial: Boolean)

    @Volatile var privateStats: PrivateStatsCache? = null
    @Volatile var privateStatsTime: Long = 0L

    fun ageLabel(timeMs: Long): String {
        if (timeMs == 0L) return ""
        val ago = (System.currentTimeMillis() - timeMs) / 1000
        return when {
            ago < 60 -> "только что"
            ago < 3600 -> "${ago / 60} мин назад"
            ago < 86400 -> "${ago / 3600} ч назад"
            else -> "${ago / 86400} дн назад"
        }
    }
}
