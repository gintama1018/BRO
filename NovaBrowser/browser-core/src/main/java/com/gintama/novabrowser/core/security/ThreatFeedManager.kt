package com.gintama.novabrowser.core.security

import android.content.Context
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.RuleSeverity
import com.gintama.novabrowser.core.model.RuleType
import com.gintama.novabrowser.core.model.SecurityRule
import com.gintama.novabrowser.core.model.SnapshotMeta

/**
 * Stage 2: Threat & Reputation Feed Manager
 *
 * Enforces Guardrail #1 (Clear Conceptual Separation):
 * - URLHAUS        -> Malware and active payload distribution domains (Severity: BLOCK)
 * - EASYLIST       -> Ad-serving and intrusive advertising domains (Severity: INFO / BLOCK for subresources)
 * - EASYPRIVACY    -> Telemetry, tracking pixels, and behavioral trackers (Severity: INFO)
 * - LOCAL_HEURISTIC-> Emergency patches and known phishing patterns (Severity: WARN / BLOCK)
 *
 * Enforces Guardrail #3 (Cryptographic Authenticity Model):
 * Production architecture requires:
 * Feed Payload -> Cryptographic Signature Check (Public Key) -> SHA-256 Hash Verification -> Local DB Install.
 */
class ThreatFeedManager(private val context: Context) {

    private val db = NovaDatabaseHelper.getInstance(context)

    init {
        ensureInitialSnapshotSeeded()
    }

    fun lookup(canonical: CanonicalUrl): SecurityRule? {
        return db.findMatchingSecurityRule(canonical.host, canonical.canonicalUrl)
    }

    fun getSnapshotMetadata(source: String): SnapshotMeta? {
        return db.getSnapshotMeta(source)
    }

    fun getTotalActiveRules(): Int {
        return db.getTotalSecurityRuleCount()
    }

    /**
     * Imports filter list rules (e.g. EasyList, EasyPrivacy) using AdblockParser.
     * Returns count of successfully imported rules.
     */
    fun importAdblockRules(ruleLines: List<String>, source: String): Int {
        val rules = mutableListOf<SecurityRule>()
        val now = System.currentTimeMillis()
        for (line in ruleLines) {
            val parsed = AdblockParser.parseLine(line, source) ?: continue
            rules.add(AdblockParser.toSecurityRule(parsed, updatedAt = now))
        }
        if (rules.isNotEmpty()) {
            db.batchInsertSecurityRules(rules)
            db.updateSnapshotMeta(source, rules.size)
        }
        return rules.size
    }

    /**
     * Imports malware rules from URLhaus CSV export lines.
     */
    fun importUrlhausCsv(csvLines: List<String>): Int {
        val rules = mutableListOf<SecurityRule>()
        for (line in csvLines) {
            val rule = AdblockParser.parseUrlhausCsvLine(line) ?: continue
            rules.add(rule)
        }
        if (rules.isNotEmpty()) {
            db.batchInsertSecurityRules(rules)
            db.updateSnapshotMeta("URLHAUS", rules.size)
        }
        return rules.size
    }

    /**
     * Signed Feed Manifest representing verified metadata from feed authority.
     */
    data class SignedFeedManifest(
        val feedSource: String,
        val version: String,
        val expectedSha256Hex: String,
        val signatureHex: String
    )

