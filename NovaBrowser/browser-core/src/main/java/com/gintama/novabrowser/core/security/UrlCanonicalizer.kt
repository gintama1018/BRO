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
 * Hardened RFC 3986 component-wise normalization:
 * - Normalizes scheme, host, userinfo, port, path, query independently.
 * - Decodes percent-encoding strictly on host to prevent delimiter disruption (+ / & / /).
 * - Converts Internationalized Domain Names (IDN) to ASCII Punycode (e.g. Cyrillic рaypal.com -> xn--aypal-uye.com).
 * - Strips embedded basic-auth user credentials.
 */
object UrlCanonicalizer {

    fun canonicalize(inputUrl: String): CanonicalUrl {
        val trimmed = inputUrl.trim()

        return try {
            val preparedUrl = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
            val uri = URI(preparedUrl)
            val scheme = (uri.scheme ?: "https").lowercase()

            var rawHost = uri.host ?: extractHostFallback(preparedUrl)

            // Strip credentials if present (e.g., https://google.com@evil.com)
            if (rawHost.contains("@")) {
                rawHost = rawHost.substringAfterLast("@")
            }

            // Component-wise host normalization:
            // Decodes percent-encoded host characters specifically (e.g. %252e -> %2e -> .) without touching query or path delimiters!
            val hostDecoded = decodeHostPercentEncoding(rawHost).lowercase().trimEnd('.')

            // Convert internationalized domain name to ASCII punycode (e.g. Cyrillic рaypal.com -> xn--aypal-uye.com)
            val asciiHost = try {
                IDN.toASCII(hostDecoded).lowercase()
            } catch (e: Exception) {
                hostDecoded
            }

            // Convert to Unicode representation for human display
            val unicodeHost = try {
                IDN.toUnicode(asciiHost)
            } catch (e: Exception) {
                hostDecoded
            }

            val isPunycode = asciiHost.startsWith("xn--", ignoreCase = true) ||
                    asciiHost.contains(".xn--", ignoreCase = true) ||
                    hostDecoded != asciiHost

            // Normalize default ports
            val port = uri.port
            val effectivePort = when {
                scheme == "http" && port == 80 -> -1
                scheme == "https" && port == 443 -> -1
                else -> port
            }

            val path = if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath
            val query = uri.rawQuery

            val portString = if (effectivePort != -1) ":$effectivePort" else ""
            val queryString = if (!query.isNullOrEmpty()) "?$query" else ""
            val canonicalString = "$scheme://$asciiHost$portString$path$queryString"

            CanonicalUrl(
                rawUrl = inputUrl,
                canonicalUrl = canonicalString,
                scheme = scheme,
                host = asciiHost,
                unicodeHost = unicodeHost,
                port = effectivePort,
                path = path,
                query = query,
                isPunycode = isPunycode
            )
        } catch (e: Exception) {
            // Fallback for non-standard or malformed URLs
            val fallbackHost = extractHostFallback(trimmed).lowercase()
            val hostDecoded = decodeHostPercentEncoding(fallbackHost).trimEnd('.')
            val asciiFallback = try { IDN.toASCII(hostDecoded).lowercase() } catch (_: Exception) { hostDecoded }
            val unicodeFallback = try { IDN.toUnicode(asciiFallback) } catch (_: Exception) { hostDecoded }
            val isPunycode = asciiFallback.startsWith("xn--", ignoreCase = true) || asciiFallback != hostDecoded

            CanonicalUrl(
                rawUrl = inputUrl,
                canonicalUrl = inputUrl,
                scheme = "https",
                host = asciiFallback,
                unicodeHost = unicodeFallback,
                port = -1,
                path = "/",
                query = null,
                isPunycode = isPunycode
            )
        }
    }

    /**
     * Component-specific percent decoding for hostnames only up to 3 passes.
     * Defends against obfuscated dot encoding (%252e -> %2e -> .) without corrupting
     * path slashes or query parameter delimiters (+ or &).
     */
    private fun decodeHostPercentEncoding(host: String, maxPasses: Int = 3): String {
        var current = host
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
