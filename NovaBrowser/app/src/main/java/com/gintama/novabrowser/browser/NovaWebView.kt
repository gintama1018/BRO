package com.gintama.novabrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * NovaWebView: Custom configured WebView optimized for speed, memory efficiency,
 * and private browsing isolation.
 */
@SuppressLint("SetJavaScriptEnabled")
class NovaWebView(
    context: Context,
    val tabId: String,
    val isPrivate: Boolean = false
) : WebView(context) {

    init {
        configureSettings()
        setupPrivacyMode()
    }

    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = false

            // Security: Never allow insecure mixed content
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Safe user agent
            defaultMobileUserAgent = userAgentString.replace("; wv", "")
            userAgentString = defaultMobileUserAgent
        }
    }

    private var defaultMobileUserAgent: String = ""
    var isDesktopMode: Boolean = false
        private set

    fun setDesktopMode(enabled: Boolean) {
        if (isDesktopMode == enabled) return
        isDesktopMode = enabled
        if (enabled) {
            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = defaultMobileUserAgent.ifBlank { WebSettings.getDefaultUserAgent(context).replace("; wv", "") }
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        reload()
    }

    private fun setupPrivacyMode() {
        if (isPrivate) {
            settings.apply {
                cacheMode = WebSettings.LOAD_NO_CACHE
                saveFormData = false
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, false)
        } else {
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            val blockThirdParty = com.gintama.novabrowser.adblock.AdBlockEngine.isThirdPartyCookiesBlocked()
            cookieManager.setAcceptThirdPartyCookies(this, !blockThirdParty)
        }
    }

    override fun loadUrl(url: String) {
        if (com.gintama.novabrowser.adblock.AdBlockEngine.isDntEnabled()) {
            val headers = mapOf("DNT" to "1", "Sec-GPC" to "1")
            super.loadUrl(url, headers)
        } else {
            super.loadUrl(url)
        }
    }

    fun cleanUp() {
        stopLoading()
        clearHistory()
        if (isPrivate) {
            clearCache(true)
            clearFormData()
            clearSslPreferences()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
        destroy()
    }
}
