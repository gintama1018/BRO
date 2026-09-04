package com.gintama.novabrowser.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectTrackerTest {

    @Test
    fun testProtocolDowngradeDetection() {
        val tracker = RedirectTracker()
        tracker.recordHop(UrlCanonicalizer.canonicalize("https://secure-site.com/auth"))
        val analysis = tracker.recordHop(UrlCanonicalizer.canonicalize("http://unencrypted-destination.com/login"))

        assertTrue(analysis.hasDowngrade)
        assertTrue(analysis.warningReasons.any { it.contains("protocol downgrade", ignoreCase = true) })
    }

    @Test
    fun testExcessiveRedirectLoopDetection() {
        val tracker = RedirectTracker()
        tracker.recordHop(UrlCanonicalizer.canonicalize("https://site1.com"))
        tracker.recordHop(UrlCanonicalizer.canonicalize("https://site2.com"))
        tracker.recordHop(UrlCanonicalizer.canonicalize("https://site3.com"))
        tracker.recordHop(UrlCanonicalizer.canonicalize("https://site4.com"))
        val analysis = tracker.recordHop(UrlCanonicalizer.canonicalize("https://site5.com"))

        assertTrue(analysis.isExcessiveLength)
        assertTrue(analysis.totalHops > 4)
    }

    @Test
    fun testNormalNavigationHasNoDowngrade() {
        val tracker = RedirectTracker()
        val analysis = tracker.recordHop(UrlCanonicalizer.canonicalize("https://google.com"))
        assertFalse(analysis.hasDowngrade)
        assertFalse(analysis.isExcessiveLength)
    }
}
