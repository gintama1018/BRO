package com.gintama.novabrowser.core.security

import com.gintama.novabrowser.core.security.abp.FilterDecision
import com.gintama.novabrowser.core.security.abp.RulePrecedence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockParserTest {

    @Test
    fun testDomainAnchorParsing() {
        val rule = AdblockParser.parseNetworkFilter("||doubleclick.net^\$third-party")
        assertNotNull(rule)
        assertTrue(rule!!.isDomainAnchor)
        assertEquals("doubleclick.net", rule.targetHost)
        assertTrue(rule.options.isThirdParty)

        // Matches doubleclick.net and subdomains on 3rd party
        assertTrue(rule.matches("https://ad.doubleclick.net/pixel", "ad.doubleclick.net", "news.com", isThirdParty = true))
        assertFalse(rule.matches("https://ad.doubleclick.net/pixel", "ad.doubleclick.net", "doubleclick.net", isThirdParty = false))
    }

    @Test
    fun testDomainConstraintInclusionAndExclusion() {
        val rule = AdblockParser.parseNetworkFilter("||analytics.com^\$domain=news.com|~blog.news.com")
        assertNotNull(rule)
        val constraint = rule!!.options.domainConstraint
        assertNotNull(constraint)

        // Included on news.com
        assertTrue(constraint!!.matches("news.com"))
        assertTrue(constraint.matches("sports.news.com"))

        // Excluded on blog.news.com
        assertFalse(constraint.matches("blog.news.com"))
        assertFalse(constraint.matches("sub.blog.news.com"))

        // Excluded on unrelated site
        assertFalse(constraint.matches("other.org"))
    }

    @Test
    fun testCosmeticRuleParsing() {
        val global = AdblockParser.parseCosmeticFilter("##.adsbygoogle")
        assertNotNull(global)
        assertEquals(".adsbygoogle", global!!.selector)
        assertFalse(global.isException)
        assertTrue(global.appliesTo("any-site.com"))

        val siteSpecific = AdblockParser.parseCosmeticFilter("example.com##div[id^='ad_']")
        assertNotNull(siteSpecific)
        assertEquals("div[id^='ad_']", siteSpecific!!.selector)
        assertTrue(siteSpecific.appliesTo("example.com"))
        assertFalse(siteSpecific.appliesTo("other.com"))

        val exception = AdblockParser.parseCosmeticFilter("example.com#@#.ad-banner")
        assertNotNull(exception)
        assertTrue(exception!!.isException)
    }

    @Test
    fun testRulePrecedenceSemantics() {
        // 1. Badfilter disables rule
        val normalRule = AdblockParser.parseNetworkFilter("||tracker.com^")!!
        val badFilter = AdblockParser.parseNetworkFilter("||tracker.com^\$badfilter")!!

        val activeRules = RulePrecedence.filterActiveRules(listOf(normalRule, badFilter))
        assertEquals(0, activeRules.size) // normalRule was neutralized

        // 2. Important Exception beats Important Block
        val importantBlock = AdblockParser.parseNetworkFilter("||cdn.tracker.com^\$important")!!
        val importantException = AdblockParser.parseNetworkFilter("@@||cdn.tracker.com^\$important")!!
        val decision1 = RulePrecedence.evaluate(listOf(importantBlock, importantException))
        assertEquals(FilterDecision.ALLOW, decision1)

        // 3. Important Block beats Normal Exception
        val normalException = AdblockParser.parseNetworkFilter("@@||cdn.tracker.com^")!!
        val decision2 = RulePrecedence.evaluate(listOf(importantBlock, normalException))
        assertEquals(FilterDecision.BLOCK, decision2)

        // 4. Normal Exception beats Normal Block
        val decision3 = RulePrecedence.evaluate(listOf(normalRule, normalException))
        assertEquals(FilterDecision.ALLOW, decision3)
    }
}
