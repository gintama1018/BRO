package com.gintama.novabrowser.core.controller

import android.content.Context
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.BookmarkItem
import com.gintama.novabrowser.core.model.HistoryItem
import com.gintama.novabrowser.core.model.TabSession
import com.gintama.novabrowser.core.navigation.UrlSanitizer
import com.gintama.novabrowser.core.security.DeterministicSecurityGate
import com.gintama.novabrowser.core.security.SecurityDecision
import com.gintama.novabrowser.core.security.SecurityGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central Controller orchestrating browser state, history recording,
 * bookmarks, sessions, and security gate routing.
 */
class BrowserController(
    private val context: Context,
    val securityGate: SecurityGate = DeterministicSecurityGate(context)
) {
    private val db = NovaDatabaseHelper.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    var defaultSearchTemplate: String = com.gintama.novabrowser.core.navigation.SearchEngine.DEFAULT.queryUrlTemplate

    fun evaluateNavigation(
        rawInput: String,
        isRedirect: Boolean = false,
        searchEngineTemplate: String = defaultSearchTemplate
    ): Pair<String, SecurityDecision> {
        val sanitizedUrl = UrlSanitizer.sanitizeInput(rawInput, searchEngineTemplate)
        val decision = securityGate.evaluate(sanitizedUrl, isRedirect)
        return Pair(sanitizedUrl, decision)
    }

    fun onPageVisited(url: String, title: String?, isPrivate: Boolean) {
        if (isPrivate || url == "about:blank") return

        scope.launch {
            val domain = UrlSanitizer.extractDomain(url)
            db.recordHistory(
                url = url,
                title = if (title.isNullOrBlank()) domain else title,
                domain = domain
            )
        }
    }

    fun toggleBookmark(url: String, title: String?, onResult: (Boolean) -> Unit) {
        scope.launch {
            val alreadyBookmarked = db.isBookmarked(url)
            if (alreadyBookmarked) {
                // Find and remove
                val all = db.getBookmarks()
                val match = all.firstOrNull { it.url == url }
                if (match != null) {
                    db.removeBookmark(match.id)
                }
                withContext(Dispatchers.Main) { onResult(false) }
            } else {
                val domain = UrlSanitizer.extractDomain(url)
                val cleanTitle = if (title.isNullOrBlank()) domain else title
                db.addBookmark(url = url, title = cleanTitle)
                withContext(Dispatchers.Main) { onResult(true) }
            }
        }
    }

    suspend fun isBookmarked(url: String): Boolean = withContext(Dispatchers.IO) {
        db.isBookmarked(url)
    }

    suspend fun getRecentHistory(limit: Int = 100): List<HistoryItem> = withContext(Dispatchers.IO) {
        db.getRecentHistory(limit)
    }

    suspend fun searchHistory(query: String): List<HistoryItem> = withContext(Dispatchers.IO) {
        db.searchHistory(query)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        db.clearHistory()
    }

    suspend fun getBookmarks(): List<BookmarkItem> = withContext(Dispatchers.IO) {
        db.getBookmarks()
    }

    suspend fun deleteBookmark(id: Long): Boolean = withContext(Dispatchers.IO) {
        db.removeBookmark(id)
    }

    fun saveSessions(tabs: List<TabSession>) {
        scope.launch {
            db.saveSessions(tabs)
        }
    }

    suspend fun restoreSessions(): List<TabSession> = withContext(Dispatchers.IO) {
        db.loadSessions()
    }
}
