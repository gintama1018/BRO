package com.gintama.novabrowser.core.security

import com.gintama.novabrowser.core.model.RuleSeverity
import com.gintama.novabrowser.core.model.RuleType
import com.gintama.novabrowser.core.model.SecurityRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatFeedIngestionTest {

    @Test
    fun testParseDomainRulesCleansAndNormalizes() {
        val sampleLines = sequenceOf(
            "# URLhaus Comment Header",
            "   ",
            "multiplay.at",
            "127.0.0.1  stealer-c2-worker.ru",
            "0.0.0.0 www.crypto-drainer-payload.xyz",
            "# Another comment",
            "evil-botnet.net  "
        )

        val rules = ThreatFeedManager.parseDomainRules(sampleLines, "URLHAUS")

        assertEquals(4, rules.size)

        assertEquals("multiplay.at", rules[0].pattern)
        assertEquals(RuleType.MALWARE, rules[0].ruleType)
        assertEquals(RuleSeverity.BLOCK, rules[0].severity)
        assertEquals("URLHAUS", rules[0].source)

        assertEquals("stealer-c2-worker.ru", rules[1].pattern)
        assertEquals("crypto-drainer-payload.xyz", rules[2].pattern)
        assertEquals("evil-botnet.net", rules[3].pattern)
    }

    @Test
    fun testParseUrlhausLineHandlesBothCsvAndDomainFormats() {
        // 1. Full URLhaus CSV export line
        val csvLine = """"3912843","2026-09-06 11:49:07","https://multiplay.at/shaders/Shaders.zip","online","2026-09-06 11:49:07","malware_download","extension,stealer,zip","https://urlhaus.abuse.ch/url/3912843/","anonymous""""
        val ruleFromCsv = AdblockParser.parseUrlhausLine(csvLine)

        assertNotNull(ruleFromCsv)
        assertEquals("multiplay.at", ruleFromCsv!!.pattern)
        assertEquals(RuleType.MALWARE, ruleFromCsv.ruleType)
        assertEquals(RuleSeverity.BLOCK, ruleFromCsv.severity)
        assertEquals("URLHAUS", ruleFromCsv.source)

        // 2. Plain domain line
        val domainLine = "0x1x2x3.top"
        val ruleFromDomain = AdblockParser.parseUrlhausLine(domainLine)

        assertNotNull(ruleFromDomain)
        assertEquals("0x1x2x3.top", ruleFromDomain!!.pattern)
        assertEquals(RuleType.MALWARE, ruleFromDomain.ruleType)
        assertEquals(RuleSeverity.BLOCK, ruleFromDomain.severity)
        assertEquals("URLHAUS", ruleFromDomain.source)

        // 3. Comments and blanks return null
        assertNull(AdblockParser.parseUrlhausLine("# comment"))
        assertNull(AdblockParser.parseUrlhausLine("   "))
    }

    @Test
    fun testGateBlocksUrlhausDomainAndSubdomains() {
        val rules = listOf(
            SecurityRule(
                id = 101,
                ruleType = RuleType.MALWARE,
                pattern = "multiplay.at",
                source = "URLHAUS",
                severity = RuleSeverity.BLOCK
            ),
            SecurityRule(
                id = 102,
                ruleType = RuleType.MALWARE,
                pattern = "stealer-payload.xyz",
                source = "URLHAUS",
                severity = RuleSeverity.BLOCK
            )
        )

        val gate = DeterministicSecurityGate(
            context = null,
            ruleLookupOverride = { canonical ->
                // Simulate SQLite indexed pattern lookup with parent domain matching
                val host = canonical.host
                val hostParts = host.split(".")
                val candidatePatterns = mutableListOf(host, canonical.canonicalUrl)
                if (hostParts.size > 2) {
                    for (i in 1 until hostParts.size - 1) {
                        candidatePatterns.add(hostParts.subList(i, hostParts.size).joinToString("."))
                    }
                }
                rules.firstOrNull { rule -> candidatePatterns.contains(rule.pattern) }
            }
        )

        // Exact domain match
        val decisionExact = gate.evaluate("https://multiplay.at/shaders/Shaders.zip")
        assertEquals(GateAction.BLOCK, decisionExact.action)
        assertEquals(RiskState.BLOCKED, decisionExact.riskState)
        assertEquals("URLHAUS", decisionExact.feedSource)
        assertEquals("URLHAUS-101", decisionExact.matchedRuleId)
        assertTrue(decisionExact.reasons.any { it.contains("verified threat database") })

        // Subdomain evasion attempt
        val decisionSub = gate.evaluate("https://cdn.dl.multiplay.at/drop.exe")
        assertEquals(GateAction.BLOCK, decisionSub.action)
        assertEquals(RiskState.BLOCKED, decisionSub.riskState)
        assertEquals("URLHAUS", decisionSub.feedSource)

        // Unrelated safe domain is not blocked
        val decisionSafe = gate.evaluate("https://google.com")
        assertEquals(GateAction.ALLOW, decisionSafe.action)
        assertEquals(RiskState.KNOWN_SAFE, decisionSafe.riskState)
    }
}
