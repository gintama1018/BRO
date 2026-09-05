package com.gintama.novabrowser.adblock

import android.content.Context
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * CosmeticEngine: Dedicated DOM Element-Hiding Engine.
 *
 * Architectural Boundary:
 * - SecurityGate: Pure Network Security (Threat Feeds, Heuristics, Typosquatting, Protocol Downgrade).
 * - AdBlockEngine: Pure Network Subresource Interception (URL filtering in shouldInterceptRequest).
 * - CosmeticEngine: Pure DOM Element Hiding (CSS rules and JS injection on onPageFinished).
 *
 * Keeping Cosmetic Filtering strictly separate ensures network security decisions are NEVER
 * conflated with or polluted by DOM-level stylistic operations.
 */
object CosmeticEngine {
    private const val TAG = "CosmeticEngine"
    private val cosmeticBatches = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        scope.launch {
            loadCosmeticSelectors(appContext)
            isInitialized = true
            Log.d(TAG, "CosmeticEngine initialized with ${cosmeticBatches.size} CSS batches.")
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

    fun getBatchCount(): Int = cosmeticBatches.size

    fun injectCosmeticFilters(webView: WebView, siteHost: String?) {
        if (!AdBlockEngine.isMasterCosmeticEnabled()) return
        if (siteHost != null && !AdBlockEngine.isCosmeticEnabledForSite(siteHost)) return
        if (cosmeticBatches.isEmpty()) return

        for (batchCss in cosmeticBatches) {
            val safeCss = batchCss.replace("`", "\\`").replace("\\", "\\\\")
            val js = """
                (function() {
                    try {
                        var style = document.getElementById('nova-adblock-cosmetic');
                        if (!style) {
                            style = document.createElement('style');
                            style.id = 'nova-adblock-cosmetic';
                            style.type = 'text/css';
                            (document.head || document.documentElement).appendChild(style);
                        }
                        style.textContent += `$safeCss`;
                    } catch(e) {}
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }
}
