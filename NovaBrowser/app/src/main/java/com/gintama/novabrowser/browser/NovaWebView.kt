package com.gintama.novabrowser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

import androidx.core.content.ContextCompat
import com.gintama.novabrowser.R

/**
 * NovaWebView: Custom configured WebView optimized for speed, memory efficiency,
 * zero-flicker reload rendering, and private browsing isolation.
 */
@SuppressLint("SetJavaScriptEnabled")
class NovaWebView(
    context: Context,
    val tabId: String,
    val isPrivate: Boolean = false
) : WebView(context) {

    var isForceDarkMode: Boolean = false
        private set

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.canvas_base))
        setLayerType(LAYER_TYPE_HARDWARE, null)
        configureSettings()
        setupPrivacyMode()

        isForceDarkMode = WebDarkThemeManager.isGlobalForceDarkEnabled(context)
        if (isForceDarkMode) {
            WebDarkThemeManager.applyDarkTheme(this, true)
        }
    }

    fun setForceDarkMode(enabled: Boolean) {
        isForceDarkMode = enabled
        WebDarkThemeManager.applyDarkTheme(this, enabled)
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
            allowFileAccess = true
            allowContentAccess = true

            // Web Compatibility: Support OAuth popups and multi-window logins
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true

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

    /**
     * Dynamically configures JavaScript execution and third-party cookie isolation
     * according to per-site shield rules.
     */
    fun applySiteShields(siteHost: String?) {
        val shieldSettings = com.gintama.novabrowser.shields.SiteShieldManager.getSettingsForSite(siteHost)
        settings.javaScriptEnabled = shieldSettings.isJavaScriptAllowed()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val blockThirdParty = if (isPrivate) true else shieldSettings.isThirdPartyCookiesBlocked()
        cookieManager.setAcceptThirdPartyCookies(this, !blockThirdParty)
    }


    override fun loadUrl(url: String) {
        if (com.gintama.novabrowser.adblock.AdBlockEngine.isDntEnabled()) {
            val headers = mapOf("DNT" to "1", "Sec-GPC" to "1")
            super.loadUrl(url, headers)
        } else {
            super.loadUrl(url)
        }
    }

    var onScrollDeltaListener: ((deltaY: Int, scrollY: Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollDeltaListener?.invoke(t - oldt, t)
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
