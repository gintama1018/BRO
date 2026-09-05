package com.gintama.novabrowser.core.security

import com.gintama.novabrowser.core.model.RuleSeverity
import com.gintama.novabrowser.core.model.RuleType
import com.gintama.novabrowser.core.model.SecurityRule

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
    val source: String,
    val severity: RuleSeverity,
    val ruleType: RuleType
)

/**
 * Adblock & Threat Feed Rule Parser
 *
 * Parses standard filter-list syntax (EasyList, EasyPrivacy) and threat intelligence feeds (URLhaus):
 * - Comments: lines starting with '!' or '[' are skipped
 * - Exception filters: lines starting with '@@'
 * - Domain anchor: '||domain.com^' matches domain.com and any subdomain (*.domain.com)
 * - Options: '$third-party', '$script', '$image', '$subdocument'
 * - URLhaus CSV format: extracts malicious URLs and associated threat classifications
 */
object AdblockParser {

    fun parseLine(line: String, source: String = "EASYLIST"): ParsedFilterRule? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
            return null
        }

        // 1. Exception rules: @@||safe.com^
        val isException = trimmed.startsWith("@@")
        val content = if (isException) trimmed.substring(2) else trimmed

        // 2. Options extraction ($third-party, $script, etc.)
        val hasOptions = content.contains("$")
        val rulePart = if (hasOptions) content.substringBefore("$") else content
        val optionsPart = if (hasOptions) content.substringAfter("$").lowercase() else ""

        val isThirdParty = optionsPart.contains("third-party") || optionsPart.contains("3p")

        // 3. Domain anchor check (||domain.com^)
        val isDomainAnchor = rulePart.startsWith("||")
        val stripped = if (isDomainAnchor) rulePart.substring(2) else rulePart

        // Extract clean domain / pattern
        val pattern = stripped.trimEnd('^').trimEnd('*')
        val targetDomain = if (isDomainAnchor) {
            pattern.substringBefore("/").substringBefore("^").substringBefore("*").lowercase()
        } else if (!pattern.contains("/") && pattern.contains(".")) {
            pattern.lowercase()
        } else {
            null
        }

        val ruleType = when (source) {
            "URLHAUS" -> RuleType.MALWARE
            "EASYPRIVACY" -> RuleType.TRACKER
            else -> RuleType.DOMAIN
        }

        val severity = when {
            isException -> RuleSeverity.INFO
            source == "URLHAUS" -> RuleSeverity.BLOCK
            source == "EASYPRIVACY" -> RuleSeverity.INFO // Subresource block
            else -> RuleSeverity.INFO // Subresource block
        }

        return ParsedFilterRule(
            rawRule = trimmed,
            pattern = pattern,
            isException = isException,
            isDomainAnchor = isDomainAnchor,
            targetDomain = targetDomain,
            isThirdPartyOnly = isThirdParty,
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
     * Parses URLhaus CSV export lines:
     * Format: id,dateadded,url,url_status,last_online,threat,tags,urlhaus_link,reporter
     */
    fun parseUrlhausCsvLine(csvLine: String): SecurityRule? {
        val line = csvLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return null

        val tokens = line.split(",")
        if (tokens.size < 3) return null

        val rawUrl = tokens[2].trim().trim('"')
        if (rawUrl.isBlank() || !rawUrl.startsWith("http")) return null

        return SecurityRule(
            id = 0,
            ruleType = RuleType.MALWARE,
            pattern = rawUrl,
            source = "URLHAUS",
            severity = RuleSeverity.BLOCK,
            updatedAt = System.currentTimeMillis()
        )
    }
}
