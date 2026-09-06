package com.gintama.novabrowser.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying SiteShieldSettings invariants and SiteShieldManager domain normalization.
 */
class SiteShieldManagerTest {

    @Test
    fun defaultSettings_haveAllProtectionsActive() {
        val settings = SiteShieldSettings(domain = "example.com")

        assertTrue("Master shields should be UP by default", settings.shieldsEnabled)
        assertTrue("Ad blocking should be active", settings.isAdBlockActive())
        assertTrue("Cosmetic element hiding should be active", settings.isCosmeticActive())
        assertTrue("JavaScript execution should be allowed by default", settings.isJavaScriptAllowed())
        assertTrue("Third-party cookies should be blocked by default", settings.isThirdPartyCookiesBlocked())
    }

    @Test
    fun masterShieldsDown_bypassesAllInterceptions() {
        val paused = SiteShieldSettings(
            domain = "broken-site.com",
            shieldsEnabled = false,
            adBlockEnabled = true,
            cosmeticEnabled = true,
            javaScriptEnabled = false, // Even if JS was disabled, pausing shields allows JS
            blockThirdPartyCookies = true
        )

        assertFalse("Ad blocking should be bypassed when shields are DOWN", paused.isAdBlockActive())
        assertFalse("Cosmetic filtering should be skipped when shields are DOWN", paused.isCosmeticActive())
        assertTrue("JavaScript must be allowed when shields are DOWN", paused.isJavaScriptAllowed())
        assertFalse("Third-party cookies should NOT be blocked when shields are DOWN", paused.isThirdPartyCookiesBlocked())
    }

    @Test
    fun granularToggles_isolateIndividualProtections() {
        // Disabling JS only
        val noJs = SiteShieldSettings(domain = "news.com", javaScriptEnabled = false)
        assertFalse("JavaScript should be blocked", noJs.isJavaScriptAllowed())
        assertTrue("Ad blocking should remain active", noJs.isAdBlockActive())
        assertTrue("Cosmetic filtering should remain active", noJs.isCosmeticActive())
        assertTrue("Third-party cookies should remain blocked", noJs.isThirdPartyCookiesBlocked())

        // Disabling AdBlock only (e.g. for streaming video CDNs)
        val noAdBlock = SiteShieldSettings(domain = "video.com", adBlockEnabled = false)
        assertFalse("Ad blocking should be inactive", noAdBlock.isAdBlockActive())
        assertTrue("Cosmetic filtering should remain active", noAdBlock.isCosmeticActive())
        assertTrue("JavaScript should remain allowed", noAdBlock.isJavaScriptAllowed())

        // Allowing third-party cookies (e.g. for federated SSO login widgets)
        val allowThirdPartyCookies = SiteShieldSettings(domain = "sso.portal.com", blockThirdPartyCookies = false)
        assertFalse("Third-party cookies should not be blocked", allowThirdPartyCookies.isThirdPartyCookiesBlocked())
        assertTrue("Ad blocking should remain active", allowThirdPartyCookies.isAdBlockActive())
    }

    @Test
    fun normalizeDomain_cleansUrlsCorrectly() {
        assertEquals("example.com", SiteShieldManager.normalizeDomain("https://www.example.com/article/123"))
        assertEquals("github.com", SiteShieldManager.normalizeDomain("https://github.com"))
        assertEquals("reddit.com", SiteShieldManager.normalizeDomain("WWW.REDDIT.COM"))
        assertEquals("sub.domain.org", SiteShieldManager.normalizeDomain("http://sub.domain.org:8080/path"))
        assertEquals("wikipedia.org", SiteShieldManager.normalizeDomain("wikipedia.org/wiki/Kotlin"))
        assertEquals("Local Canvas", SiteShieldManager.normalizeDomain("about:blank"))
        assertEquals("", SiteShieldManager.normalizeDomain(null))
        assertEquals("", SiteShieldManager.normalizeDomain("   "))
    }

    @Test
    fun localCanvas_resolvesSafeDefaultSettings() {
        val settings = SiteShieldManager.getSettingsForSite("about:blank")
        assertEquals("Local Canvas", settings.domain)
        assertTrue(settings.shieldsEnabled)
    }
}
