package com.gintama.novabrowser.core.security

import com.gintama.novabrowser.core.model.RuleSeverity
import com.gintama.novabrowser.core.model.RuleType
import com.gintama.novabrowser.core.model.SecurityRule
import com.gintama.novabrowser.core.security.abp.CosmeticFilter
import com.gintama.novabrowser.core.security.abp.FilterOption
import com.gintama.novabrowser.core.security.abp.NetworkFilter

/**
 * Parsed Adblock Plus (ABP) / EasyList / EasyPrivacy rule representation.
 */
data class ParsedFilterRule(
    val rawRule: String,
    val pattern: String,
    val isException: Boolean,
    val isDomainAnchor: Boolean,
    val targetDomain: String?,
    val isThirdPartyOnly: Boolean,
    val isImportant: Boolean = false,
    val isBadFilter: Boolean = false,
    val source: String,
    val severity: RuleSeverity,
    val ruleType: RuleType
)

/**
 * Modular Adblock & Threat Feed Rule Parser
 *
 * Directs filter list parsing across specialized parsers:
 * - Network rules (domain anchors, substring, regex, exceptions)
 * - Cosmetic element hiding rules (##, #@#)
 * - URLhaus threat intelligence CSV export parsing
 */
object AdblockParser {

    /**
     * Parses a cosmetic filter rule (##, #?#, #@#).
     */
    fun parseCosmeticFilter(line: String): CosmeticFilter? {
        return CosmeticFilter.parse(line)
    }

    /**
     * Parses a network filtering rule with full modifier support ($important, $badfilter, $domain=, etc.).
     */
    fun parseNetworkFilter(line: String, source: String = "EASYLIST"): NetworkFilter? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[") || trimmed.startsWith("#")) {
            return null
        }
        if (trimmed.contains("##") || trimmed.contains("#@#") || trimmed.contains("#?#")) {
            return null // Delegate to cosmetic filter
        }

        val isException = trimmed.startsWith("@@")
        val content = if (isException) trimmed.substring(2) else trimmed

        val hasOptions = content.contains("$")
        val rulePart = if (hasOptions) content.substringBefore("$") else content
        val optionsPart = if (hasOptions) content.substringAfter("$") else ""
        val options = FilterOption.parse(optionsPart)

        val isRegex = rulePart.startsWith("/") && rulePart.endsWith("/") && rulePart.length > 2
        val cleanRule = if (isRegex) rulePart.substring(1, rulePart.length - 1) else rulePart

        val isDomainAnchor = cleanRule.startsWith("||")
        val stripped = if (isDomainAnchor) cleanRule.substring(2) else cleanRule

        val pattern = stripped.trimEnd('^').trimEnd('*')
        val targetHost = if (isDomainAnchor) {
            pattern.substringBefore("/").substringBefore("^").substringBefore("*").lowercase()
        } else if (!pattern.contains("/") && pattern.contains(".")) {
            pattern.lowercase()
        } else {
            null
        }

        return NetworkFilter(
            rawRule = trimmed,
            pattern = if (isRegex) cleanRule else pattern,
            isException = isException,
            isDomainAnchor = isDomainAnchor,
            isRegex = isRegex,
            targetHost = targetHost,
            options = options,
            source = source
        )
    }

    /**
     * Legacy & snapshot compatible parser for database persistence.
     */
    fun parseLine(line: String, source: String = "EASYLIST"): ParsedFilterRule? {
        val network = parseNetworkFilter(line, source) ?: return null

        val ruleType = when (source) {
            "URLHAUS" -> RuleType.MALWARE
            "EASYPRIVACY" -> RuleType.TRACKER
            else -> RuleType.DOMAIN
        }

        val severity = when {
            network.isException -> RuleSeverity.INFO
            source == "URLHAUS" -> RuleSeverity.BLOCK
            network.options.isImportant -> RuleSeverity.BLOCK
            else -> RuleSeverity.INFO
        }

        return ParsedFilterRule(
            rawRule = network.rawRule,
            pattern = network.pattern,
            isException = network.isException,
            isDomainAnchor = network.isDomainAnchor,
            targetDomain = network.targetHost,
            isThirdPartyOnly = network.options.isThirdParty,
            isImportant = network.options.isImportant,
            isBadFilter = network.options.isBadFilter,
            source = source,
            severity = severity,
            ruleType = ruleType
        )
    }

    /**
     * Converts a parsed filter rule into a persistent database SecurityRule record.
     */
    fun toSecurityRule(parsed: ParsedFilterRule, id: Long = 0, updatedAt: Long = System.currentTimeMillis()): SecurityRule {
        return SecurityRule(
            id = id,
            ruleType = parsed.ruleType,
            pattern = parsed.targetDomain ?: parsed.pattern,
            source = parsed.source,
            severity = parsed.severity,
            updatedAt = updatedAt
        )
    }

    /**
     * Parses URLhaus threat feed lines (both full CSV export lines and plain domain lists).
     * Format 1 (CSV): id,dateadded,url,url_status,last_online,threat,tags,urlhaus_link,reporter
     * Format 2 (Domain/Host list): domain.tld or 127.0.0.1 domain.tld
     */
    fun parseUrlhausLine(line: String): SecurityRule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return null

        // Format 1: CSV line with quoted or unquoted tokens
        if (trimmed.contains(",")) {
            val tokens = trimmed.split(",")
            if (tokens.size >= 3) {
                val rawUrl = tokens[2].trim().trim('"')
                if (rawUrl.startsWith("http", ignoreCase = true)) {
                    val host = try {
                        java.net.URI(rawUrl).host?.lowercase()
                    } catch (e: Exception) {
                        null
                    }
                    val pattern = host ?: rawUrl
                    return SecurityRule(
                        id = 0,
                        ruleType = RuleType.MALWARE,
                        pattern = pattern,
                        source = "URLHAUS",
                        severity = RuleSeverity.BLOCK,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }

        // Format 2: Domain or host entry
        val domain = trimmed
            .removePrefix("127.0.0.1")
            .removePrefix("0.0.0.0")
            .trim()
            .removePrefix("www.")
            .lowercase()

        if (domain.isNotEmpty() && !domain.contains(" ")) {
            return SecurityRule(
                id = 0,
                ruleType = RuleType.MALWARE,
                pattern = domain,
                source = "URLHAUS",
                severity = RuleSeverity.BLOCK,
                updatedAt = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * Legacy URLhaus CSV parser. Delegates to parseUrlhausLine.
     */
    fun parseUrlhausCsvLine(csvLine: String): SecurityRule? {
        return parseUrlhausLine(csvLine)
    }
}
