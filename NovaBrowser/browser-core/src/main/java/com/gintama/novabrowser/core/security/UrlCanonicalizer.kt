package com.gintama.novabrowser.core.security

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class CanonicalUrl(
    val rawUrl: String,
    val canonicalUrl: String,
    val scheme: String,
    val host: String,
    val unicodeHost: String,
    val port: Int,
    val path: String,
    val query: String?,
    val isPunycode: Boolean
)

/**
 * Stage 1: URL Canonicalization & Normalization
 * Defends against obfuscation, nested percent-encoding, deceptive user-info,
 * and IDN homoglyph evasion before any blocklist lookup or heuristic scoring.
 */
object UrlCanonicalizer {

    fun canonicalize(inputUrl: String): CanonicalUrl {
        val trimmed = inputUrl.trim()
        val decoded = recursivePercentDecode(trimmed)

        return try {
            val uri = URI(decoded)
            val scheme = (uri.scheme ?: "https").lowercase()
            var rawHost = uri.host ?: extractHostFallback(decoded)
            val port = uri.port

            // Strip credentials if present (e.g., https://google.com@evil.com)
            if (rawHost.contains("@")) {
                rawHost = rawHost.substringAfterLast("@")
            }

            // Lowercase and remove trailing dot
            val normalizedHost = rawHost.lowercase().trimEnd('.')

            // Punycode decoding: xn--pypa-4ve.com -> unicode representation
            val unicodeHost = try {
                IDN.toUnicode(normalizedHost)
            } catch (e: Exception) {
                normalizedHost
            }
            val isPunycode = normalizedHost.startsWith("xn--", ignoreCase = true) ||
                    normalizedHost.contains(".xn--", ignoreCase = true)

            // Normalize default ports
            val effectivePort = when {
                scheme == "http" && port == 80 -> -1
                scheme == "https" && port == 443 -> -1
                else -> port
            }

            val path = if (uri.path.isNullOrEmpty()) "/" else uri.path
            val query = uri.query

            val portString = if (effectivePort != -1) ":$effectivePort" else ""
            val queryString = if (!query.isNullOrEmpty()) "?$query" else ""
            val canonicalString = "$scheme://$normalizedHost$portString$path$queryString"

            CanonicalUrl(
                rawUrl = inputUrl,
                canonicalUrl = canonicalString,
                scheme = scheme,
                host = normalizedHost,
                unicodeHost = unicodeHost,
                port = effectivePort,
                path = path,
                query = query,
                isPunycode = isPunycode
            )
        } catch (e: Exception) {
            // Fallback for non-standard or malformed URLs
            val fallbackHost = extractHostFallback(trimmed).lowercase()
            CanonicalUrl(
                rawUrl = inputUrl,
                canonicalUrl = inputUrl,
                scheme = "https",
                host = fallbackHost,
                unicodeHost = fallbackHost,
                port = -1,
                path = "/",
                query = null,
                isPunycode = false
            )
        }
    }

    /**
     * Decodes nested percent-encoding up to 3 passes to prevent double/triple encoding bypasses
     * (e.g. %252e -> %2e -> .).
     */
    private fun recursivePercentDecode(url: String, maxPasses: Int = 3): String {
        var current = url
        for (i in 0 until maxPasses) {
            if (!current.contains("%")) break
            try {
                val decoded = URLDecoder.decode(current, StandardCharsets.UTF_8.name())
                if (decoded == current) break
                current = decoded
            } catch (e: Exception) {
                break
            }
        }
        return current
    }

    private fun extractHostFallback(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val hostPart = withoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        return if (hostPart.contains("@")) hostPart.substringAfterLast("@") else hostPart
    }
}
