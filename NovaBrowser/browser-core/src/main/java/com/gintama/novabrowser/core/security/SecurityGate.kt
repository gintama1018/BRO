package com.gintama.novabrowser.core.security

import android.content.Context
import com.gintama.novabrowser.core.model.RuleSeverity

/**
 * Risk classification defined in SECURITY.md and DESIGN.md
 */
enum class RiskState {
    KNOWN_SAFE,
    UNKNOWN,
    SUSPICIOUS,
    HIGH_RISK,
    BLOCKED
}

enum class GateAction {
    ALLOW,
    WARN,
    BLOCK
}

data class SecurityDecision(
    val action: GateAction,
    val riskState: RiskState,
    val targetUrl: String,
    val canonicalUrl: String,
    val reasons: List<String> = emptyList(),
    val matchedRuleId: String? = null,
    val feedSource: String? = null,
    val riskScore: Double = 0.0
)

/**
 * Deterministic Security Gate Interface.
 * AI is never the authority. All navigation requests (user, web-redirect, or AI-originated)
 * must pass through this gate.
 */
interface SecurityGate {
    fun evaluate(url: String, isRedirect: Boolean = false): SecurityDecision
    fun getRedirectTracker(): RedirectTracker
}

/**
 * Phase 2 Canonical Implementation: DeterministicSecurityGate
 * Multi-stage pipeline:
 * 1. URL Canonicalization & Punycode decoding
 * 2. Local Threat Feed Matching (URLhaus / EasyList / EasyPrivacy)
 * 3. Heuristics & Typosquat Analysis (Shannon Entropy, Levenshtein distance)
 * 4. Multi-hop Redirect Chain Verification
 */
class DeterministicSecurityGate(
    context: Context? = null,
    private val threatFeedManager: ThreatFeedManager? = if (context != null) ThreatFeedManager(context) else null,
    private val redirectTracker: RedirectTracker = RedirectTracker(),
    private val ruleLookupOverride: ((CanonicalUrl) -> com.gintama.novabrowser.core.model.SecurityRule?)? = null
) : SecurityGate {

    companion object {
        // High-reputation verified domains eligible for KNOWN_SAFE classification
        private val VERIFIED_SAFE_DOMAINS = setOf(
            "google.com", "wikipedia.org", "github.com", "mozilla.org",
            "w3.org", "cloudflare.com", "android.com", "eff.org"
        )
    }

    override fun getRedirectTracker(): RedirectTracker = redirectTracker

    override fun evaluate(url: String, isRedirect: Boolean): SecurityDecision {
        val reasons = mutableListOf<String>()

        // Stage 1: URL Canonicalization & Normalization
        val canonical = UrlCanonicalizer.canonicalize(url)

        // Stage 4: Record Redirect Hop (if part of navigation lineage)
        val redirectAnalysis = if (isRedirect) {
            redirectTracker.recordHop(canonical)
        } else {
            redirectTracker.reset()
            redirectTracker.recordHop(canonical)
        }

        // Stage 2: Local Threat Feed Lookup (URLhaus / EasyList / EasyPrivacy)
        val matchedRule = ruleLookupOverride?.invoke(canonical) ?: threatFeedManager?.lookup(canonical)
        if (matchedRule != null) {
            when (matchedRule.severity) {
                RuleSeverity.BLOCK -> {
                    reasons.add("Exact match in verified threat database (Source: ${matchedRule.source})")
                    reasons.add("Rule pattern: ${matchedRule.pattern}")
                    return SecurityDecision(
                        action = GateAction.BLOCK,
                        riskState = RiskState.BLOCKED,
                        targetUrl = url,
                        canonicalUrl = canonical.canonicalUrl,
                        reasons = reasons,
                        matchedRuleId = "${matchedRule.source}-${matchedRule.id}",
                        feedSource = matchedRule.source,
                        riskScore = 1.0
                    )
                }
                RuleSeverity.WARN -> {
                    reasons.add("Matched suspicious pattern in local security rules (Source: ${matchedRule.source})")
                    return SecurityDecision(
                        action = GateAction.WARN,
                        riskState = RiskState.HIGH_RISK,
                        targetUrl = url,
                        canonicalUrl = canonical.canonicalUrl,
                        reasons = reasons,
                        matchedRuleId = "${matchedRule.source}-${matchedRule.id}",
                        feedSource = matchedRule.source,
                        riskScore = 0.85
                    )
                }
                RuleSeverity.INFO -> {
                    // Trackers/Ads - Log for subresource filtering, do not block top-level navigation
                    reasons.add("Known tracker/ad domain (${matchedRule.source})")
                }
            }
        }

        // Stage 3: Heuristics & Typosquat Analysis
        val heuristic = HeuristicsEngine.evaluate(canonical)
        if (heuristic.reasons.isNotEmpty()) {
            reasons.addAll(heuristic.reasons)
        }

        // Add any redirect chain warnings
        if (redirectAnalysis.warningReasons.isNotEmpty()) {
            reasons.addAll(redirectAnalysis.warningReasons)
        }

        // Evaluate cumulative risk state
        return when {
            // Guardrail #4: Heuristics produce WARN with HIGH_RISK or SUSPICIOUS (never hard BLOCK)
            heuristic.suggestedRiskState == RiskState.HIGH_RISK -> {
                SecurityDecision(
                    action = GateAction.WARN,
                    riskState = RiskState.HIGH_RISK,
                    targetUrl = url,
                    canonicalUrl = canonical.canonicalUrl,
                    reasons = reasons,
                    riskScore = heuristic.riskScore
                )
            }
            heuristic.suggestedRiskState == RiskState.SUSPICIOUS || redirectAnalysis.hasDowngrade -> {
                SecurityDecision(
                    action = GateAction.WARN,
                    riskState = RiskState.SUSPICIOUS,
                    targetUrl = url,
                    canonicalUrl = canonical.canonicalUrl,
                    reasons = reasons,
                    riskScore = heuristic.riskScore
                )
            }
            // Verified safe whitelist domains
            VERIFIED_SAFE_DOMAINS.contains(canonical.host) || VERIFIED_SAFE_DOMAINS.any { canonical.host.endsWith(".$it") } -> {
                SecurityDecision(
                    action = GateAction.ALLOW,
                    riskState = RiskState.KNOWN_SAFE,
                    targetUrl = url,
                    canonicalUrl = canonical.canonicalUrl,
                    reasons = listOf("Verified high-reputation domain"),
                    riskScore = 0.0
                )
            }
            // CORE SPEC RULE: Unclassified domains are UNKNOWN (NEVER labeled KNOWN_SAFE!)
            else -> {
                SecurityDecision(
                    action = GateAction.ALLOW,
                    riskState = RiskState.UNKNOWN,
                    targetUrl = url,
                    canonicalUrl = canonical.canonicalUrl,
                    reasons = if (reasons.isEmpty()) listOf("Unclassified domain (baseline sandboxed navigation)") else reasons,
                    riskScore = heuristic.riskScore
                )
            }
        }
    }
}

/**
 * Passthrough gate retained for explicit testing / baseline profiling.
 */
class PassthroughSecurityGate : SecurityGate {
    private val tracker = RedirectTracker()
    override fun getRedirectTracker(): RedirectTracker = tracker
    override fun evaluate(url: String, isRedirect: Boolean): SecurityDecision {
        return SecurityDecision(
            action = GateAction.ALLOW,
            riskState = RiskState.UNKNOWN,
            targetUrl = url,
            canonicalUrl = url,
            reasons = listOf("Passthrough mode")
        )
    }
}
