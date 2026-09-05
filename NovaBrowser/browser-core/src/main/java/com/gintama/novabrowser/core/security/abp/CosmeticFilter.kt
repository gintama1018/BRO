package com.gintama.novabrowser.core.security.abp

/**
 * Cosmetic element hiding filter representation (##selector, domain##selector, domain#@#selector).
 */
data class CosmeticFilter(
    val rawRule: String,
    val selector: String,
    val isException: Boolean,
    val domainConstraint: DomainConstraint?
) {
    /**
     * Checks if this cosmetic rule should be injected on the specified page host.
     */
    fun appliesTo(pageHost: String?): Boolean {
        if (domainConstraint == null) {
            return true // Global cosmetic selector
        }
        if (pageHost == null) return false
        return domainConstraint.matches(pageHost)
    }

    companion object {
        fun parse(line: String): CosmeticFilter? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
                return null
            }

            // Check for exception: #@#
            if (trimmed.contains("#@#")) {
                val domainPart = trimmed.substringBefore("#@#")
                val selectorPart = trimmed.substringAfter("#@#").trim()
                if (selectorPart.isEmpty()) return null
                val domainConstraint = if (domainPart.isNotBlank()) DomainConstraint.parse(domainPart) else null
                return CosmeticFilter(
                    rawRule = trimmed,
                    selector = selectorPart,
                    isException = true,
                    domainConstraint = domainConstraint
                )
            }

            // Standard element hiding: ## or #?#
            val separator = when {
                trimmed.contains("##") -> "##"
                trimmed.contains("#?#") -> "#?#"
                else -> return null
            }

            val domainPart = trimmed.substringBefore(separator)
            val selectorPart = trimmed.substringAfter(separator).trim()
            if (selectorPart.isEmpty()) return null

            val domainConstraint = if (domainPart.isNotBlank()) DomainConstraint.parse(domainPart) else null
            return CosmeticFilter(
                rawRule = trimmed,
                selector = selectorPart,
                isException = false,
                domainConstraint = domainConstraint
            )
        }
    }
}
