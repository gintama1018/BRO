package com.gintama.novabrowser.shields

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import com.gintama.novabrowser.adblock.AdBlockEngine
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.security.UrlCanonicalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * SiteShieldManager: Centralized coordinator for per-site security, privacy shields,
 * and permissions management.
 *
 * Provides:
 * - O(1) in-memory cache resolution for subresource and navigation checks.
 * - Persistent storage in SQLite (site_shields_settings).
 * - Site-specific data clearing (cookies, local storage, geolocation, hardware permissions).
 */
object SiteShieldManager {

    private val cache = ConcurrentHashMap<String, SiteShieldSettings>()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isInitialized = false
    private lateinit var dbHelper: NovaDatabaseHelper

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        dbHelper = NovaDatabaseHelper.getInstance(appContext)

        scope.launch {
            loadAllRules()
            isInitialized = true
        }
    }

    private fun loadAllRules() {
        val records = dbHelper.getAllSiteShieldRecords()
        cache.putAll(records)

        // Seed from legacy adblock rules if not yet present in site shields
        val legacyRules = dbHelper.getAllSiteAdBlockRules()
        for ((domain, rule) in legacyRules) {
            if (!cache.containsKey(domain)) {
                val migrated = SiteShieldSettings(
                    domain = domain,
                    shieldsEnabled = rule.first,
                    adBlockEnabled = rule.first,
                    cosmeticEnabled = rule.second
                )
                cache[domain] = migrated
                dbHelper.setSiteShieldRecord(migrated)
            }
        }
    }

    /**
     * Normalizes a URL or host into a clean, lowercased domain without "www.".
     */
    fun normalizeDomain(hostOrUrl: String?): String {
        if (hostOrUrl.isNullOrBlank()) return ""
        val candidate = hostOrUrl.trim()
        if (candidate.startsWith("about:", ignoreCase = true) || candidate.equals("local canvas", ignoreCase = true)) {
            return "Local Canvas"
        }
        val host = try {
            if (candidate.contains("://")) {
                URI(candidate).host ?: candidate
            } else {
                candidate.substringBefore("/").substringBefore(":")
            }
        } catch (e: Exception) {
            candidate
        }
        return host.lowercase().removePrefix("www.")
    }

    /**
     * Resolves per-site shield settings in O(1) time.
     * If no rule has been customized, returns the default settings (Shields UP, all protections active).
     */
    fun getSettingsForSite(hostOrUrl: String?): SiteShieldSettings {
        val domain = normalizeDomain(hostOrUrl)
        if (domain.isBlank() || domain.equals("local canvas", ignoreCase = true) || domain.equals("about:blank", ignoreCase = true)) {
            return SiteShieldSettings(domain = "Local Canvas")
        }

        cache[domain]?.let { return it }

        if (::dbHelper.isInitialized) {
            val fromDb = dbHelper.getSiteShieldRecord(domain)
            if (fromDb != null) {
                cache[domain] = fromDb
                return fromDb
            }
        }

        // Return default configuration: all shields active
        val defaultSettings = SiteShieldSettings(domain = domain)
        cache[domain] = defaultSettings
        return defaultSettings
    }

    /**
     * Persists updated shield settings in memory and SQLite.
     */
    fun updateSettings(settings: SiteShieldSettings) {
        val domain = normalizeDomain(settings.domain)
        if (domain.isBlank() || domain.equals("local canvas", ignoreCase = true)) return

        val normalized = settings.copy(domain = domain, updatedAt = System.currentTimeMillis())
        cache[domain] = normalized

        scope.launch {
            if (::dbHelper.isInitialized) {
                dbHelper.setSiteShieldRecord(normalized)
            }
        }

        // Keep legacy AdBlockEngine cache synchronized
        AdBlockEngine.setSiteRule(domain, normalized.isAdBlockActive(), normalized.isCosmeticActive())
    }

    /**
     * Clears cookies, WebStorage (localStorage/IndexedDB), geolocation permissions,
     * and hardware permission grants strictly for the specified site.
     */
    fun clearSiteData(context: Context, siteUrl: String, onComplete: () -> Unit) {
        val canonicalOrigin = UrlCanonicalizer.canonicalOrigin(siteUrl)
        val domain = normalizeDomain(siteUrl)

        scope.launch {
            try {
                if (::dbHelper.isInitialized) {
                    dbHelper.clearSitePermissions(canonicalOrigin)
                    dbHelper.clearSitePermissions(domain)
                }
            } catch (e: Exception) {
                // Ignore DB deletion errors
            }

            runOnMain {
                try {
                    // 1. Delete WebStorage (HTML5 localStorage / Web SQL / IndexedDB)
                    WebStorage.getInstance().deleteOrigin(canonicalOrigin)
                    if (domain.isNotEmpty() && !canonicalOrigin.contains(domain)) {
                        WebStorage.getInstance().deleteOrigin("https://$domain")
                        WebStorage.getInstance().deleteOrigin("http://$domain")
                    }

                    // 2. Clear Geolocation permissions
                    GeolocationPermissions.getInstance().clear(canonicalOrigin)

                    // 3. Expire site cookies
                    val cookieManager = CookieManager.getInstance()
                    val cookieStr = cookieManager.getCookie(siteUrl)
                    if (!cookieStr.isNullOrBlank()) {
                        val cookiePairs = cookieStr.split(";")
                        for (pair in cookiePairs) {
                            val name = pair.substringBefore("=").trim()
                            if (name.isNotEmpty()) {
                                cookieManager.setCookie(siteUrl, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                            }
                        }
                        cookieManager.flush()
                    }
                } catch (e: Exception) {
                    // Suppress webview storage errors
                }

                onComplete()
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        try {
            Handler(Looper.getMainLooper()).post(block)
        } catch (e: Throwable) {
            block()
        }
    }
}
