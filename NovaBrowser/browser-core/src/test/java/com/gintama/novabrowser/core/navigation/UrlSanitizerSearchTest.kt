package com.gintama.novabrowser.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlSanitizerSearchTest {

    @Test
    fun testBlankInputReturnsAboutBlank() {
        assertEquals("about:blank", UrlSanitizer.sanitizeInput(""))
        assertEquals("about:blank", UrlSanitizer.sanitizeInput("   "))
    }

    @Test
    fun testExplicitSchemePreserved() {
        assertEquals("https://news.ycombinator.com", UrlSanitizer.sanitizeInput("https://news.ycombinator.com"))
        assertEquals("http://example.com/test", UrlSanitizer.sanitizeInput("http://example.com/test"))
        assertEquals("about:blank", UrlSanitizer.sanitizeInput("about:blank"))
        assertEquals("file:///android_asset/offline.html", UrlSanitizer.sanitizeInput("file:///android_asset/offline.html"))
    }

    @Test
    fun testDomainPatternsUpgradedToHttps() {
        assertEquals("https://github.com", UrlSanitizer.sanitizeInput("github.com"))
        assertEquals("https://sub.domain.org/path?k=v", UrlSanitizer.sanitizeInput("sub.domain.org/path?k=v"))
        assertEquals("https://192.168.1.1:8080", UrlSanitizer.sanitizeInput("192.168.1.1:8080"))
        assertEquals("https://localhost:3000", UrlSanitizer.sanitizeInput("localhost:3000"))
    }

    @Test
    fun testSearchQueriesWithDefaultTemplate() {
        val result = UrlSanitizer.sanitizeInput("best rust web framework")
        assertEquals("https://duckduckgo.com/?q=best+rust+web+framework", result)
    }

    @Test
    fun testSearchQueriesWithBraveTemplate() {
        val braveTemplate = SearchEngine.BRAVE.queryUrlTemplate
        val result = UrlSanitizer.sanitizeInput("android webview security", braveTemplate)
        assertEquals("https://search.brave.com/search?q=android+webview+security", result)
    }

    @Test
    fun testSearchQueriesWithCustomTemplate() {
        val customTemplate = "https://custom.search/find?terms=%s"
        val result = UrlSanitizer.sanitizeInput("zero telemetry", customTemplate)
        assertEquals("https://custom.search/find?terms=zero+telemetry", result)
    }
}
