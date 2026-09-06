package com.gintama.novabrowser.core.navigation

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Normalizes and sanitizes user input into valid URLs or search queries.
 */
object UrlSanitizer {

    private const val DEFAULT_SEARCH_ENGINE = "https://www.google.com/search?q="

    private val DOMAIN_PATTERN = Pattern.compile(
        "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$"
    )
    private val IP_PATTERN = Pattern.compile(
        "^(https?://)?(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"
    )
    private val LOCALHOST_PATTERN = Pattern.compile(
        "^(https?://)?localhost(:\\d+)?(/.*)?$"
    )

    fun sanitizeInput(
        input: String,
        searchEngineTemplate: String = SearchEngine.DEFAULT.queryUrlTemplate
    ): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "about:blank"

        // Check if already has a valid scheme
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return trimmed
        }

        // Check if input matches common domain / IP / localhost patterns
        if (DOMAIN_PATTERN.matcher(trimmed).matches() ||
            IP_PATTERN.matcher(trimmed).matches() ||
            LOCALHOST_PATTERN.matcher(trimmed).matches()
        ) {
            return "https://$trimmed"
        }

        // If it contains spaces or lacks a dot, treat as search query using the configured engine
        return SearchEngine.buildSearchUrl(trimmed, searchEngineTemplate)
    }

    fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host
            if (!host.isNullOrEmpty()) {
                if (host.startsWith("www.", ignoreCase = true)) {
                    host.substring(4)
                } else {
                    host
                }
            } else {
                "local"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
