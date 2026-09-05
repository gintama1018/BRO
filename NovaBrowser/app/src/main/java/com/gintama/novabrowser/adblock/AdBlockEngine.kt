package com.gintama.novabrowser.adblock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.WebView
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * High-Performance Local Ad & Tracker Blocking Engine (Phase 1 & Phase 2).
 *
 * Architecture:
 * - O(labels) subresource domain matching using HashSet of normalized domains.
 * - Pre-batched CSS cosmetic element hiding injected on onPageFinished().
 * - Per-site enable/disable allowlisting stored in NovaDatabaseHelper.
 * - Master switches and persistent lifetime blocked counters.
 */
object AdBlockEngine {
    private const val TAG = "AdBlockEngine"
    private const val PREFS_NAME = "novabrowser_adblock_prefs"
    private const val KEY_MASTER_ENABLED = "pref_adblock_master_enabled"
    private const val KEY_COSMETIC_ENABLED = "pref_cosmetic_master_enabled"
    private const val KEY_THIRD_PARTY_COOKIES = "pref_third_party_cookies_blocked"
    private const val KEY_HTTPS_ONLY = "pref_https_only_mode"
    private const val KEY_DNT_ENABLED = "pref_dnt_enabled"
    private const val KEY_LIFETIME_BLOCKED = "pref_lifetime_blocked_ads"

    private val blocklistSet = HashSet<String>(16384)
    private val cosmeticBatches = mutableListOf<String>()
    private val siteRulesCache = ConcurrentHashMap<String, Pair<Boolean, Boolean>>()
    private val lifetimeCounter = AtomicLong(0)

    @Volatile
    private var isInitialized = false
    private lateinit var prefs: SharedPreferences
    private lateinit var db: NovaDatabaseHelper
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        db = NovaDatabaseHelper.getInstance(appContext)
        lifetimeCounter.set(prefs.getLong(KEY_LIFETIME_BLOCKED, 0L))

        // Initialize dedicated CosmeticEngine (clean separation of DOM vs network concerns)
        CosmeticEngine.init(appContext)

