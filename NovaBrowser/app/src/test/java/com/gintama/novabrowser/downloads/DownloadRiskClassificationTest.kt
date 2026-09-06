package com.gintama.novabrowser.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class DownloadRiskClassificationTest {

    @Test
    fun testRiskyExtensionsTriggerQuarantineClassification() {
        val riskyList = listOf(
            "apk", "dex", "sh", "bat", "exe", "vbs", "js", "cmd", "msi", "scr", "jar",
            ".APK", ".EXE", ".sh", ".Dex"
        )
        for (ext in riskyList) {
            assertTrue("Extension '$ext' must be flagged as risky", DownloadHandler.isRiskyExtension(ext))
        }
    }

    @Test
    fun testSafeExtensionsBypassQuarantine() {
        val safeList = listOf(
            "pdf", "png", "jpg", "jpeg", "webp", "gif", "txt", "mp3", "mp4", "zip", "csv", "json", "svg"
        )
        for (ext in safeList) {
            assertFalse("Extension '$ext' must be classified as safe", DownloadHandler.isRiskyExtension(ext))
        }
    }

    @Test
    fun testQuarantineDigestComputation() {
        val payload = "NOVA_QUARANTINE_PAYLOAD_TEST_DATA".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val hashHex = digest.joinToString("") { "%02x".format(it) }

        assertEquals(64, hashHex.length)
        // Verify deterministic reproducibility
        val secondDigest = MessageDigest.getInstance("SHA-256").digest(payload)
        val secondHash = secondDigest.joinToString("") { "%02x".format(it) }
        assertEquals(hashHex, secondHash)
    }
}
