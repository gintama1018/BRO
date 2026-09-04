package com.gintama.novabrowser.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCanonicalizerTest {

    @Test
    fun testPunycodeDecoding() {
        // xn--pypa-4ve.com -> unicode
        val input = "https://xn--pypa-4ve.com/login"
        val canonical = UrlCanonicalizer.canonicalize(input)
        assertTrue(canonical.isPunycode)
        assertEquals("xn--pypa-4ve.com", canonical.host)
        assertTrue(canonical.unicodeHost.isNotEmpty())
    }

    @Test
    fun testNestedPercentEncoding() {
        // Nested encoded: %252e -> %2e -> .
        val input = "https://example%252ecom/test"
        val canonical = UrlCanonicalizer.canonicalize(input)
        assertEquals("example.com", canonical.host)
    }

    @Test
    fun testStripUserCredentials() {
        val input = "https://admin:secret@malicious-site.com/payload"
        val canonical = UrlCanonicalizer.canonicalize(input)
        assertEquals("malicious-site.com", canonical.host)
        assertFalse(canonical.canonicalUrl.contains("admin:secret"))
    }

    @Test
    fun testNormalizeDefaultPorts() {
        val inputHttp = "http://example.com:80/index.html"
        val canonicalHttp = UrlCanonicalizer.canonicalize(inputHttp)
        assertEquals("http://example.com/index.html", canonicalHttp.canonicalUrl)

        val inputHttps = "https://example.com:443/secure"
        val canonicalHttps = UrlCanonicalizer.canonicalize(inputHttps)
        assertEquals("https://example.com/secure", canonicalHttps.canonicalUrl)
    }
}
