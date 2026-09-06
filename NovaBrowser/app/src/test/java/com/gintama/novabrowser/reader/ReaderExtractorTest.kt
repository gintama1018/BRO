package com.gintama.novabrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderExtractorTest {

    @Test
    fun testReaderThemeResolution() {
        assertEquals(ReaderTheme.DARK, ReaderTheme.fromId("dark"))
        assertEquals(ReaderTheme.SEPIA, ReaderTheme.fromId("sepia"))
        assertEquals(ReaderTheme.LIGHT, ReaderTheme.fromId("light"))
        assertEquals(ReaderTheme.SLATE, ReaderTheme.fromId("slate"))

        assertEquals(ReaderTheme.DARK, ReaderTheme.fromId(null))
        assertEquals(ReaderTheme.DARK, ReaderTheme.fromId(""))
        assertEquals(ReaderTheme.DARK, ReaderTheme.fromId("unknown"))
    }

    @Test
    fun testReaderFontResolution() {
        assertEquals(ReaderFont.SANS_SERIF, ReaderFont.fromId("sans"))
        assertEquals(ReaderFont.SERIF, ReaderFont.fromId("serif"))
        assertEquals(ReaderFont.MONOSPACE, ReaderFont.fromId("mono"))

        assertEquals(ReaderFont.SANS_SERIF, ReaderFont.fromId(null))
        assertEquals(ReaderFont.SANS_SERIF, ReaderFont.fromId("unknown"))
    }

    @Test
    fun testArticleHtmlGenerationContainsThemeAndTypography() {
        val article = ArticleContent(
            title = "Deep Learning Architectures",
            byline = "Jane Doe",
            siteName = "techreview.com",
            originalUrl = "https://techreview.com/deep-learning",
            contentHtml = "<p>Neural networks have transformed computation.</p>",
            wordCount = 600,
            readTimeMinutes = 3
        )

        val htmlDark = ReaderPreferences.generateHtmlDocument(
            article = article,
            theme = ReaderTheme.DARK,
            font = ReaderFont.SERIF,
            fontScalePercent = 110
        )

        assertTrue(htmlDark.contains("Deep Learning Architectures"))
        assertTrue(htmlDark.contains("Jane Doe"))
        assertTrue(htmlDark.contains("techreview.com"))
        assertTrue(htmlDark.contains("3 min read"))
        assertTrue(htmlDark.contains(ReaderTheme.DARK.bgColor))
        assertTrue(htmlDark.contains(ReaderTheme.DARK.textColor))
        assertTrue(htmlDark.contains("Georgia"))

        // Verify Sepia theme
        val htmlSepia = ReaderPreferences.generateHtmlDocument(
            article = article,
            theme = ReaderTheme.SEPIA,
            font = ReaderFont.SANS_SERIF,
            fontScalePercent = 100
        )
        assertTrue(htmlSepia.contains(ReaderTheme.SEPIA.bgColor))
        assertTrue(htmlSepia.contains(ReaderTheme.SEPIA.textColor))
    }

    @Test
    fun testHtmlEscapingPreventsInjectionInMetadata() {
        val article = ArticleContent(
            title = "Dangerous <script>alert(1)</script> Title",
            byline = "Hacker & Co.",
            siteName = "evil<tag>.org",
            originalUrl = "https://evil.org",
            contentHtml = "<p>Safe content</p>",
            wordCount = 100,
            readTimeMinutes = 1
        )

        val html = ReaderPreferences.generateHtmlDocument(
            article = article,
            theme = ReaderTheme.DARK,
            font = ReaderFont.SANS_SERIF,
            fontScalePercent = 100
        )

        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("Hacker &amp; Co."))
        assertTrue(html.contains("evil&lt;tag&gt;.org"))
    }

    @Test
    fun testArticleReadingTimeCalculation() {
        fun calculateReadTime(words: Int): Int = Math.max(1, Math.ceil(words / 200.0).toInt())

        assertEquals(1, calculateReadTime(50))
        assertEquals(1, calculateReadTime(200))
        assertEquals(2, calculateReadTime(201))
        assertEquals(3, calculateReadTime(600))
        assertEquals(5, calculateReadTime(1000))

        val article = ArticleContent(
            title = "Quantum Computing Breakthrough",
            byline = "Alice Smith",
            siteName = "nature.com",
            originalUrl = "https://nature.com/articles/quantum",
            contentHtml = "<p>Superconducting qubits achieved 99.9% fidelity.</p>",
            wordCount = 450,
            readTimeMinutes = calculateReadTime(450)
        )

        assertEquals("Quantum Computing Breakthrough", article.title)
        assertEquals("Alice Smith", article.byline)
        assertEquals(450, article.wordCount)
        assertEquals(3, article.readTimeMinutes)
    }
}
