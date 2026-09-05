package com.gintama.novabrowser.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gintama.novabrowser.core.model.BookmarkItem
import com.gintama.novabrowser.core.model.DownloadItem
import com.gintama.novabrowser.core.model.DownloadStatus
import com.gintama.novabrowser.core.model.HistoryItem
import com.gintama.novabrowser.core.model.TabSession

/**
 * SQLite OpenHelper implementing the complete NovaBrowser schema specified in DESIGN.md.
 * Provides thread-safe, local-first storage for History, Bookmarks, Sessions, Downloads, and Security rules.
 */
class NovaDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "nova_browser.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: NovaDatabaseHelper? = null

        fun getInstance(context: Context): NovaDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: NovaDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. History Table
        db.execSQL("""
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                title TEXT,
                domain TEXT NOT NULL,
                visited_at INTEGER NOT NULL,
                summary TEXT,
                embedding BLOB,
                extracted_text_meta TEXT
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_history_domain ON history(domain);")
        db.execSQL("CREATE INDEX idx_history_visited_at ON history(visited_at);")

        // Try creating FTS5 for history lexical search (fallback gracefully if FTS5 is not compiled into sqlite)
        try {
            db.execSQL("""
                CREATE VIRTUAL TABLE history_fts USING fts5(
                    title, url, summary, content='history', content_rowid='id'
                );
            """.trimIndent())
        } catch (e: Exception) {
            // Fallback for older/custom SQLite builds without FTS5 extension
        }

        // 2. Bookmarks Table
        db.execSQL("""
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                title TEXT,
                folder TEXT,
                created_at INTEGER NOT NULL
            );
        """.trimIndent())

        // 3. Sessions (Tabs) Table
        db.execSQL("""
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tab_id TEXT NOT NULL,
                url TEXT,
                title TEXT,
                is_private INTEGER DEFAULT 0,
                last_active_at INTEGER
            );
        """.trimIndent())

        // 4. Downloads Table
        db.execSQL("""
            CREATE TABLE downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                filename TEXT,
                mime_type TEXT,
                status TEXT CHECK(status IN ('pending','safe','quarantined','blocked','completed')),
                risk_reason TEXT,
                created_at INTEGER NOT NULL
            );
        """.trimIndent())

        // 5. Security Rules Table (Ready for Phase 2)
        db.execSQL("""
            CREATE TABLE security_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                rule_type TEXT CHECK(rule_type IN ('domain','url','tracker','malware')),
                pattern TEXT NOT NULL,
                source TEXT CHECK(source IN ('URLHAUS','EASYLIST','EASYPRIVACY','LOCAL_HEURISTIC')),
                severity TEXT CHECK(severity IN ('block','warn','info')),
                updated_at INTEGER NOT NULL
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_security_pattern ON security_rules(pattern);")

        // 6. Snapshot Meta Table
        db.execSQL("""
            CREATE TABLE snapshot_meta (
                feed_source TEXT PRIMARY KEY,
                last_updated_at INTEGER NOT NULL,
                rule_count INTEGER
            );
        """.trimIndent())

        // 7. AI Page Index Table (Nullable embeddings for low-memory tiers)
        db.execSQL("""
            CREATE TABLE ai_page_index (
                history_id INTEGER NOT NULL REFERENCES history(id) ON DELETE CASCADE,
                chunk_index INTEGER NOT NULL,
                chunk_text TEXT NOT NULL,
                chunk_embedding BLOB,
                PRIMARY KEY (history_id, chunk_index)
            );
        """.trimIndent())

        // 8. Ad-Block Site Rules Table (Per-site exceptions & cosmetic toggles)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS adblock_site_rules (
                domain TEXT PRIMARY KEY,
                adblock_enabled INTEGER NOT NULL DEFAULT 1,
                cosmetic_enabled INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            );
        """.trimIndent())
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS adblock_site_rules (
                domain TEXT PRIMARY KEY,
                adblock_enabled INTEGER NOT NULL DEFAULT 1,
                cosmetic_enabled INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL
            );
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For development / early phases:
        db.execSQL("DROP TABLE IF EXISTS adblock_site_rules")
        db.execSQL("DROP TABLE IF EXISTS ai_page_index")
        db.execSQL("DROP TABLE IF EXISTS snapshot_meta")
        db.execSQL("DROP TABLE IF EXISTS security_rules")
        db.execSQL("DROP TABLE IF EXISTS downloads")
        db.execSQL("DROP TABLE IF EXISTS sessions")
        db.execSQL("DROP TABLE IF EXISTS bookmarks")
        db.execSQL("DROP TABLE IF EXISTS history_fts")
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    // ==========================================
    // History DAO Operations
    // ==========================================

    fun recordHistory(url: String, title: String?, domain: String, summary: String? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("domain", domain)
            put("visited_at", System.currentTimeMillis())
            put("summary", summary)
        }
        val rowId = db.insert("history", null, values)

        // Keep FTS5 in sync if virtual table is available
        if (rowId != -1L) {
            try {
                db.execSQL(
                    "INSERT INTO history_fts(rowid, title, url, summary) VALUES(?, ?, ?, ?)",
                    arrayOf(rowId, title ?: "", url, summary ?: "")
                )
            } catch (ignored: Exception) {
            }
        }
        return rowId
    }

    fun getRecentHistory(limit: Int = 100): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, url, title, domain, visited_at, summary FROM history ORDER BY visited_at DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val urlCol = it.getColumnIndexOrThrow("url")
            val titleCol = it.getColumnIndexOrThrow("title")
            val domainCol = it.getColumnIndexOrThrow("domain")
            val visitedCol = it.getColumnIndexOrThrow("visited_at")
            val summaryCol = it.getColumnIndexOrThrow("summary")

            while (it.moveToNext()) {
                list.add(
                    HistoryItem(
                        id = it.getLong(idCol),
                        url = it.getString(urlCol),
                        title = it.getString(titleCol),
                        domain = it.getString(domainCol),
                        visitedAt = it.getLong(visitedCol),
                        summary = it.getString(summaryCol)
                    )
                )
            }
        }
        return list
    }

    fun searchHistory(query: String, limit: Int = 50): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = readableDatabase
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return getRecentHistory(limit)

        var usedFts = false
        // Primary path: SQLite FTS5 lexical match with BM25 ranking
        try {
            val ftsQuery = cleanQuery.split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .joinToString(" ") { "\"${it.replace("\"", "\"\"")}\"*" }

            val cursor = db.rawQuery(
                """
                SELECT h.id, h.url, h.title, h.domain, h.visited_at, h.summary
                FROM history h
                JOIN history_fts f ON h.id = f.rowid
                WHERE history_fts MATCH ?
                ORDER BY bm25(history_fts) ASC, h.visited_at DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(ftsQuery, limit.toString())
            )
            cursor.use {
                val idCol = it.getColumnIndexOrThrow("id")
                val urlCol = it.getColumnIndexOrThrow("url")
                val titleCol = it.getColumnIndexOrThrow("title")
                val domainCol = it.getColumnIndexOrThrow("domain")
                val visitedCol = it.getColumnIndexOrThrow("visited_at")
                val summaryCol = it.getColumnIndexOrThrow("summary")

                while (it.moveToNext()) {
                    list.add(
                        HistoryItem(
                            id = it.getLong(idCol),
                            url = it.getString(urlCol),
                            title = it.getString(titleCol),
                            domain = it.getString(domainCol),
                            visitedAt = it.getLong(visitedCol),
                            summary = it.getString(summaryCol)
                        )
                    )
                }
            }
            usedFts = true
        } catch (e: Exception) {
            // Graceful fallback to substring LIKE if query has syntax issues or FTS5 table missing
            usedFts = false
        }

        if (!usedFts) {
            val sanitized = "%$cleanQuery%"
            val cursor = db.rawQuery(
                """
                SELECT id, url, title, domain, visited_at, summary 
                FROM history 
                WHERE title LIKE ? OR url LIKE ? OR domain LIKE ? OR summary LIKE ?
                ORDER BY visited_at DESC LIMIT ?
                """.trimIndent(),
                arrayOf(sanitized, sanitized, sanitized, sanitized, limit.toString())
            )
            cursor.use {
                val idCol = it.getColumnIndexOrThrow("id")
                val urlCol = it.getColumnIndexOrThrow("url")
                val titleCol = it.getColumnIndexOrThrow("title")
                val domainCol = it.getColumnIndexOrThrow("domain")
                val visitedCol = it.getColumnIndexOrThrow("visited_at")
                val summaryCol = it.getColumnIndexOrThrow("summary")

                while (it.moveToNext()) {
                    list.add(
                        HistoryItem(
                            id = it.getLong(idCol),
                            url = it.getString(urlCol),
                            title = it.getString(titleCol),
                            domain = it.getString(domainCol),
                            visitedAt = it.getLong(visitedCol),
                            summary = it.getString(summaryCol)
                        )
                    )
                }
            }
        }
        return list
    }

    fun clearHistory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("history", null, null)
            try {
                db.delete("history_fts", null, null)
            } catch (ignored: Exception) {
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ==========================================
    // Bookmarks DAO Operations
    // ==========================================

    fun addBookmark(url: String, title: String?, folder: String? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("folder", folder)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("bookmarks", null, values)
    }

    fun removeBookmark(id: Long): Boolean {
        val db = writableDatabase
        return db.delete("bookmarks", "id = ?", arrayOf(id.toString())) > 0
    }

    fun isBookmarked(url: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM bookmarks WHERE url = ? LIMIT 1", arrayOf(url))
        cursor.use {
            return it.count > 0
        }
    }

    fun getBookmarks(): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, url, title, folder, created_at FROM bookmarks ORDER BY created_at DESC",
            null
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val urlCol = it.getColumnIndexOrThrow("url")
            val titleCol = it.getColumnIndexOrThrow("title")
            val folderCol = it.getColumnIndexOrThrow("folder")
            val createdCol = it.getColumnIndexOrThrow("created_at")

            while (it.moveToNext()) {
                list.add(
                    BookmarkItem(
                        id = it.getLong(idCol),
                        url = it.getString(urlCol),
                        title = it.getString(titleCol),
                        folder = it.getString(folderCol),
                        createdAt = it.getLong(createdCol)
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // Sessions (Tabs) Operations
    // ==========================================

    fun saveSessions(sessions: List<TabSession>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sessions", null, null)
            for (tab in sessions) {
                // Do NOT persist private tabs per DESIGN.md and PRD.md
                if (tab.isPrivate) continue

                val values = ContentValues().apply {
                    put("tab_id", tab.tabId)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("is_private", 0)
                    put("last_active_at", tab.lastActiveAt)
                }
                db.insert("sessions", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadSessions(): List<TabSession> {
        val list = mutableListOf<TabSession>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, tab_id, url, title, is_private, last_active_at FROM sessions ORDER BY last_active_at DESC",
            null
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val tabIdCol = it.getColumnIndexOrThrow("tab_id")
            val urlCol = it.getColumnIndexOrThrow("url")
            val titleCol = it.getColumnIndexOrThrow("title")
            val isPrivateCol = it.getColumnIndexOrThrow("is_private")
            val lastActiveCol = it.getColumnIndexOrThrow("last_active_at")

            while (it.moveToNext()) {
                list.add(
                    TabSession(
                        id = it.getLong(idCol),
                        tabId = it.getString(tabIdCol),
                        url = it.getString(urlCol),
                        title = it.getString(titleCol),
                        isPrivate = it.getInt(isPrivateCol) == 1,
                        lastActiveAt = it.getLong(lastActiveCol)
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // Downloads Operations
    // ==========================================

    fun recordDownload(
        url: String,
        filename: String?,
        mimeType: String?,
        status: DownloadStatus,
        riskReason: String? = null
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("url", url)
            put("filename", filename)
            put("mime_type", mimeType)
            put("status", status.name.lowercase())
            put("risk_reason", riskReason)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("downloads", null, values)
    }

    fun updateDownloadStatus(id: Long, status: DownloadStatus, riskReason: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", status.name.lowercase())
            if (riskReason != null) {
                put("risk_reason", riskReason)
            }
        }
        db.update("downloads", values, "id = ?", arrayOf(id.toString()))
    }

    fun getDownloads(): List<DownloadItem> {
        val list = mutableListOf<DownloadItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, url, filename, mime_type, status, risk_reason, created_at FROM downloads ORDER BY created_at DESC",
            null
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val urlCol = it.getColumnIndexOrThrow("url")
            val fnCol = it.getColumnIndexOrThrow("filename")
            val mimeCol = it.getColumnIndexOrThrow("mime_type")
            val statusCol = it.getColumnIndexOrThrow("status")
            val riskCol = it.getColumnIndexOrThrow("risk_reason")
            val createdCol = it.getColumnIndexOrThrow("created_at")

            while (it.moveToNext()) {
                val statusStr = it.getString(statusCol).uppercase()
                val status = try {
                    DownloadStatus.valueOf(statusStr)
                } catch (e: Exception) {
                    DownloadStatus.PENDING
                }

                list.add(
                    DownloadItem(
                        id = it.getLong(idCol),
                        url = it.getString(urlCol),
                        filename = it.getString(fnCol),
                        mimeType = it.getString(mimeCol),
                        status = status,
                        riskReason = it.getString(riskCol),
                        createdAt = it.getLong(createdCol)
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // Security Rules DAO Operations (Phase 2)
    // ==========================================

    fun findMatchingSecurityRule(host: String, canonicalUrl: String): com.gintama.novabrowser.core.model.SecurityRule? {
        val db = readableDatabase

        // Generate parent domain candidates: e.g. "a.b.evil.com" -> ["a.b.evil.com", "b.evil.com", "evil.com"]
        val hostParts = host.split(".")
        val candidatePatterns = mutableListOf<String>()
        candidatePatterns.add(host)
        candidatePatterns.add(canonicalUrl)

        if (hostParts.size > 2) {
            for (i in 1 until hostParts.size - 1) {
                candidatePatterns.add(hostParts.subList(i, hostParts.size).joinToString("."))
            }
        }

        val placeholders = candidatePatterns.joinToString(",") { "?" }
        val query = """
            SELECT id, rule_type, pattern, source, severity, updated_at 
            FROM security_rules 
            WHERE pattern IN ($placeholders)
            ORDER BY 
                CASE severity 
                    WHEN 'block' THEN 1 
                    WHEN 'warn' THEN 2 
                    ELSE 3 
                END ASC 
            LIMIT 1
        """.trimIndent()

        val cursor = db.rawQuery(query, candidatePatterns.toTypedArray())
        cursor.use {
            if (it.moveToNext()) {
                val idCol = it.getColumnIndexOrThrow("id")
                val typeCol = it.getColumnIndexOrThrow("rule_type")
                val patternCol = it.getColumnIndexOrThrow("pattern")
                val sourceCol = it.getColumnIndexOrThrow("source")
                val sevCol = it.getColumnIndexOrThrow("severity")
                val updatedCol = it.getColumnIndexOrThrow("updated_at")

                val ruleType = try {
                    com.gintama.novabrowser.core.model.RuleType.valueOf(it.getString(typeCol).uppercase())
                } catch (e: Exception) {
                    com.gintama.novabrowser.core.model.RuleType.DOMAIN
                }

                val severity = try {
                    com.gintama.novabrowser.core.model.RuleSeverity.valueOf(it.getString(sevCol).uppercase())
                } catch (e: Exception) {
                    com.gintama.novabrowser.core.model.RuleSeverity.BLOCK
                }

                return com.gintama.novabrowser.core.model.SecurityRule(
                    id = it.getLong(idCol),
                    ruleType = ruleType,
                    pattern = it.getString(patternCol),
                    source = it.getString(sourceCol),
                    severity = severity,
                    updatedAt = it.getLong(updatedCol)
                )
            }
        }
        return null
    }

    fun batchInsertSecurityRules(rules: List<com.gintama.novabrowser.core.model.SecurityRule>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement("""
                INSERT OR REPLACE INTO security_rules (rule_type, pattern, source, severity, updated_at)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent())

            for (rule in rules) {
                stmt.clearBindings()
                stmt.bindString(1, rule.ruleType.name.lowercase())
                stmt.bindString(2, rule.pattern.lowercase().trim())
                stmt.bindString(3, rule.source.uppercase())
                stmt.bindString(4, rule.severity.name.lowercase())
                stmt.bindLong(5, rule.updatedAt)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateSnapshotMeta(source: String, ruleCount: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("feed_source", source.uppercase())
            put("last_updated_at", System.currentTimeMillis())
            put("rule_count", ruleCount)
        }
        db.insertWithOnConflict("snapshot_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSnapshotMeta(source: String): com.gintama.novabrowser.core.model.SnapshotMeta? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT feed_source, last_updated_at, rule_count FROM snapshot_meta WHERE feed_source = ? LIMIT 1",
            arrayOf(source.uppercase())
        )
        cursor.use {
            if (it.moveToNext()) {
                return com.gintama.novabrowser.core.model.SnapshotMeta(
                    feedSource = it.getString(0),
                    lastUpdatedAt = it.getLong(1),
                    ruleCount = it.getInt(2)
                )
            }
        }
        return null
    }

    fun getTotalSecurityRuleCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM security_rules", null)
        cursor.use {
            if (it.moveToNext()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    // ==========================================
    // Ad-Block Site Rules DAO (Phase 1 & Phase 4)
    // ==========================================

    fun getSiteAdBlockRule(domain: String): Pair<Boolean, Boolean>? {
        val cleanDomain = domain.lowercase().trim().removePrefix("www.")
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT adblock_enabled, cosmetic_enabled FROM adblock_site_rules WHERE domain = ?",
            arrayOf(cleanDomain)
        )
        cursor.use {
            if (it.moveToNext()) {
                val adBlock = it.getInt(0) == 1
                val cosmetic = it.getInt(1) == 1
                return Pair(adBlock, cosmetic)
            }
        }
        return null
    }

    fun setSiteAdBlockRule(domain: String, adBlockEnabled: Boolean, cosmeticEnabled: Boolean) {
        val cleanDomain = domain.lowercase().trim().removePrefix("www.")
        val db = writableDatabase
        val values = android.content.ContentValues().apply {
            put("domain", cleanDomain)
            put("adblock_enabled", if (adBlockEnabled) 1 else 0)
            put("cosmetic_enabled", if (cosmeticEnabled) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("adblock_site_rules", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllSiteAdBlockRules(): Map<String, Pair<Boolean, Boolean>> {
        val result = mutableMapOf<String, Pair<Boolean, Boolean>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT domain, adblock_enabled, cosmetic_enabled FROM adblock_site_rules", null)
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = Pair(it.getInt(1) == 1, it.getInt(2) == 1)
            }
        }
        return result
    }
}
