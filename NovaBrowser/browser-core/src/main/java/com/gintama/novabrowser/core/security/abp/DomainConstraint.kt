package com.gintama.novabrowser.core.security.abp

/**
 * Encapsulates domain-specific filter constraints ($domain=example.com|~sub.example.com).
 */
data class DomainConstraint(
    val includedDomains: Set<String>,
    val excludedDomains: Set<String>
) {
    /**
     * Returns true if the constraint applies to the given page host.
     */
    fun matches(host: String): Boolean {
        val lower = host.lowercase().trim()
        if (lower.isEmpty()) return includedDomains.isEmpty()

        // 1. Explicit exclusions always take precedence (~domain)
        if (isDomainOrParentInSet(lower, excludedDomains)) {
            return false
        }

        // 2. If no inclusions specified, applies to all non-excluded domains
        if (includedDomains.isEmpty()) {
            return true
        }

        // 3. Must match at least one inclusion
        return isDomainOrParentInSet(lower, includedDomains)
    }

    private fun isDomainOrParentInSet(host: String, set: Set<String>): Boolean {
        if (set.contains(host)) return true
        var index = host.indexOf('.')
        while (index != -1 && index < host.length - 1) {
            val parent = host.substring(index + 1)
            if (set.contains(parent)) return true
            index = host.indexOf('.', index + 1)
        }
        return false
    }

    companion object {
        fun parse(domainsSpec: String): DomainConstraint {
            val included = mutableSetOf<String>()
            val excluded = mutableSetOf<String>()
            val tokens = domainsSpec.split("|")
            for (token in tokens) {
                val trimmed = token.trim().lowercase()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("~")) {
                    val d = trimmed.substring(1).trim()
                    if (d.isNotEmpty()) excluded.add(d)
                } else {
                    included.add(trimmed)
                }
            }
            return DomainConstraint(included, excluded)
        }
    }
}