    /**
     * Verifies manifest cryptographic authenticity using the trusted EC public key.
     * Uses SHA256withECDSA over the canonical payload string.
     */
    fun verifyManifestSignature(manifest: SignedFeedManifest, trustedPublicKeyHex: String): Boolean {
        if (manifest.signatureHex.isBlank() || trustedPublicKeyHex.isBlank()) return false
        return try {
            val signedPayload = "${manifest.feedSource}:${manifest.version}:${manifest.expectedSha256Hex}"
            val keyBytes = hexToByteArray(trustedPublicKeyHex)
            val sigBytes = hexToByteArray(manifest.signatureHex)
            val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val publicKey = keyFactory.generatePublic(keySpec)

            val verifier = java.security.Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(signedPayload.toByteArray(Charsets.UTF_8))
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun hexToByteArray(hex: String): ByteArray {
            val cleanHex = hex.trim().removePrefix("0x")
            require(cleanHex.length % 2 == 0) { "Hex string must have an even length" }
            val result = ByteArray(cleanHex.length / 2)
            for (i in result.indices) {
                val index = i * 2
                result[i] = cleanHex.substring(index, index + 2).toInt(16).toByte()
            }
            return result
        }

        fun byteArrayToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun signManifestPayload(signedPayload: String, privateKey: java.security.PrivateKey): String {
            val signer = java.security.Signature.getInstance("SHA256withECDSA")
            signer.initSign(privateKey)
            signer.update(signedPayload.toByteArray(Charsets.UTF_8))
            return byteArrayToHex(signer.sign())
        }
    }

    /**
     * Validates feed payload integrity using SHA-256 digest before ingestion.
     * Enforces Guardrail #3: Cryptographic Integrity Verification.
     */
    fun verifyFeedIntegrity(payload: ByteArray, expectedSha256Hex: String): Boolean {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload)
            val computedHex = digest.joinToString("") { "%02x".format(it) }
            computedHex.equals(expectedSha256Hex.trim(), ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Atomic Snapshot-Level Staging & Rollback Pipeline:
     * 1. Authenticity check: manifest signature verified against trusted public key.
     * 2. Integrity check: payload SHA-256 hash match.
     * 3. Candidate parsing & coherence validation.
     * 4. Atomic SQLite transaction replace (all-or-nothing).
     * 5. If any step fails: candidate discarded, active snapshot remains untouched.
     */
    fun updateFeedTransactionally(
        manifest: SignedFeedManifest,
        rawPayload: ByteArray,
        trustedPublicKeyHex: String
    ): Result<Int> {
        // Step 1: Signature authenticity
        if (!verifyManifestSignature(manifest, trustedPublicKeyHex)) {
            return Result.failure(SecurityException("Manifest authenticity verification failed: untrusted signature"))
        }

        // Step 2: SHA-256 integrity
        if (!verifyFeedIntegrity(rawPayload, manifest.expectedSha256Hex)) {
            return Result.failure(SecurityException("Feed payload integrity check failed: SHA-256 digest mismatch"))
        }

        // Step 3: Candidate parsing
        val lines = String(rawPayload, Charsets.UTF_8).lines()
        val candidates = mutableListOf<SecurityRule>()
        val now = System.currentTimeMillis()

        if (manifest.feedSource.equals("URLHAUS", ignoreCase = true)) {
            for (line in lines) {
                val rule = AdblockParser.parseUrlhausCsvLine(line) ?: continue
                candidates.add(rule)
            }
        } else {
            for (line in lines) {
                val parsed = AdblockParser.parseLine(line, manifest.feedSource) ?: continue
                candidates.add(AdblockParser.toSecurityRule(parsed, updatedAt = now))
            }
        }

        // Step 4: Validate candidate coherence
        if (candidates.isEmpty()) {
            return Result.failure(IllegalStateException("Parsed candidate ruleset is empty: rejected"))
        }

        // Step 5: Atomic SQLite snapshot replacement
        val success = db.atomicReplaceSnapshot(manifest.feedSource, candidates, manifest.version)
        return if (success) {
            Result.success(candidates.size)
        } else {
            Result.failure(RuntimeException("Database snapshot transaction failed and rolled back"))
        }
    }

    /**
     * Seeds initial curated offline snapshot if database is uninitialized.
     * Guarantees offline navigation protection without requiring initial network connection.
     * Uses standard Adblock Plus (EasyList/EasyPrivacy) and URLhaus syntax via AdblockParser.
     */
    private fun ensureInitialSnapshotSeeded() {
        if (db.getTotalSecurityRuleCount() > 0) return

        val initialRules = mutableListOf<SecurityRule>()
        val now = System.currentTimeMillis()

        // 1. URLhaus High-Confidence Malware Seeds (RuleType.MALWARE, Severity: BLOCK)
        val malwareDomains = listOf(
            "urlhaus-test.openphish.com",
            "testsafebrowsing.appspot.com",
            "malware-traffic-analysis.net",
            "malicious-test-domain.example",
            "drive-by-download-test.org",
            "banking-stealer-sample.net",
            "crypto-drainer-payload.xyz",
            "cozy-stealer.online",
            "discord-drop-payload.com",
            "ledger-live-update.com",
            "redline-stealer-c2.net",
            "metamask-token-claim.top"
        )
        for (m in malwareDomains) {
            initialRules.add(
                SecurityRule(
                    ruleType = RuleType.MALWARE,
                    pattern = m,
                    source = "URLHAUS",
                    severity = RuleSeverity.BLOCK,
                    updatedAt = now
                )
            )
        }

        // 2. EasyPrivacy Trackers — parsed via standard Adblock syntax (||domain^)
        val abpTrackerRules = listOf(
            "||telemetry.bad-tracker.net^",
            "||pixel.behavioral-ads.com^",
            "||tracking.spy-adnetwork.biz^",
            "||fingerprint-collector.xyz^",
            "||analytics.google.com^\$third-party",
            "||hotjar.com^\$third-party",
            "||scorecardresearch.com^",
            "||mixpanel.com^",
            "||segment.io^",
            "||clarity.ms^",
            "||connect.facebook.net^\$third-party",
            "||pixel.quantserve.com^"
        )
        for (rule in abpTrackerRules) {
            val parsed = AdblockParser.parseLine(rule, "EASYPRIVACY") ?: continue
            initialRules.add(AdblockParser.toSecurityRule(parsed, updatedAt = now))
        }

        // 3. EasyList Ad Networks — parsed via standard Adblock syntax (||domain^)
        val abpAdRules = listOf(
            "||adserver.popup-ads.com^",
            "||banners.intrusive-marketing.net^",
            "||doubleclick.net^",
            "||googleadservices.com^",
            "||criteo.com^",
            "||adnxs.com^",
            "||taboola.com^",
            "||outbrain.com^",
            "||popads.net^",
            "||propellerads.com^",
            "||zergnet.com^",
            "||mgid.com^",
            "||adroll.com^"
        )
        for (rule in abpAdRules) {
            val parsed = AdblockParser.parseLine(rule, "EASYLIST") ?: continue
            initialRules.add(AdblockParser.toSecurityRule(parsed, updatedAt = now))
        }

        // 4. Local Phishing & Homoglyph Signatures (RuleType.URL, Severity: WARN)
        val phishingPatterns = listOf(
            "paypa1-security-update.xyz",
            "g00gle-account-recovery.top",
            "github-login-verification.click",
            "apple-id-suspended-security.club",
            "steam-gift-card-community.site"
        )
        for (p in phishingPatterns) {
            initialRules.add(
                SecurityRule(
                    ruleType = RuleType.URL,
                    pattern = p,
                    source = "LOCAL_HEURISTIC",
                    severity = RuleSeverity.WARN,
                    updatedAt = now
                )
            )
        }

        // Bulk insert rules into SQLite with transaction
        db.batchInsertSecurityRules(initialRules)

        // Record metadata
        db.updateSnapshotMeta("URLHAUS", malwareDomains.size)
        db.updateSnapshotMeta("EASYPRIVACY", abpTrackerRules.size)
        db.updateSnapshotMeta("EASYLIST", abpAdRules.size)
        db.updateSnapshotMeta("LOCAL_HEURISTIC", phishingPatterns.size)
    }
}
