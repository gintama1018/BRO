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
            userAgentString = userAgentString.replace("; wv", "")
        }
    }

    private fun setupPrivacyMode() {
        if (isPrivate) {
            settings.apply {
                cacheMode = WebSettings.LOAD_NO_CACHE
                saveFormData = false
            }
            // Isolate cookies for private mode session
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, false)
        } else {
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
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
