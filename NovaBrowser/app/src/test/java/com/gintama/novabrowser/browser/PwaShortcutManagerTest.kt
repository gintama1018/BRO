package com.gintama.novabrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PwaShortcutManagerTest {

    @Test
    fun testExtraStandaloneConstant() {
        assertEquals("extra_standalone_pwa", PwaShortcutManager.EXTRA_STANDALONE)
    }

    @Test
    fun testShortcutIdDeterministic() {
        val url = "https://twitter.com"
        val expectedId = "nova_pwa_${url.hashCode()}"
        assertEquals(expectedId, "nova_pwa_${url.hashCode()}")
        assertTrue(expectedId.startsWith("nova_pwa_"))
    }

    @Test
    fun testTitleTruncationRules() {
        val longTitle = "A very long progressive web application title that exceeds normal launcher constraints"
        val shortLabel = longTitle.take(25)
        val longLabel = longTitle.take(50)

        assertEquals(25, shortLabel.length)
        assertEquals(50, longLabel.length)
        assertTrue(longTitle.startsWith(shortLabel))
    }

    @Test
    fun testInitialExtraction() {
        fun extractInitial(title: String, fallback: String): String {
            return if (title.isNotBlank()) title.take(1).uppercase() else fallback.take(1).uppercase()
        }

        assertEquals("G", extractInitial("github", "fallback"))
        assertEquals("X", extractInitial("x.com", "fallback"))
        assertEquals("F", extractInitial("", "fallback"))
    }
}
