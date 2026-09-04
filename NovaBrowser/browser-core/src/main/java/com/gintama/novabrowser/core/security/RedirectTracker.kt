package com.gintama.novabrowser.core.security

data class RedirectHop(
    val url: String,
    val scheme: String,
    val host: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class RedirectChainAnalysis(
    val totalHops: Int,
    val hasDowngrade: Boolean,
    val isExcessiveLength: Boolean,
    val warningReasons: List<String>
)

/**
 * Stage 4: Multi-hop Redirect Chain Protection
 * Tracks navigation lineage per tab/session to detect cloaking, redirect loops,
 * and SSL stripping protocol downgrades (HTTPS -> HTTP).
 */
class RedirectTracker {

    private val hops = mutableListOf<RedirectHop>()

    fun reset() {
        hops.clear()
    }

    fun recordHop(canonical: CanonicalUrl): RedirectChainAnalysis {
        val hop = RedirectHop(
            url = canonical.canonicalUrl,
            scheme = canonical.scheme,
            host = canonical.host
        )
        hops.add(hop)

        val reasons = mutableListOf<String>()
        var hasDowngrade = false

        // Check for protocol downgrade across hops
        for (i in 1 until hops.size) {
            val prev = hops[i - 1]
            val curr = hops[i]
            if (prev.scheme == "https" && curr.scheme == "http") {
                hasDowngrade = true
                reasons.add("Insecure protocol downgrade detected (${prev.host} HTTPS -> ${curr.host} HTTP)")
                break
            }
        }

        // Check for excessive redirect hops (> 4 hops indicates cloaking or redirect looping)
        val isExcessive = hops.size > 4
        if (isExcessive) {
            reasons.add("Suspicious redirect chain length: ${hops.size} hops detected (possible cloaking or traffic redirection)")
        }

        return RedirectChainAnalysis(
            totalHops = hops.size,
            hasDowngrade = hasDowngrade,
            isExcessiveLength = isExcessive,
            warningReasons = reasons
        )
    }

    fun getTraceSummary(): List<String> {
        return hops.map { "${it.scheme}://${it.host}" }
    }
}
