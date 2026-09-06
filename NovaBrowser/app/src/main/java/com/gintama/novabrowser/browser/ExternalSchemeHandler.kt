package com.gintama.novabrowser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handles external URI schemes (tel:, mailto:, sms:, geo:, upi:, market:, intent://)
 * with strict security controls against Android component hijacking.
 */
object ExternalSchemeHandler {

    // Whitelist of supported external URI schemes
    private val ALLOWED_EXTERNAL_SCHEMES = setOf(
        "tel",
        "mailto",
        "sms",
        "geo",
        "upi",
        "market"
    )

    fun isWebScheme(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("about:") ||
                lower.startsWith("file://") ||
                lower.startsWith("javascript:") ||
                lower.startsWith("data:")
    }

    fun isExternalScheme(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.startsWith("intent://", ignoreCase = true)) return true
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex <= 0) return false
        val scheme = trimmed.substring(0, colonIndex).lowercase()
        return ALLOWED_EXTERNAL_SCHEMES.contains(scheme)
    }

    /**
     * Resolves and executes external intent or returns fallback web URL if resolution fails.
     * Returns:
     * - Pair(true, null): Handled externally by an installed app.
     * - Pair(true, fallbackUrl): Not handled externally, but has a safe http/https fallback URL to load.
     * - Pair(true, null): Intercepted external scheme (cannot be loaded as raw web document in WebView).
     * - Pair(false, null): Not an external scheme (standard web URL).
     */
    fun handleExternalUrl(context: Context, url: String): Pair<Boolean, String?> {
        val trimmed = url.trim()

        if (trimmed.startsWith("intent://", ignoreCase = true)) {
            return try {
                val intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
                // Security hardening against Android component hijacking:
                intent.component = null
                intent.selector = null
                intent.addCategory(Intent.CATEGORY_BROWSABLE)

                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                if (resolveInfo != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Pair(true, null)
                } else {
                    // Fallback URL handling: check browser_fallback_url
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (!fallback.isNullOrBlank() && (fallback.startsWith("http://", ignoreCase = true) || fallback.startsWith("https://", ignoreCase = true))) {
                        Pair(true, fallback)
                    } else {
                        Pair(true, null)
                    }
                }
            } catch (e: Exception) {
                Pair(true, null)
            }
        }

        val colonIndex = trimmed.indexOf(':')
        if (colonIndex > 0) {
            val scheme = trimmed.substring(0, colonIndex).lowercase()
            if (ALLOWED_EXTERNAL_SCHEMES.contains(scheme)) {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        context.startActivity(intent)
                    }
                    Pair(true, null)
                } catch (e: Exception) {
                    Pair(true, null)
                }
            }
        }

        return Pair(false, null)
    }
}
