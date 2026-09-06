package com.gintama.novabrowser.security

import com.gintama.novabrowser.core.security.GateAction
import com.gintama.novabrowser.core.security.RiskState
import com.gintama.novabrowser.core.security.SecurityDecision
import com.gintama.novabrowser.ui.SecurityWarningActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityDiagnosticsTest {

    @Test
    fun testSecurityWarningIntentConstantsAreConsistent() {
        assertEquals("extra_target_url", SecurityWarningActivity.EXTRA_TARGET_URL)
        assertEquals("extra_canonical_url", SecurityWarningActivity.EXTRA_CANONICAL_URL)
        assertEquals("extra_action", SecurityWarningActivity.EXTRA_ACTION)
        assertEquals("extra_risk_state", SecurityWarningActivity.EXTRA_RISK_STATE)
        assertEquals("extra_reasons", SecurityWarningActivity.EXTRA_REASONS)
        assertEquals("extra_rule_id", SecurityWarningActivity.EXTRA_RULE_ID)
        assertEquals("extra_feed_source", SecurityWarningActivity.EXTRA_FEED_SOURCE)
        assertEquals("extra_risk_score", SecurityWarningActivity.EXTRA_RISK_SCORE)
    }

    @Test
    fun testSecurityDecisionCarriesThreatMetadata() {
        val decision = SecurityDecision(
            action = GateAction.BLOCK,
            riskState = RiskState.BLOCKED,
            targetUrl = "http://multiplay.at/shaders/Shaders.zip",
            canonicalUrl = "https://multiplay.at/shaders/Shaders.zip",
            reasons = listOf(
                "Exact match in verified threat database (Source: URLHAUS)",
                "Rule pattern: multiplay.at"
            ),
            matchedRuleId = "URLHAUS-3912843",
            feedSource = "URLHAUS",
            riskScore = 1.0
        )

        assertEquals(GateAction.BLOCK, decision.action)
        assertEquals(RiskState.BLOCKED, decision.riskState)
        assertEquals("URLHAUS", decision.feedSource)
        assertEquals("URLHAUS-3912843", decision.matchedRuleId)
        assertEquals("https://multiplay.at/shaders/Shaders.zip", decision.canonicalUrl)
        assertEquals(2, decision.reasons.size)
        assertTrue(decision.reasons.first().contains("verified threat database"))
    }
}
