package com.sonnik.telemetry.geo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One recorded coordinate of a live-location share. */
data class GeoPoint(
    val at: Long,
    val lat: Double,
    val lon: Double,
    val heading: Int,
    val accuracy: Double,
)

/** Aggregated stats over a recorded track. */
data class TrackStats(
    val points: Int,
    val distanceMeters: Double,
    val durationSec: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
)

/**
 * Local log of live-location points (framework SQLite, no extra deps). Every
 * coordinate TDLib pushes for an active share is appended here, so a moving
 * contact leaves a track that can be replayed and measured.
 */
class GeoStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "geo.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE live_points (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, " +
                "user_id INTEGER NOT NULL, at INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, " +
                "heading INTEGER NOT NULL, accuracy REAL NOT NULL)",
        )
        db.execSQL("CREATE INDEX idx_geo_msg_at ON live_points(chat_id, message_id, at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Appends a point only when it differs from the last one, to avoid duplicate spam. */
    fun record(chatId: Long, messageId: Long, userId: Long, point: GeoPoint): Boolean {
        readableDatabase.rawQuery(
            "SELECT lat, lon FROM live_points WHERE chat_id=? AND message_id=? ORDER BY at DESC LIMIT 1",
            arrayOf(chatId.toString(), messageId.toString()),
        ).use { c ->
            if (c.moveToNext() && c.getDouble(0) == point.lat && c.getDouble(1) == point.lon) return false
        }
        val values = ContentValues().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("user_id", userId)
            put("at", point.at)
            put("lat", point.lat)
            put("lon", point.lon)
            put("heading", point.heading)
            put("accuracy", point.accuracy)
        }
        writableDatabase.insert("live_points", null, values)
        return true
    }

    fun track(chatId: Long, messageId: Long): List<GeoPoint> {
        val points = ArrayList<GeoPoint>()
        readableDatabase.rawQuery(
            "SELECT at, lat, lon, heading, accuracy FROM live_points WHERE chat_id=? AND message_id=? ORDER BY at ASC",
            arrayOf(chatId.toString(), messageId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                points += GeoPoint(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getInt(3), c.getDouble(4))
            }
        }
        return points
    }

    fun stats(track: List<GeoPoint>): TrackStats {
        if (track.size < 2) {
            return TrackStats(track.size, 0.0, 0, 0.0, 0.0)
        }
        var distance = 0.0
        var maxSpeed = 0.0
        for (i in 1 until track.size) {
            val d = haversine(track[i - 1].lat, track[i - 1].lon, track[i].lat, track[i].lon)
            distance += d
            val dt = (track[i].at - track[i - 1].at).coerceAtLeast(1)
            val speed = d / dt * 3.6 // m/s → km/h
            if (speed > maxSpeed) maxSpeed = speed
        }
        val duration = track.last().at - track.first().at
        val avg = if (duration > 0) distance / duration * 3.6 else 0.0
        return TrackStats(track.size, distance, duration, avg, maxSpeed)
    }

    fun clear(chatId: Long, messageId: Long) {
        writableDatabase.delete("live_points", "chat_id=? AND message_id=?", arrayOf(chatId.toString(), messageId.toString()))
    }

    companion object {
        /** Great-circle distance in metres. */
        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return r * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
