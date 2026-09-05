package com.gintama.novabrowser.core.security.abp

/**
 * Parsed modifier options for ABP network rules ($third-party, $important, $badfilter, $script, etc.).
 */
data class FilterOption(
    val isThirdParty: Boolean = false,
    val isImportant: Boolean = false,
    val isBadFilter: Boolean = false,
    val resourceTypes: Set<String> = emptySet(),
    val domainConstraint: DomainConstraint? = null
) {
    companion object {
        fun parse(optionsString: String): FilterOption {
            if (optionsString.isBlank()) return FilterOption()

            var thirdParty = false
            var important = false
            var badFilter = false
            val types = mutableSetOf<String>()
            var domainConstraint: DomainConstraint? = null

            val parts = optionsString.split(",")
            for (part in parts) {
                val opt = part.trim().lowercase()
                when {
                    opt == "third-party" || opt == "3p" -> thirdParty = true
                    opt == "important" -> important = true
                    opt == "badfilter" -> badFilter = true
                    opt.startsWith("domain=") -> {
                        val spec = opt.substringAfter("domain=")
                        domainConstraint = DomainConstraint.parse(spec)
                    }
                    opt in listOf("script", "image", "stylesheet", "subdocument", "xmlhttprequest", "websocket", "media", "font", "other") -> {
                        types.add(opt)
                    }
                }
            }

            return FilterOption(
                isThirdParty = thirdParty,
                isImportant = important,
                isBadFilter = badFilter,
                resourceTypes = types,
                domainConstraint = domainConstraint
            )
        }
    }
}
