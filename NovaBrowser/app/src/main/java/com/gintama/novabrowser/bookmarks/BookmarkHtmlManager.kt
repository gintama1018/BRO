package com.gintama.novabrowser.bookmarks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.gintama.novabrowser.core.model.BookmarkItem
import java.io.File
import java.util.regex.Pattern

/**
 * BookmarkHtmlManager: Universal Netscape Bookmark File parser and generator.
 * Provides 100% interoperability with Google Chrome, Mozilla Firefox, Apple Safari, Brave, and Edge.
 */
object BookmarkHtmlManager {

    private val BOOKMARK_REGEX = Pattern.compile(
        """<A\s+[^>]*HREF=["']([^"']+)["'][^>]*>(.*?)</A>""",
        Pattern.CASE_INSENSITIVE or Pattern.DOTALL
    )

    fun exportBookmarksToHtml(bookmarks: List<BookmarkItem>): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file from NovaBrowser. -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>Bookmarks</TITLE>\n")
        sb.append("<H1>Bookmarks</H1>\n")
        sb.append("<DL><p>\n")

        for (bm in bookmarks) {
            val safeUrl = escapeHtml(bm.url)
            val titleText = if (!bm.title.isNullOrBlank()) bm.title else bm.url
            val safeTitle = escapeHtml(titleText)
            val timestampSec = bm.createdAt / 1000L
            sb.append("    <DT><A HREF=\"$safeUrl\" ADD_DATE=\"$timestampSec\">$safeTitle</A>\n")
        }

        sb.append("</DL><p>\n")
        return sb.toString()
    }

    fun importBookmarksFromHtml(htmlContent: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val seenUrls = mutableSetOf<String>()

        val matcher = BOOKMARK_REGEX.matcher(htmlContent)
        while (matcher.find()) {
            val url = unescapeHtml(matcher.group(1)?.trim().orEmpty())
            var title = unescapeHtml(matcher.group(2)?.trim().orEmpty())
            title = title.replace(Regex("<[^>]*>"), "").trim()

            if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                if (!seenUrls.contains(url)) {
                    seenUrls.add(url)
                    results.add(Pair(url, if (title.isNotBlank()) title else url))
                }
            }
        }
        return results
    }

    fun shareExportedBookmarks(activity: Activity, bookmarks: List<BookmarkItem>): File? {
        return try {
            val htmlData = exportBookmarksToHtml(bookmarks)
            val exportDir = File(activity.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "nova_bookmarks.html")
            file.writeText(htmlData, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "NovaBrowser Bookmarks Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Export Bookmarks via"))
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeHtml(text: String?): String {
        return text.orEmpty()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun unescapeHtml(text: String?): String {
        return text.orEmpty()
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
    }
}
