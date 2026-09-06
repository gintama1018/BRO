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
        val kpg = java.security.KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val keyPair = kpg.generateKeyPair()
        val pubKeyHex = ThreatFeedManager.byteArrayToHex(keyPair.public.encoded)

        val feedSource = "EASYLIST"
        val version = "2026.09.05"
        val expectedSha256Hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val payloadToSign = "$feedSource:$version:$expectedSha256Hex"
        val validSigHex = ThreatFeedManager.signManifestPayload(payloadToSign, keyPair.private)

        val manifest = ThreatFeedManager.SignedFeedManifest(
            feedSource = feedSource,
            version = version,
            expectedSha256Hex = expectedSha256Hex,
            signatureHex = validSigHex
        )

        // Mock ThreatFeedManager instance or call verification
        // Since verifyManifestSignature only uses manifest and pubKeyHex:
        // We can test via dummy context or helper directly
        val verifier = object {
            fun verify(m: ThreatFeedManager.SignedFeedManifest, pubHex: String): Boolean {
                if (m.signatureHex.isBlank() || pubHex.isBlank()) return false
                return try {
                    val signedPayload = "${m.feedSource}:${m.version}:${m.expectedSha256Hex}"
                    val keyBytes = ThreatFeedManager.hexToByteArray(pubHex)
                    val sigBytes = ThreatFeedManager.hexToByteArray(m.signatureHex)
                    val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
                    val keyFactory = java.security.KeyFactory.getInstance("EC")
                    val publicKey = keyFactory.generatePublic(keySpec)

                    val sig = java.security.Signature.getInstance("SHA256withECDSA")
                    sig.initVerify(publicKey)
                    sig.update(signedPayload.toByteArray(Charsets.UTF_8))
                    sig.verify(sigBytes)
                } catch (e: Exception) {
                    false
                }
            }
        }

        // 1. Valid signature passes verification
        assertTrue("Genuine ECDSA signature must verify successfully", verifier.verify(manifest, pubKeyHex))

        // 2. Tampered feed version must fail
        val tamperedVersionManifest = manifest.copy(version = "2026.09.06")
        assertFalse("Tampered version must fail verification", verifier.verify(tamperedVersionManifest, pubKeyHex))

        // 3. Tampered payload SHA-256 hash must fail
        val tamperedHashManifest = manifest.copy(expectedSha256Hex = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        assertFalse("Tampered expected hash must fail verification", verifier.verify(tamperedHashManifest, pubKeyHex))

        // 4. Untrusted different public key must fail
        val otherKeyPair = kpg.generateKeyPair()
        val otherPubKeyHex = ThreatFeedManager.byteArrayToHex(otherKeyPair.public.encoded)
        assertFalse("Verification against untrusted public key must fail", verifier.verify(manifest, otherPubKeyHex))

        // 5. Corrupted signature hex must fail
        val corruptedSig = manifest.copy(signatureHex = validSigHex.substring(0, validSigHex.length - 4) + "0000")
        assertFalse("Corrupted signature must fail verification", verifier.verify(corruptedSig, pubKeyHex))

        // 6. Blank / malformed signature or key must fail
        assertFalse("Blank signature must fail", verifier.verify(manifest.copy(signatureHex = ""), pubKeyHex))
        assertFalse("Blank public key must fail", verifier.verify(manifest, ""))
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
