package com.gintama.novabrowser.core.security

import com.gintama.novabrowser.core.model.RuleSeverity
import com.gintama.novabrowser.core.model.RuleType
import com.gintama.novabrowser.core.model.SecurityRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityGateTest {

    private fun createGateWithMockRules(rules: List<SecurityRule>): DeterministicSecurityGate {
        return DeterministicSecurityGate(
            context = null,
            ruleLookupOverride = { canonical ->
                rules.firstOrNull { it.pattern.equals(canonical.host, ignoreCase = true) || canonical.canonicalUrl.contains(it.pattern, ignoreCase = true) }
            }
        )
    }

    @Test
    fun testKnownMalwareTriggersBlock() {
        val malwareRule = SecurityRule(
            id = 1,
            ruleType = RuleType.MALWARE,
            pattern = "malicious-stealer.example",
            source = "URLHAUS",
            severity = RuleSeverity.BLOCK
        )
        val gate = createGateWithMockRules(listOf(malwareRule))

        val decision = gate.evaluate("https://malicious-stealer.example/payload.exe")
        assertEquals(GateAction.BLOCK, decision.action)
        assertEquals(RiskState.BLOCKED, decision.riskState)
        assertEquals("URLHAUS", decision.feedSource)
        assertTrue(decision.reasons.any { it.contains("verified threat database", ignoreCase = true) })
    }

    @Test
    fun testTyposquatHeuristicYieldsWarningNotHardBlock() {
        // Enforces Guardrail #4: Heuristics must warn (HIGH_RISK or SUSPICIOUS), NOT hard BLOCK
        val gate = createGateWithMockRules(emptyList())

        val decision = gate.evaluate("https://paypa1.com/login")
        assertEquals(GateAction.WARN, decision.action)
        assertTrue(decision.riskState == RiskState.HIGH_RISK || decision.riskState == RiskState.SUSPICIOUS)
        assertNotEquals(GateAction.BLOCK, decision.action)
        assertNotEquals(RiskState.BLOCKED, decision.riskState)
    }

    @Test
    fun testUnclassifiedDomainIsUnknownNeverKnownSafe() {
        // Enforces Core Spec Axiom: UNKNOWN != SAFE
        val gate = createGateWithMockRules(emptyList())

        val decision = gate.evaluate("https://unknown-personal-portfolio.org/about")
        assertEquals(GateAction.ALLOW, decision.action)
        assertEquals(RiskState.UNKNOWN, decision.riskState)

        // MANDATORY CHECK: UNKNOWN must NEVER be labeled KNOWN_SAFE
        assertNotEquals(RiskState.KNOWN_SAFE, decision.riskState)
    }

    @Test
    fun testVerifiedWhitelistDomainIsKnownSafe() {
        val gate = createGateWithMockRules(emptyList())

        val decision = gate.evaluate("https://en.wikipedia.org/wiki/Computer_security")
        assertEquals(GateAction.ALLOW, decision.action)
        assertEquals(RiskState.KNOWN_SAFE, decision.riskState)
    }

    @Test
    fun testAiOriginatedNavigationGetsZeroPrivilege() {
        // Enforces Guardrail #5: AI-originated navigation is subject to the exact same gate
        val malwareRule = SecurityRule(
            id = 2,
            ruleType = RuleType.MALWARE,
            pattern = "urlhaus-test.openphish.com",
            source = "URLHAUS",
            severity = RuleSeverity.BLOCK
        )
        val gate = createGateWithMockRules(listOf(malwareRule))

        // Simulate AI tool calling open_url("https://urlhaus-test.openphish.com")
        val aiRequestedUrl = "https://urlhaus-test.openphish.com/auth"
        val decision = gate.evaluate(aiRequestedUrl)

        assertEquals(GateAction.BLOCK, decision.action)
        assertEquals(RiskState.BLOCKED, decision.riskState)
    }

    @Test
    fun testExplicitPlainHttpYieldsWarning() {
        // Enforces Objective #2: Plain HTTP is never silently treated as UNKNOWN/ALLOW
        val gate = createGateWithMockRules(emptyList())

        val decision = gate.evaluate("http://example-insecure.com/login")
        assertEquals(GateAction.WARN, decision.action)
        assertEquals(RiskState.SUSPICIOUS, decision.riskState)
        assertTrue(decision.reasons.any { it.contains("unencrypted", ignoreCase = true) })
    }
}
