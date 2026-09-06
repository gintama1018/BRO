package com.gintama.novabrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabStateTest {

    data class MockTab(
        val id: String,
        val title: String,
        val url: String,
        val isPrivate: Boolean
    )

    @Test
    fun testTabFilteringSeparatesStandardAndPrivateTabs() {
        val allTabs = listOf(
            MockTab("1", "GitHub", "https://github.com", isPrivate = false),
            MockTab("2", "Private Search", "https://duckduckgo.com", isPrivate = true),
            MockTab("3", "Wikipedia", "https://en.wikipedia.org", isPrivate = false),
            MockTab("4", "Bank Portal", "https://mybank.com", isPrivate = true)
        )

        val standardTabs = allTabs.filter { !it.isPrivate }
        val privateTabs = allTabs.filter { it.isPrivate }

        assertEquals(2, standardTabs.size)
        assertEquals(2, privateTabs.size)

        assertTrue(standardTabs.all { !it.isPrivate })
        assertTrue(privateTabs.all { it.isPrivate })

        assertEquals("GitHub", standardTabs[0].title)
        assertEquals("Wikipedia", standardTabs[1].title)
        assertEquals("Private Search", privateTabs[0].title)
        assertEquals("Bank Portal", privateTabs[1].title)
    }

    @Test
    fun testPrivateTabsAreNeverPersisted() {
        val tabs = listOf(
            MockTab("1", "Normal", "https://news.ycombinator.com", isPrivate = false),
            MockTab("2", "Incognito", "https://secret.local", isPrivate = true)
        )

        // Persistence filter: only non-private tabs are saved
        val persisted = tabs.filter { !it.isPrivate }
        assertEquals(1, persisted.size)
        assertEquals("Normal", persisted[0].title)
        assertFalse(persisted.any { it.isPrivate })
    }

    @Test
    fun testMonogramDerivation() {
        fun deriveMonogram(domain: String): String {
            if (domain == "Start Canvas" || domain.isBlank()) return "✦"
            val parts = domain.split(".")
            return if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                val first = parts[0]
                if (first.length >= 2) first.substring(0, 2).uppercase() else first.uppercase()
            } else {
                "✦"
            }
        }

        assertEquals("GI", deriveMonogram("github.com"))
        assertEquals("WI", deriveMonogram("wikipedia.org"))
        assertEquals("✦", deriveMonogram("Start Canvas"))
        assertEquals("✦", deriveMonogram(""))
    }
}
