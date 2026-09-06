package com.gintama.novabrowser.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun testDefaultEngineIsDuckDuckGo() {
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.DEFAULT)
        assertEquals("duckduckgo", SearchEngine.DEFAULT.id)
    }

    @Test
    fun testFromIdResolution() {
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("duckduckgo"))
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromId("brave"))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId("google"))
        assertEquals(SearchEngine.BING, SearchEngine.fromId("bing"))
        assertEquals(SearchEngine.STARTPAGE, SearchEngine.fromId("startpage"))
        assertEquals(SearchEngine.ECOSIA, SearchEngine.fromId("ecosia"))
        assertEquals(SearchEngine.CUSTOM, SearchEngine.fromId("custom"))

        // Case insensitivity
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromId("BRAVE"))

        // Null and invalid fallbacks
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId(null))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId(""))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("unknown_engine"))
    }

    @Test
    fun testBuildSearchUrlWithBuiltInEngines() {
        val query = "kotlin android"

        val ddgUrl = SearchEngine.buildSearchUrl(query, SearchEngine.DUCKDUCKGO.queryUrlTemplate)
        assertEquals("https://duckduckgo.com/?q=kotlin+android", ddgUrl)

        val braveUrl = SearchEngine.buildSearchUrl(query, SearchEngine.BRAVE.queryUrlTemplate)
        assertEquals("https://search.brave.com/search?q=kotlin+android", braveUrl)

        val googleUrl = SearchEngine.buildSearchUrl(query, SearchEngine.GOOGLE.queryUrlTemplate)
        assertEquals("https://www.google.com/search?q=kotlin+android", googleUrl)

        val bingUrl = SearchEngine.buildSearchUrl(query, SearchEngine.BING.queryUrlTemplate)
        assertEquals("https://www.bing.com/search?q=kotlin+android", bingUrl)

        val spUrl = SearchEngine.buildSearchUrl(query, SearchEngine.STARTPAGE.queryUrlTemplate)
        assertEquals("https://www.startpage.com/sp/search?query=kotlin+android", spUrl)

        val ecosiaUrl = SearchEngine.buildSearchUrl(query, SearchEngine.ECOSIA.queryUrlTemplate)
        assertEquals("https://www.ecosia.org/search?q=kotlin+android", ecosiaUrl)
    }

    @Test
    fun testCustomUrlTemplateSubstitution() {
        val customTemplate = "https://kagi.com/search?q=%s"
        val query = "zero telemetry & privacy"
        val result = SearchEngine.buildSearchUrl(query, customTemplate)

        assertEquals("https://kagi.com/search?q=zero+telemetry+%26+privacy", result)
    }

    @Test
    fun testCustomUrlTemplateFallbacks() {
        // Without %s, ending with '='
        val resultEq = SearchEngine.buildSearchUrl("test query", "https://searx.be/search?q=")
        assertEquals("https://searx.be/search?q=test+query", resultEq)

        // Without %s, plain URL
        val resultPlain = SearchEngine.buildSearchUrl("test query", "https://searx.be")
        assertEquals("https://searx.be?q=test+query", resultPlain)
    }
}