        scope.launch {
            loadBlocklist(appContext)
            loadCosmeticSelectors(appContext)
            loadSiteRules()
            isInitialized = true
            Log.d(TAG, "AdBlockEngine initialized with ${blocklistSet.size} domains and ${cosmeticBatches.size} cosmetic batches.")
        }
    }

    private fun loadBlocklist(context: Context) {
        try {
            context.assets.open("blocklist_domains.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim().lowercase().removePrefix("www.")
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            blocklistSet.add(trimmed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blocklist_domains.txt", e)
        }
    }

    private fun loadCosmeticSelectors(context: Context) {
        try {
            val selectors = mutableListOf<String>()
            context.assets.open("cosmetic_selectors.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                            selectors.add(trimmed)
                        }
                    }
                }
            }

            // Batch selectors in chunks of 150 to keep single CSS rule lengths optimal
            cosmeticBatches.clear()
            selectors.chunked(150).forEach { batch ->
                val css = "${batch.joinToString(",")}{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;}"
                cosmeticBatches.add(css)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cosmetic_selectors.txt", e)
        }
    }

    private fun loadSiteRules() {
        val rules = db.getAllSiteAdBlockRules()
        siteRulesCache.putAll(rules)
    }

    // ==========================================
    // Fast O(labels) Matching
    // ==========================================

    /**
     * Checks if a request host belongs to an ad network or tracker.
     * Complexity: O(k) where k is the number of domain segments (typically 2-4),
     * requiring at most 4 HashSet lookups per subresource request.
     */
    fun isAdOrTracker(requestHost: String?): Boolean {
        if (!isMasterAdBlockEnabled()) return false
        if (requestHost.isNullOrBlank()) return false

        var candidate = requestHost.trim().lowercase().trimEnd('.')
        if (candidate.startsWith("www.")) {
            candidate = candidate.substring(4)
        }

        // Never block content CDNs like googlevideo.com where video playback stream is hosted
        if (candidate.endsWith("googlevideo.com")) return false

        // Direct exact match
        if (blocklistSet.contains(candidate)) return true

        // Check parent domains (e.g. ad.doubleclick.net -> doubleclick.net)
        val parts = candidate.split(".")
        if (parts.size > 2) {
            for (i in 1 until parts.size - 1) {
                val parentDomain = parts.subList(i, parts.size).joinToString(".")
                if (blocklistSet.contains(parentDomain)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Checks if ad-blocking is enabled for the specific top-level site.
     */
    fun isAdBlockEnabledForSite(siteHost: String?): Boolean {
        if (!isMasterAdBlockEnabled()) return false
        if (siteHost.isNullOrBlank()) return true

        val clean = siteHost.lowercase().trim().removePrefix("www.")
        val rule = siteRulesCache[clean] ?: db.getSiteAdBlockRule(clean)
        return rule?.first ?: true
    }

    /**
     * Checks if cosmetic element hiding is enabled for the specific top-level site.
     */
    fun isCosmeticEnabledForSite(siteHost: String?): Boolean {
        if (!isMasterCosmeticEnabled()) return false
        if (siteHost.isNullOrBlank()) return true

        val clean = siteHost.lowercase().trim().removePrefix("www.")
        val rule = siteRulesCache[clean] ?: db.getSiteAdBlockRule(clean)
        return rule?.second ?: true
    }

    fun setSiteRule(siteHost: String, adBlockEnabled: Boolean, cosmeticEnabled: Boolean) {
        val clean = siteHost.lowercase().trim().removePrefix("www.")
        siteRulesCache[clean] = Pair(adBlockEnabled, cosmeticEnabled)
        scope.launch {
            db.setSiteAdBlockRule(clean, adBlockEnabled, cosmeticEnabled)
        }
    }

    fun getSiteRule(siteHost: String): Pair<Boolean, Boolean> {
        val clean = siteHost.lowercase().trim().removePrefix("www.")
        return siteRulesCache[clean] ?: db.getSiteAdBlockRule(clean) ?: Pair(true, true)
    }

    // ==========================================
    // Cosmetic CSS Injection (Phase 2)
    // Delegated to CosmeticEngine for clean architectural separation
    // ==========================================

    fun injectCosmeticFilters(webView: WebView, siteHost: String?) {
        CosmeticEngine.injectCosmeticFilters(webView, siteHost)
    }

    // ==========================================
    // Counters & Stats (Phase 4)
    // ==========================================

    fun getBlocklistCount(): Int = blocklistSet.size

    fun getCosmeticBatchCount(): Int = CosmeticEngine.getBatchCount()

    fun recordBlockedAd(count: Int = 1) {
        val newLifetime = lifetimeCounter.addAndGet(count.toLong())
        scope.launch {
            prefs.edit().putLong(KEY_LIFETIME_BLOCKED, newLifetime).apply()
        }
    }

    fun getLifetimeBlockedCount(): Long = lifetimeCounter.get()

    // ==========================================
    // Master Settings & Privacy Preferences (Phase 4 & 5)
    // ==========================================

    fun isMasterAdBlockEnabled(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_MASTER_ENABLED, true) else true
    }

    fun setMasterAdBlockEnabled(enabled: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        }
    }

    fun isMasterCosmeticEnabled(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_COSMETIC_ENABLED, true) else true
    }

    fun setMasterCosmeticEnabled(enabled: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_COSMETIC_ENABLED, enabled).apply()
        }
    }

    fun isThirdPartyCookiesBlocked(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_THIRD_PARTY_COOKIES, true) else true
    }

    fun setThirdPartyCookiesBlocked(blocked: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_THIRD_PARTY_COOKIES, blocked).apply()
        }
    }

    fun isHttpsOnlyMode(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_HTTPS_ONLY, false) else false
    }

    fun setHttpsOnlyMode(enabled: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_HTTPS_ONLY, enabled).apply()
        }
    }

    fun isDntEnabled(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_DNT_ENABLED, true) else true
    }

    fun setDntEnabled(enabled: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_DNT_ENABLED, enabled).apply()
        }
    }
}
