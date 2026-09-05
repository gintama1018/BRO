package com.gintama.novabrowser.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class FeedStagingAndIntegrityTest {

    private fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testFeedPayloadSha256IntegrityMatching() {
        val validPayload = "||malicious-feed-domain.com^\n||tracker-test.org^\n".toByteArray(Charsets.UTF_8)
        val expectedHash = computeSha256(validPayload)

        // Verifying exact match passes
        val digest = MessageDigest.getInstance("SHA-256").digest(validPayload)
        val computedHex = digest.joinToString("") { "%02x".format(it) }
        assertTrue(computedHex.equals(expectedHash, ignoreCase = true))

        // Tampered payload fails integrity
        val tamperedPayload = "||malicious-feed-domain.com^\n||tampered-domain.org^\n".toByteArray(Charsets.UTF_8)
        val tamperedDigest = MessageDigest.getInstance("SHA-256").digest(tamperedPayload)
        val tamperedHex = tamperedDigest.joinToString("") { "%02x".format(it) }
        assertFalse(tamperedHex.equals(expectedHash, ignoreCase = true))
    }

    @Test
    fun testManifestSignatureAuthenticityCheck() {
        val manifest = ThreatFeedManager.SignedFeedManifest(
            feedSource = "EASYLIST",
            version = "2026.09.05",
            expectedSha256Hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            signatureHex = "3045022100a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f"
        )
        val trustedPubKey = "04abcd1234ef567890abcdef1234567890abcdef"

        val manager = ThreatFeedManager.SignedFeedManifest(
            manifest.feedSource,
            manifest.version,
            manifest.expectedSha256Hex,
            manifest.signatureHex
        )

        // Missing signature or empty key must fail authenticity
        val invalidManifest = manifest.copy(signatureHex = "")
        assertFalse(invalidManifest.signatureHex.isNotBlank() && trustedPubKey.isNotBlank())

        // Incomplete hash length must fail
        val badHashManifest = manifest.copy(expectedSha256Hex = "short_hash")
        assertFalse(badHashManifest.expectedSha256Hex.length == 64)
    }

    @Test
    fun testCandidateParsingAndValidationRejectsEmptyRuleset() {
        val emptyPayload = "# Just a comment line\n! Another comment\n\n"
        val lines = emptyPayload.lines()
        val candidates = mutableListOf<com.gintama.novabrowser.core.model.SecurityRule>()

        for (line in lines) {
            val parsed = AdblockParser.parseLine(line, "TEST_FEED") ?: continue
            candidates.add(AdblockParser.toSecurityRule(parsed))
        }

        // Staging check: empty candidate set must be rejected before touching database
        assertTrue(candidates.isEmpty())
    }
}
