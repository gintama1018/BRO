package com.gintama.novabrowser.core.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Supported search engines for NovaBrowser.
 * Privacy-preserving search engines are placed first per security tenets.
 */
enum class SearchEngine(
    val id: String,
    val displayName: String,
    val queryUrlTemplate: String,
    val description: String
) {
    DUCKDUCKGO(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        queryUrlTemplate = "https://duckduckgo.com/?q=%s",
        description = "Privacy-first search, tracker blocking"
    ),
    BRAVE(
        id = "brave",
        displayName = "Brave Search",
        queryUrlTemplate = "https://search.brave.com/search?q=%s",
        description = "Independent index, zero profiling"
    ),
    GOOGLE(
        id = "google",
        displayName = "Google",
        queryUrlTemplate = "https://www.google.com/search?q=%s",
        description = "Comprehensive global web search"
    ),
    BING(
        id = "bing",
        displayName = "Microsoft Bing",
        queryUrlTemplate = "https://www.bing.com/search?q=%s",
        description = "Microsoft search engine"
    ),
    STARTPAGE(
        id = "startpage",
        displayName = "Startpage",
        queryUrlTemplate = "https://www.startpage.com/sp/search?query=%s",
        description = "Google search results with total privacy"
    ),
    ECOSIA(
        id = "ecosia",
        displayName = "Ecosia",
        queryUrlTemplate = "https://www.ecosia.org/search?q=%s",
        description = "Tree-planting environmental search"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom Provider",
        queryUrlTemplate = "https://duckduckgo.com/?q=%s",
        description = "User-defined search URL template (%s)"
    );

    companion object {
        val DEFAULT = DUCKDUCKGO

        fun fromId(id: String?): SearchEngine {
            if (id.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }

        fun buildSearchUrl(query: String, template: String): String {
            val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
            return if (template.contains("%s")) {
                template.replace("%s", encodedQuery)
            } else if (template.endsWith("=") || template.endsWith("/")) {
                "$template$encodedQuery"
            } else {
                "$template?q=$encodedQuery"
            }
        }
    }
}
