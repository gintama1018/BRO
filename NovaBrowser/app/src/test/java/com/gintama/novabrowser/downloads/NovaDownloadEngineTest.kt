package com.gintama.novabrowser.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaDownloadEngineTest {

    @Test
    fun `formatSpeed formats bytes per second correctly`() {
        assertEquals("0 KB/s", NovaDownloadEngine.formatSpeed(0L))
        assertEquals("500 B/s", NovaDownloadEngine.formatSpeed(500L))
        assertEquals("1.5 KB/s", NovaDownloadEngine.formatSpeed(1536L))
        assertEquals("1.00 MB/s", NovaDownloadEngine.formatSpeed(1024L * 1024L))
        assertEquals("12.50 MB/s", NovaDownloadEngine.formatSpeed((12.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatEta formats remaining seconds, minutes, and hours`() {
        assertEquals("--", NovaDownloadEngine.formatEta(0L, 1000L))
        assertEquals("--", NovaDownloadEngine.formatEta(1000L, 0L))

        // 30 seconds
        assertEquals("30s", NovaDownloadEngine.formatEta(3000L, 100L))

        // 1 minute 30 seconds
        assertEquals("1m 30s", NovaDownloadEngine.formatEta(9000L, 100L))

        // 1 hour 15 minutes
        val oneHourFifteenMinBytes = (3600 + 900) * 100L
        assertEquals("1h 15m", NovaDownloadEngine.formatEta(oneHourFifteenMinBytes, 100L))
    }
}
