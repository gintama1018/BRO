package com.gintama.novabrowser.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NovaDiagnosticsTest {

    @Before
    fun setUp() {
        NovaDiagnostics.clear()
    }

    @Test
    fun testEventCapacityRingBufferBounding() {
        // Enforces max 100 entries in-memory limit
        for (i in 1..150) {
            NovaDiagnostics.log(DiagnosticType.SECURITY_BLOCK, "https://threat-$i.com/path", "Blocked threat $i")
        }

        val events = NovaDiagnostics.getRecentEvents()
        assertEquals(100, events.size)
        // Last recorded event must be threat-150
        assertTrue(events.last().domainOrScheme.contains("threat-150.com"))
    }

    @Test
    fun testPrivacySanitizationOmitsUrlsQueryAndTokens() {
        val sensitiveUrl = "https://user:pass@bank.example.com/login?token=secret123#fragment"
        NovaDiagnostics.log(DiagnosticType.RENDERER_CRASH, sensitiveUrl, "Renderer process terminated")

        val events = NovaDiagnostics.getRecentEvents()
        assertEquals(1, events.size)
        val event = events.first()

        assertEquals(DiagnosticType.RENDERER_CRASH, event.type)
        // Ensure scheme + host only, NO tokens or paths or user credentials
        assertFalse(event.domainOrScheme.contains("secret123"))
        assertFalse(event.domainOrScheme.contains("user:pass"))
        assertFalse(event.domainOrScheme.contains("login"))
        assertFalse(event.domainOrScheme.contains("fragment"))
        assertTrue(event.domainOrScheme.startsWith("https://"))
    }
}
