package com.gintama.novabrowser.bookmarks

import com.gintama.novabrowser.core.model.BookmarkItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkHtmlManagerTest {

    @Test
    fun `exportBookmarksToHtml formats valid Netscape bookmark file`() {
        val bookmarks = listOf(
            BookmarkItem(id = 1L, url = "https://github.com", title = "GitHub & Code", createdAt = 1672531199000L),
            BookmarkItem(id = 2L, url = "https://duckduckgo.com", title = "Search \"Safely\"", createdAt = 1672531200000L)
        )

        val html = BookmarkHtmlManager.exportBookmarksToHtml(bookmarks)

        assertTrue(html.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("<TITLE>Bookmarks</TITLE>"))
        assertTrue(html.contains("<H1>Bookmarks</H1>"))
        assertTrue(html.contains("HREF=\"https://github.com\""))
        assertTrue(html.contains("GitHub &amp; Code"))
        assertTrue(html.contains("Search &quot;Safely&quot;"))
        assertTrue(html.contains("</DL>"))
    }

    @Test
    fun `importBookmarksFromHtml parses Netscape bookmarks from Chrome or Firefox`() {
        val rawHtml = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><A HREF="https://kotlinlang.org" ADD_DATE="1600000000">Kotlin Programming</A>
                <DT><A HREF="https://android.com" ADD_DATE="1600000001">Android Developers</A>
                <DT><A HREF="javascript:void(0)">Invalid Script</A>
                <DT><A HREF="https://kotlinlang.org" ADD_DATE="1600000002">Duplicate Kotlin</A>
            </DL><p>
        """.trimIndent()

        val parsed = BookmarkHtmlManager.importBookmarksFromHtml(rawHtml)

        assertEquals(2, parsed.size)
        assertEquals("https://kotlinlang.org", parsed[0].first)
        assertEquals("Kotlin Programming", parsed[0].second)
        assertEquals("https://android.com", parsed[1].first)
        assertEquals("Android Developers", parsed[1].second)
    }

    @Test
    fun `importBookmarksFromHtml handles HTML entities and nested tags`() {
        val rawHtml = """
            <DL><p>
                <DT><A HREF="https://example.com/search?q=a&amp;b=c"><b>Example</b> &amp; Test &#39;Quote&#39;</A>
            </DL><p>
        """.trimIndent()

        val parsed = BookmarkHtmlManager.importBookmarksFromHtml(rawHtml)

        assertEquals(1, parsed.size)
        assertEquals("https://example.com/search?q=a&b=c", parsed[0].first)
        assertEquals("Example & Test 'Quote'", parsed[0].second)
    }

    @Test
    fun `importBookmarksFromHtml returns empty list on invalid or empty html`() {
        val parsed = BookmarkHtmlManager.importBookmarksFromHtml("<html><body>No bookmarks here</body></html>")
        assertTrue(parsed.isEmpty())
    }
}
