package com.gintama.novabrowser.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicsEngineTest {

    @Test
    fun testTyposquatDetectionForPaypal() {
        val canonical = UrlCanonicalizer.canonicalize("https://paypa1.com/login")
        val result = HeuristicsEngine.evaluate(canonical)

        // Guardrail #4: Must detect as HIGH_RISK or SUSPICIOUS with high score, NOT hard BLOCK
        assertTrue(result.riskScore >= 0.5)
        assertTrue(result.suggestedRiskState == RiskState.HIGH_RISK || result.suggestedRiskState == RiskState.SUSPICIOUS)
        assertTrue(result.reasons.any { it.contains("Brand impersonation", ignoreCase = true) })
    }

    @Test
    fun testDgaHighEntropyDomain() {
        val canonical = UrlCanonicalizer.canonicalize("https://x89dfa78bf23kj.biz/payload")
        val result = HeuristicsEngine.evaluate(canonical)

        assertTrue(result.riskScore > 0.25)
        assertTrue(result.reasons.any { it.contains("entropy", ignoreCase = true) })
    }

    @Test
    fun testSubdomainDeception() {
        val canonical = UrlCanonicalizer.canonicalize("https://paypal.com.account-verify.ru/login")
        val result = HeuristicsEngine.evaluate(canonical)

        assertTrue(result.riskScore > 0.4)
        assertTrue(result.reasons.any { it.contains("Deceptive subdomain", ignoreCase = true) })
    }

    @Test
    fun testUnclassifiedDomainIsNotFlaggedAsHighRisk() {
        val canonical = UrlCanonicalizer.canonicalize("https://random-developer-blog.org/article")
        val result = HeuristicsEngine.evaluate(canonical)

        // Clean unclassified domain must be UNKNOWN, NOT HIGH_RISK
        assertEquals(RiskState.UNKNOWN, result.suggestedRiskState)
        assertNotEquals(RiskState.KNOWN_SAFE, result.suggestedRiskState)
    }
}
