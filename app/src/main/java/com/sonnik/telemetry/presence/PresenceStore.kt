package com.sonnik.telemetry.presence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A person being watched, with their last observed status snapshot. */
data class WatchedUser(
    val userId: Long,
    val title: String,
    val online: Boolean,
    val lastSeen: Int,
    val lastChangeAt: Long,
)

/** One online→offline interval reconstructed from the event log. */
data class OnlineSession(val startSec: Long, val endSec: Long) {
    val durationSec: Long get() = (endSec - startSec).coerceAtLeast(0)
}

/**
 * Local presence log backed by the framework SQLite (no extra dependencies).
 * Stores the set of watched users and an append-only stream of status events,
 * from which sessions and activity stats are derived.
 */
class PresenceStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "presence.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE watched (" +
                "user_id INTEGER PRIMARY KEY, title TEXT NOT NULL, added_at INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE presence (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, " +
                "at INTEGER NOT NULL, online INTEGER NOT NULL, was_online INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX idx_presence_user_at ON presence(user_id, at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 only; nothing to migrate yet.
    }

    fun addWatched(userId: Long, title: String) {
        val values = ContentValues().apply {
            put("user_id", userId)
            put("title", title)
            put("added_at", System.currentTimeMillis() / 1000)
        }
        writableDatabase.insertWithOnConflict("watched", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeWatched(userId: Long) {
        writableDatabase.delete("watched", "user_id = ?", arrayOf(userId.toString()))
        writableDatabase.delete("presence", "user_id = ?", arrayOf(userId.toString()))
    }

    fun watchedIds(): Set<Long> {
        val ids = HashSet<Long>()
        readableDatabase.rawQuery("SELECT user_id FROM watched", null).use { c ->
            while (c.moveToNext()) ids += c.getLong(0)
        }
        return ids
    }

    fun watchedTitle(userId: Long): String? {
        readableDatabase.rawQuery("SELECT title FROM watched WHERE user_id = ?", arrayOf(userId.toString())).use { c ->
            return if (c.moveToNext()) c.getString(0) else null
        }
    }

    /** All watched users with their most recent status, most recently changed first. */
    fun watchedUsers(): List<WatchedUser> {
        val result = ArrayList<WatchedUser>()
        readableDatabase.rawQuery("SELECT user_id, title FROM watched", null).use { c ->
            while (c.moveToNext()) {
                val userId = c.getLong(0)
                val title = c.getString(1)
                var online = false
                var lastSeen = 0
                var lastChangeAt = 0L
                readableDatabase.rawQuery(
                    "SELECT at, online, was_online FROM presence WHERE user_id = ? ORDER BY at DESC LIMIT 1",
                    arrayOf(userId.toString()),
                ).use { p ->
                    if (p.moveToNext()) {
                        lastChangeAt = p.getLong(0)
                        online = p.getInt(1) != 0
                        lastSeen = p.getInt(2)
                    }
                }
                result += WatchedUser(userId, title, online, lastSeen, lastChangeAt)
            }
        }
        return result.sortedByDescending { it.lastChangeAt }
    }

    /** Records a status change only if it differs from the last recorded state. */
    fun recordIfChanged(userId: Long, online: Boolean, wasOnline: Int): Boolean {
        var prevOnline: Boolean? = null
        readableDatabase.rawQuery(
            "SELECT online FROM presence WHERE user_id = ? ORDER BY at DESC LIMIT 1",
            arrayOf(userId.toString()),
        ).use { c ->
            if (c.moveToNext()) prevOnline = c.getInt(0) != 0
        }
        if (prevOnline == online) return false
        val values = ContentValues().apply {
            put("user_id", userId)
            put("at", System.currentTimeMillis() / 1000)
            put("online", if (online) 1 else 0)
            put("was_online", wasOnline)
        }
        writableDatabase.insert("presence", null, values)
        return true
    }

    /** Reconstructs online sessions since [sinceSec] by pairing online/offline events. */
    fun sessions(userId: Long, sinceSec: Long): List<OnlineSession> {
        val sessions = ArrayList<OnlineSession>()
        var onlineStart: Long? = null
        readableDatabase.rawQuery(
            "SELECT at, online FROM presence WHERE user_id = ? AND at >= ? ORDER BY at ASC",
            arrayOf(userId.toString(), sinceSec.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val at = c.getLong(0)
                val online = c.getInt(1) != 0
                if (online) {
                    if (onlineStart == null) onlineStart = at
                } else {
                    val start = onlineStart
                    if (start != null) {
                        sessions += OnlineSession(start, at)
                        onlineStart = null
                    }
                }
            }
        }
        // An unterminated online run is counted up to now.
        onlineStart?.let { sessions += OnlineSession(it, System.currentTimeMillis() / 1000) }
        return sessions
    }
}
