package com.gintama.novabrowser.core.security.abp

/**
 * Evaluates ABP rule precedence and decision resolution according to the specification:
 * 1. Badfilter ($badfilter) disables matching target rule.
 * 2. Important exception (@@...$important) overrides even important blocking rules.
 * 3. Important blocking rule ($important) overrides normal exceptions (@@...).
 * 4. Normal exception (@@...) overrides normal blocking rules.
 * 5. Normal blocking rules block the request.
 */
enum class FilterDecision {
    ALLOW,
    BLOCK,
    NO_MATCH
}

object RulePrecedence {

    /**
     * Filters out rules disabled by $badfilter directives.
     */
    fun filterActiveRules(rules: List<NetworkFilter>): List<NetworkFilter> {
        val badFilterSignatures = rules.filter { it.options.isBadFilter }
            .map { it.pattern to it.isException }
            .toSet()

        if (badFilterSignatures.isEmpty()) return rules

        return rules.filter { rule ->
            !rule.options.isBadFilter && !badFilterSignatures.contains(rule.pattern to rule.isException)
        }
    }

    /**
     * Resolves matching candidate filters down to an authoritative decision.
     */
    fun evaluate(matchingFilters: List<NetworkFilter>): FilterDecision {
        if (matchingFilters.isEmpty()) return FilterDecision.NO_MATCH

        // 1. Highest Priority: Important Exception
        if (matchingFilters.any { it.isException && it.options.isImportant }) {
            return FilterDecision.ALLOW
        }

        // 2. High Priority: Important Block
        if (matchingFilters.any { !it.isException && it.options.isImportant }) {
            return FilterDecision.BLOCK
        }

        // 3. Medium Priority: Normal Exception
        if (matchingFilters.any { it.isException }) {
            return FilterDecision.ALLOW
        }

        // 4. Baseline Priority: Normal Block
        if (matchingFilters.any { !it.isException }) {
            return FilterDecision.BLOCK
        }

        return FilterDecision.NO_MATCH
    }
}
