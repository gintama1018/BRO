package com.gintama.novabrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSchemeHandlerTest {

    @Test
    fun testWebSchemesAreRecognized() {
        assertTrue(ExternalSchemeHandler.isWebScheme("http://example.com"))
        assertTrue(ExternalSchemeHandler.isWebScheme("https://secure.site.org/path?q=1"))
        assertTrue(ExternalSchemeHandler.isWebScheme("about:blank"))
        assertTrue(ExternalSchemeHandler.isWebScheme("file:///android_asset/page.html"))
        assertTrue(ExternalSchemeHandler.isWebScheme("javascript:void(0)"))
        assertTrue(ExternalSchemeHandler.isWebScheme("data:text/html,<h1>Hello</h1>"))

        assertFalse(ExternalSchemeHandler.isWebScheme("tel:1234567890"))
        assertFalse(ExternalSchemeHandler.isWebScheme("upi://pay?pa=merchant@bank"))
    }

    @Test
    fun testExternalSchemesAreDetected() {
        assertTrue(ExternalSchemeHandler.isExternalScheme("tel:+1234567890"))
        assertTrue(ExternalSchemeHandler.isExternalScheme("mailto:support@novabrowser.org"))
        assertTrue(ExternalSchemeHandler.isExternalScheme("sms:+919876543210"))
        assertTrue(ExternalSchemeHandler.isExternalScheme("geo:37.7749,-122.4194"))
        assertTrue(ExternalSchemeHandler.isExternalScheme("upi://pay?pa=merchant@upi&pn=Store&am=100"))
        assertTrue(ExternalSchemeHandler.isExternalScheme("market://details?id=com.gintama.novabrowser"))
        assertTrue(ExternalSchemeHandler.isExternalScheme("intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end"))

        // Standard web URLs must NOT be treated as external schemes
        assertFalse(ExternalSchemeHandler.isExternalScheme("https://google.com"))
        assertFalse(ExternalSchemeHandler.isExternalScheme("http://localhost:8080"))
        assertFalse(ExternalSchemeHandler.isExternalScheme("about:blank"))
        assertFalse(ExternalSchemeHandler.isExternalScheme("file:///storage/emulated/0/test.pdf"))
        assertFalse(ExternalSchemeHandler.isExternalScheme("unknownscheme://action"))
    }
}
