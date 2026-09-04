package com.gintama.novabrowser.browser

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

interface NavigationCallback {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String, title: String?)
    fun onProgressChanged(progress: Int)
    fun onTitleReceived(title: String)
}

/**
 * Custom WebViewClient that implements Dual-Layer Security Interception.
 *
 * Layer 1 (Navigation Callback): shouldOverrideUrlLoading()
 * Acts as the primary security gate for top-level address transitions.
 *
 * Layer 2 (Complementary Resource Filtering): shouldInterceptRequest()
 * Enforces Guardrail #2:
 * This is a complementary defense layer for filtering secondary network subresources
 * (such as ad beacons and known tracker scripts), NOT an absolute universal security guarantee.
 */
class NovaWebViewClient(
    private val callback: NavigationCallback,
    private val onUrlOverride: (String) -> Boolean,
    private val onSubresourceCheck: ((String) -> Boolean)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val targetUrl = request?.url?.toString() ?: return false
        return onUrlOverride(targetUrl)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val subresourceUri = request?.url?.toString() ?: return null

        // Check if subresource should be filtered (e.g. tracker/malware asset)
        if (onSubresourceCheck?.invoke(subresourceUri) == true) {
            // Drop the subresource cleanly with empty response
            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            callback.onPageStarted(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            callback.onPageFinished(url, view?.title)
        }
    }
}

/**
 * Custom WebChromeClient reporting real-time page progress and page title.
 */
class NovaWebChromeClient(
    private val callback: NavigationCallback
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callback.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        if (!title.isNullOrBlank()) {
            callback.onTitleReceived(title)
        }
    }
}
