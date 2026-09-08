package com.gintama.novabrowser.backup

import android.content.Context
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.BookmarkItem
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * NovaBackupManager: 100% local-first backup, export, and import manager.
 * Supports:
 * - Netscape Bookmark File (HTML) standard export & import (compatible with Firefox, Chrome, Brave, Safari).
 * - Local JSON settings export & import.
 * Zero telemetry, zero cloud dependencies.
 */
object NovaBackupManager {

    private val BOOKMARK_A_TAG_PATTERN = Pattern.compile(
        "<A\\s+[^>]*HREF=[\"'](?<url>[^\"']+)[\"'][^>]*>(?<title>[^<]*)</A>",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Exports all user bookmarks to an industry-standard Netscape Bookmark HTML file.
     */
    fun exportBookmarksToHtml(context: Context, outputStream: OutputStream) {
        val db = NovaDatabaseHelper.getInstance(context)
        val bookmarks = db.getBookmarks()

        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write("""
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <!-- This is an automatically generated file.
                     It will be read and overwritten.
                     DO NOT EDIT! -->
                <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
                <TITLE>Bookmarks</TITLE>
                <H1>Bookmarks</H1>
                <DL><p>

            """.trimIndent())

            for (bm in bookmarks) {
                val escapedTitle = escapeHtml(bm.title ?: bm.url)
                val escapedUrl = escapeHtml(bm.url)
                val addDate = bm.createdAt / 1000L
                writer.write("    <DT><A HREF=\"$escapedUrl\" ADD_DATE=\"$addDate\">$escapedTitle</A>\n")
            }

            writer.write("</DL><p>\n")
            writer.flush()
        }
    }

    /**
     * Imports bookmarks from a Netscape Bookmark HTML stream into the local SQLite database.
     * Returns the count of successfully imported bookmarks.
     */
    fun importBookmarksFromHtml(context: Context, inputStream: InputStream): Int {
        val db = NovaDatabaseHelper.getInstance(context)
        var importedCount = 0

        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                val matcher = BOOKMARK_A_TAG_PATTERN.matcher(line)
                while (matcher.find()) {
                    val rawUrl = matcher.group("url")
                    val rawTitle = matcher.group("title")
                    if (!rawUrl.isNullOrBlank()) {
                        val unescapedUrl = unescapeHtml(rawUrl.trim())
                        val unescapedTitle = unescapeHtml(rawTitle?.trim() ?: unescapedUrl)
                        if (unescapedUrl.startsWith("http://", ignoreCase = true) ||
                            unescapedUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            db.addBookmark(unescapedUrl, unescapedTitle)
                            importedCount++
                        }
                    }
                }
                line = reader.readLine()
            }
        }
        return importedCount
    }

    /**
     * Exports local user settings and feature preferences into a JSON structure.
     */
    fun exportSettingsToJson(context: Context, outputStream: OutputStream) {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val json = JSONObject()
        val allEntries = prefs.all

        val settingsObj = JSONObject()
        for (entry in allEntries.entries) {
            settingsObj.put(entry.key, entry.value)
        }

        json.put("version", 1)
        json.put("exportedAt", System.currentTimeMillis())
        json.put("settings", settingsObj)

        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(json.toString(2))
            writer.flush()
        }
    }

    /**
     * Imports user settings from a JSON input stream.
     */
    fun importSettingsFromJson(context: Context, inputStream: InputStream): Boolean {
        return try {
            val content = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            val json = JSONObject(content)
            val settingsObj = json.optJSONObject("settings") ?: return false

            val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = settingsObj.get(key)
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }
}
