package com.gintama.novabrowser.core.security

import kotlin.math.log2

data class HeuristicResult(
    val riskScore: Double,
    val suggestedRiskState: RiskState,
    val reasons: List<String>
)

/**
 * Stage 3: Heuristics & Typosquat Analysis
 *
 * Enforces Guardrail #4:
 * Heuristics output a risk score and advisory warning (SUSPICIOUS or HIGH_RISK).
 * Heuristics NEVER act as a sole hard-blocking authority (that is reserved for verified threat feeds).
 */
object HeuristicsEngine {

    private val PROTECTED_BRANDS = listOf(
        "google", "paypal", "github", "microsoft", "apple", "amazon",
        "netflix", "facebook", "instagram", "twitter", "linkedin",
        "dropbox", "bankofamerica", "chase", "wellsfargo", "sbi", "hdfc", "icici"
    )

    private val SUSPICIOUS_TLDS = setOf(
        "xyz", "top", "work", "click", "loan", "cam", "surf", "racing",
        "buzz", "country", "tk", "ml", "ga", "cf", "gq"
    )

    fun evaluate(canonical: CanonicalUrl): HeuristicResult {
        val reasons = mutableListOf<String>()
        var score = 0.0

        val domain = canonical.host
        val unicodeDomain = canonical.unicodeHost
        val parts = domain.split(".")
        val tld = if (parts.size > 1) parts.last() else ""
        val mainDomain = if (parts.size >= 2) parts[parts.size - 2] else domain

        // 1. Check Typosquatting / Homoglyph / Levenshtein against protected brands
        val impersonatedBrand = detectBrandImpersonation(mainDomain, unicodeDomain)
        if (impersonatedBrand != null) {
            score += 0.55
            reasons.add("Brand impersonation indicator detected (Target: $impersonatedBrand)")
        }

        // 2. Check Subdomain Deception (e.g. paypal.com.account-verify.ru)
        if (parts.size > 2) {
            for (brand in PROTECTED_BRANDS) {
                if (domain.contains("$brand.") && !domain.endsWith(".$brand.com") && !domain.equals("$brand.com")) {
                    score += 0.45
                    reasons.add("Deceptive subdomain pattern detected attempting to impersonate $brand")
                    break
                }
            }
        }

        // 3. Shannon Entropy Check (Algorithmic / DGA domain detection)
        val entropy = calculateShannonEntropy(mainDomain)
        if (mainDomain.length >= 10 && entropy >= 3.6) {
            score += 0.30
            reasons.add("High entropy domain name indicating potential algorithmic generation (Entropy: ${String.format("%.2f", entropy)})")
        }

        // 4. Suspicious TLD Association
        if (SUSPICIOUS_TLDS.contains(tld)) {
            score += 0.20
            reasons.add("Domain uses a top-level domain frequently associated with abuse (.$tld)")
        }

        // 5. Punycode Alert
        if (canonical.isPunycode) {
            score += 0.25
            reasons.add("Internationalized domain name (Punycode) detected: ${canonical.unicodeHost}")
        }

        // Normalize score to max 1.0
        val finalScore = score.coerceIn(0.0, 1.0)

        // Assign risk state per Guardrail #4: Heuristics lead to HIGH_RISK or SUSPICIOUS (never pure BLOCK)
        val state = when {
            finalScore >= 0.65 -> RiskState.HIGH_RISK
            finalScore >= 0.35 -> RiskState.SUSPICIOUS
            else -> RiskState.UNKNOWN // Unknown is NEVER labeled safe!
        }

        return HeuristicResult(
            riskScore = finalScore,
            suggestedRiskState = state,
            reasons = reasons
        )
    }

    private fun detectBrandImpersonation(mainDomain: String, unicodeDomain: String): String? {
        val sanitized = mainDomain.replace("-", "").replace("_", "")

        for (brand in PROTECTED_BRANDS) {
            if (sanitized.equals(brand, ignoreCase = true)) continue

            // Normalized character replacement check (e.g. '1' -> 'l', '0' -> 'o')
            val normalized = sanitized
                .replace("1", "l")
                .replace("0", "o")
                .replace("3", "e")
                .replace("5", "s")
                .replace("8", "b")

            if (normalized.equals(brand, ignoreCase = true)) {
                return brand
            }

            // Levenshtein distance: 1 edit distance from brand is high confidence typosquat
            val distance = levenshteinDistance(sanitized, brand)
            if (distance == 1 && brand.length >= 4) {
                return brand
            }
        }
        return null
    }

    private fun calculateShannonEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val frequencyMap = mutableMapOf<Char, Int>()
        for (c in input) {
            frequencyMap[c] = frequencyMap.getOrDefault(c, 0) + 1
        }
        var entropy = 0.0
        val len = input.length.toDouble()
        for (count in frequencyMap.values) {
            val prob = count / len
            entropy -= prob * log2(prob)
        }
        return entropy
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
