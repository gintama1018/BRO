package com.gintama.novabrowser.core.security.abp

import java.util.regex.Pattern

/**
 * Immutable representation of a network filtering rule.
 */
data class NetworkFilter(
    val rawRule: String,
    val pattern: String,
    val isException: Boolean,
    val isDomainAnchor: Boolean,
    val isRegex: Boolean,
    val targetHost: String?,
    val options: FilterOption,
    val source: String
) {
    private val compiledRegex: Pattern? = if (isRegex) {
        try {
            Pattern.compile(pattern)
        } catch (e: Exception) {
            null
        }
    } else null

    /**
     * Evaluates whether this network rule matches the subresource request context.
     */
    fun matches(requestUrl: String, requestHost: String?, pageHost: String?, isThirdParty: Boolean): Boolean {
        // 1. Third-party constraint check
        if (options.isThirdParty && !isThirdParty) {
            return false
        }

        // 2. Domain constraint on calling page ($domain=...)
        if (options.domainConstraint != null && pageHost != null) {
            if (!options.domainConstraint.matches(pageHost)) {
                return false
            }
        }

        // 3. Regex match
        if (isRegex && compiledRegex != null) {
            return compiledRegex.matcher(requestUrl).find()
        }

        // 4. Domain anchor match (||domain.com^)
        if (isDomainAnchor && targetHost != null && requestHost != null) {
            val reqLower = requestHost.lowercase()
            if (reqLower == targetHost || reqLower.endsWith(".$targetHost")) {
                return true
            }
        }

        // 5. Plain substring or URL path pattern
        if (pattern.isNotEmpty() && requestUrl.contains(pattern, ignoreCase = true)) {
            return true
        }

        return false
    }
}
