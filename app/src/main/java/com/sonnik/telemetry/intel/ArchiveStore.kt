package com.sonnik.telemetry.intel

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A caught deletion or edit of a message. */
data class ArchiveEvent(
    val kind: String, // "deleted" | "edited"
    val chatId: Long,
    val messageId: Long,
    val senderId: Long,
    val at: Long,
    val oldBody: String,
    val newBody: String,
)

/** A new message that matched one of the tracked keywords. */
data class KeywordHit(
    val chatId: Long,
    val messageId: Long,
    val senderId: Long,
    val at: Long,
    val keyword: String,
    val body: String,
)

/** A caught "typing / recording / uploading" action by a contact in some chat. */
data class TypingEvent(
    val chatId: Long,
    val senderId: Long,
    val action: String,
    val at: Long,
)

/** A self-destructing media message that was captured (copied) before it vanished. */
data class CapturedMedia(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val at: Long,
    val type: String, // "photo" | "video" | "voice" | "videonote" | "gif"
    val path: String,
    val caption: String,
)

/** A recently cached message row (for local event/date extraction). */
data class CachedRow(val chatId: Long, val senderId: Long, val date: Int, val body: String)

/** A recorded change of a contact's profile field. */
data class ContactChange(
    val userId: Long,
    val at: Long,
    val field: String,
    val oldValue: String,
    val newValue: String,
)

/**
 * Local intelligence store: a rolling cache of recently seen messages plus an
 * append-only log of deletions, edits and contact profile changes. The cache is
 * what makes catching deletes possible — when TDLib reports a message gone, its
 * last known content is still here.
 */
class ArchiveStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "archive.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE cached (chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, " +
                "sender_id INTEGER NOT NULL, date INTEGER NOT NULL, body TEXT NOT NULL, " +
                "PRIMARY KEY(chat_id, message_id))",
        )
        db.execSQL(
            "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, " +
                "chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, sender_id INTEGER NOT NULL, " +
                "at INTEGER NOT NULL, old_body TEXT NOT NULL, new_body TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX idx_events_at ON events(at)")
        db.execSQL(
            "CREATE TABLE contact_changes (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, " +
                "at INTEGER NOT NULL, field TEXT NOT NULL, old_value TEXT NOT NULL, new_value TEXT NOT NULL)",
        )
        db.execSQL("CREATE TABLE contact_snapshot (user_id INTEGER PRIMARY KEY, name TEXT, username TEXT, photo_id INTEGER)")
        db.execSQL("CREATE INDEX idx_changes_user ON contact_changes(user_id, at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun cache(chatId: Long, messageId: Long, senderId: Long, date: Int, body: String) {
        val values = ContentValues().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("sender_id", senderId)
            put("date", date)
            put("body", body)
        }
        writableDatabase.insertWithOnConflict("cached", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        pruneCache()
    }

    fun cachedBody(chatId: Long, messageId: Long): Pair<Long, String>? {
        readableDatabase.rawQuery(
            "SELECT sender_id, body FROM cached WHERE chat_id=? AND message_id=?",
            arrayOf(chatId.toString(), messageId.toString()),
        ).use { c -> return if (c.moveToNext()) c.getLong(0) to c.getString(1) else null }
    }

    fun recordEvent(event: ArchiveEvent) {
        val values = ContentValues().apply {
            put("kind", event.kind)
            put("chat_id", event.chatId)
            put("message_id", event.messageId)
            put("sender_id", event.senderId)
            put("at", event.at)
            put("old_body", event.oldBody)
            put("new_body", event.newBody)
        }
        writableDatabase.insert("events", null, values)
    }

    /** Lazily creates the keyword-hits table (works on already-created databases too). */
    private fun ensureKeywordTable() {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS keyword_hits (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL, sender_id INTEGER NOT NULL, " +
                "at INTEGER NOT NULL, keyword TEXT NOT NULL, body TEXT NOT NULL)",
        )
    }

    fun recordKeywordHit(hit: KeywordHit) {
        ensureKeywordTable()
        val values = ContentValues().apply {
            put("chat_id", hit.chatId)
            put("message_id", hit.messageId)
            put("sender_id", hit.senderId)
            put("at", hit.at)
            put("keyword", hit.keyword)
            put("body", hit.body)
        }
        writableDatabase.insert("keyword_hits", null, values)
    }

    /** Lazily creates the typing-events table. */
    private fun ensureTypingTable() {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS typing_events (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "chat_id INTEGER NOT NULL, sender_id INTEGER NOT NULL, action TEXT NOT NULL, at INTEGER NOT NULL)",
        )
    }

    fun recordTyping(e: TypingEvent) {
        ensureTypingTable()
        val values = ContentValues().apply {
            put("chat_id", e.chatId)
            put("sender_id", e.senderId)
            put("action", e.action)
            put("at", e.at)
        }
        writableDatabase.insert("typing_events", null, values)
        writableDatabase.execSQL(
            "DELETE FROM typing_events WHERE id NOT IN (SELECT id FROM typing_events ORDER BY at DESC LIMIT $TYPING_LIMIT)",
        )
    }

    fun typingEvents(limit: Int): List<TypingEvent> {
        ensureTypingTable()
        val result = ArrayList<TypingEvent>()
        readableDatabase.rawQuery(
            "SELECT chat_id, sender_id, action, at FROM typing_events ORDER BY at DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += TypingEvent(c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3))
            }
        }
        return result
    }

    fun keywordHits(limit: Int): List<KeywordHit> {
        ensureKeywordTable()
        val result = ArrayList<KeywordHit>()
        readableDatabase.rawQuery(
            "SELECT chat_id, message_id, sender_id, at, keyword, body FROM keyword_hits ORDER BY at DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += KeywordHit(c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getString(4), c.getString(5))
            }
        }
        return result
    }

    fun events(limit: Int, userId: Long? = null): List<ArchiveEvent> {
        val where = if (userId != null) "WHERE sender_id=$userId" else ""
        val result = ArrayList<ArchiveEvent>()
        readableDatabase.rawQuery(
            "SELECT kind, chat_id, message_id, sender_id, at, old_body, new_body FROM events $where ORDER BY at DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += ArchiveEvent(c.getString(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getString(5), c.getString(6))
            }
        }
        return result
    }

    /** Returns the contact fields that changed vs the stored snapshot, updating it. */
    fun diffContact(userId: Long, name: String, username: String, photoId: Long): List<ContactChange> {
        var prevName: String? = null
        var prevUser: String? = null
        var prevPhoto: Long? = null
        readableDatabase.rawQuery(
            "SELECT name, username, photo_id FROM contact_snapshot WHERE user_id=?",
            arrayOf(userId.toString()),
        ).use { c ->
            if (c.moveToNext()) {
                prevName = c.getString(0); prevUser = c.getString(1); prevPhoto = c.getLong(2)
            }
        }
        val now = System.currentTimeMillis() / 1000
        val changes = ArrayList<ContactChange>()
        if (prevName != null) { // don't log the very first snapshot as a change
            if (prevName != name) changes += ContactChange(userId, now, "имя", prevName!!, name)
            if (prevUser != username) changes += ContactChange(userId, now, "юзернейм", prevUser ?: "", username)
            if (prevPhoto != photoId) changes += ContactChange(userId, now, "фото профиля", "", "")
        }
        changes.forEach { ch ->
            val v = ContentValues().apply {
                put("user_id", ch.userId); put("at", ch.at); put("field", ch.field)
                put("old_value", ch.oldValue); put("new_value", ch.newValue)
            }
            writableDatabase.insert("contact_changes", null, v)
        }
        val snap = ContentValues().apply {
            put("user_id", userId); put("name", name); put("username", username); put("photo_id", photoId)
        }
        writableDatabase.insertWithOnConflict("contact_snapshot", null, snap, SQLiteDatabase.CONFLICT_REPLACE)
        return changes
    }

    fun contactChanges(userId: Long, limit: Int): List<ContactChange> {
        val result = ArrayList<ContactChange>()
        readableDatabase.rawQuery(
            "SELECT user_id, at, field, old_value, new_value FROM contact_changes WHERE user_id=? ORDER BY at DESC LIMIT ?",
            arrayOf(userId.toString(), limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                result += ContactChange(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4))
            }
        }
        return result
    }

    /** Most recent cached messages, newest first — used for local date/event extraction. */
    fun recentCached(limit: Int): List<CachedRow> {
        val result = ArrayList<CachedRow>()
        readableDatabase.rawQuery(
            "SELECT chat_id, sender_id, date, body FROM cached ORDER BY date DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += CachedRow(c.getLong(0), c.getLong(1), c.getInt(2), c.getString(3))
            }
        }
        return result
    }

    private fun ensureCapturedTable() {
        writableDatabase.execSQL(
            "CREATE TABLE IF NOT EXISTS captured_media (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "chat_id INTEGER NOT NULL, sender_id INTEGER NOT NULL, at INTEGER NOT NULL, " +
                "type TEXT NOT NULL, path TEXT NOT NULL, caption TEXT NOT NULL)",
        )
    }

    fun recordCaptured(chatId: Long, senderId: Long, at: Long, type: String, path: String, caption: String) {
        ensureCapturedTable()
        val values = ContentValues().apply {
            put("chat_id", chatId)
            put("sender_id", senderId)
            put("at", at)
            put("type", type)
            put("path", path)
            put("caption", caption)
        }
        writableDatabase.insert("captured_media", null, values)
    }

    fun capturedMedia(limit: Int): List<CapturedMedia> {
        ensureCapturedTable()
        val result = ArrayList<CapturedMedia>()
        readableDatabase.rawQuery(
            "SELECT id, chat_id, sender_id, at, type, path, caption FROM captured_media ORDER BY at DESC LIMIT $limit",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                result += CapturedMedia(
                    c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3),
                    c.getString(4), c.getString(5), c.getString(6),
                )
            }
        }
        return result
    }

    /** Keeps the message cache bounded so it can't grow without limit. */
    private fun pruneCache() {
        writableDatabase.execSQL(
            "DELETE FROM cached WHERE rowid NOT IN (SELECT rowid FROM cached ORDER BY date DESC LIMIT $CACHE_LIMIT)",
        )
    }

    private companion object {
        const val CACHE_LIMIT = 5000
        const val TYPING_LIMIT = 3000
    }
}
