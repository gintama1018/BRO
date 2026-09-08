package com.gintama.novabrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import com.gintama.novabrowser.core.controller.BrowserController
import com.gintama.novabrowser.core.model.TabSession
import com.gintama.novabrowser.downloads.DownloadHandler
import com.gintama.novabrowser.core.security.GateAction
import com.gintama.novabrowser.core.security.SecurityDecision
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var webView: NovaWebView,
    var title: String = "New Tab",
    var url: String = "about:blank",
    val isPrivate: Boolean = false,
    var blockedAdsCount: Int = 0,
    var thumbnail: Bitmap? = null,
    var isMuted: Boolean = false
)

interface TabChangeListener {
    fun onActiveTabChanged(tab: BrowserTab)
    fun onTabsUpdated(tabs: List<BrowserTab>)
    fun onPageProgress(progress: Int)
    fun onPageCommitVisible(tab: BrowserTab) {}
    fun onPageLoadError(tab: BrowserTab, url: String, errorCode: Int, description: String) {}
    fun onRendererRecovered(tab: BrowserTab) {}
    fun onSecurityIntervention(decision: SecurityDecision, targetUrl: String, onProceed: () -> Unit)
    fun onBlockedAdsUpdated(tab: BrowserTab, blockedCount: Int)
    fun onSitePermissionPrompt(
        canonicalOrigin: String,
        permissions: List<String>,
        onAllow: () -> Unit,
        onDeny: () -> Unit
    )
    fun onShowFileChooser(
        filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
        fileChooserParams: android.webkit.WebChromeClient.FileChooserParams?
    ): Boolean
    fun onShowCustomView(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback)
    fun onHideCustomView()
    fun onCreateWindow(isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean
    fun onCloseWindow(window: android.webkit.WebView?)
    fun onJsAlert(message: String, result: android.webkit.JsResult)
    fun onJsConfirm(message: String, result: android.webkit.JsResult)
    fun onJsPrompt(message: String, defaultValue: String, result: android.webkit.JsPromptResult)
    fun onReceivedSslError(error: android.net.http.SslError, onProceed: () -> Unit, onCancel: () -> Unit)
}

/**
 * TabManager: Controls tab lifecycle, tab switching, and state persistence.
 */
class TabManager(
    private val context: Context,
    private val webViewContainer: ViewGroup,
    private val controller: BrowserController,
    private val downloadHandler: DownloadHandler,
    private val listener: TabChangeListener
) {
    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId: String? = null

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    val tabCount: Int
        get() = tabs.size

    fun getTabsList(): List<BrowserTab> = tabs.toList()

    fun getStandardTabs(): List<BrowserTab> = tabs.filter { !it.isPrivate }

    fun getPrivateTabs(): List<BrowserTab> = tabs.filter { it.isPrivate }

    fun captureActiveTabThumbnail() {
        val tab = activeTab ?: return
        try {
            val wv = tab.webView
            if (wv.width > 0 && wv.height > 0) {
                val scaledWidth = 360
                val scaledHeight = ((wv.height.toFloat() / wv.width.toFloat()) * scaledWidth).toInt().coerceIn(240, 640)
                val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(bitmap)
                val scale = scaledWidth.toFloat() / wv.width.toFloat()
                canvas.scale(scale, scale)
                wv.draw(canvas)
                tab.thumbnail?.recycle()
                tab.thumbnail = bitmap
            }
        } catch (e: Exception) {
            // Suppress thumbnail capture errors safely
        }
    }

    fun createTab(initialUrl: String = "about:blank", isPrivate: Boolean = false): BrowserTab {
        val tabId = UUID.randomUUID().toString()
        val webView = NovaWebView(context, tabId, isPrivate).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setDownloadListener(downloadHandler)
        }

        val tab = BrowserTab(
            id = tabId,
            webView = webView,
            url = initialUrl,
            isPrivate = isPrivate
        )

        setupClientsForTab(tab)

        tabs.add(tab)
        switchTab(tab.id)

        if (initialUrl.isNotBlank() && initialUrl != "about:blank") {
            val (sanitized, decision) = controller.evaluateNavigation(initialUrl)
            if (decision.action == GateAction.BLOCK || decision.action == GateAction.WARN) {
                listener.onSecurityIntervention(decision, sanitized) {
                    webView.loadUrl(sanitized)
                }
            } else {
                webView.loadUrl(sanitized)
            }
        }

        listener.onTabsUpdated(tabs)
        saveTabs()
        return tab
    }

    private fun setupClientsForTab(tab: BrowserTab) {
        val webView = tab.webView

        // Setup navigation callbacks for this tab
        val navCallback = object : NavigationCallback {
            override fun onPageStarted(url: String) {
                tab.url = url
                tab.blockedAdsCount = 0
                val siteHost = try { java.net.URI(url).host } catch (e: Exception) { null }
                tab.webView.applySiteShields(siteHost)
                if (tab.id == activeTabId) {
                    listener.onBlockedAdsUpdated(tab, 0)
                    listener.onActiveTabChanged(tab)
                }
            }

            override fun onPageFinished(url: String, title: String?) {
                tab.url = url
                if (!title.isNullOrBlank()) tab.title = title
                controller.onPageVisited(url, title, tab.isPrivate)
                if (tab.id == activeTabId) {
                    listener.onActiveTabChanged(tab)
                }
                saveTabs()
            }

            override fun onProgressChanged(progress: Int) {
                if (tab.id == activeTabId) {
                    listener.onPageProgress(progress)
                }
            }

            override fun onPageCommitVisible(url: String) {
                if (tab.id == activeTabId) {
                    listener.onPageCommitVisible(tab)
                }
            }

            override fun onPageLoadError(url: String, errorCode: Int, description: String) {
                if (tab.id == activeTabId) {
                    listener.onPageLoadError(tab, url, errorCode, description)
                }
            }

            override fun onTitleReceived(title: String) {
                tab.title = title
                if (tab.id == activeTabId) {
                    listener.onActiveTabChanged(tab)
                }
                saveTabs()
            }
        }

        webView.webViewClient = NovaWebViewClient(
            callback = navCallback,
            onUrlOverride = { targetUrl ->
                if (ExternalSchemeHandler.isExternalScheme(targetUrl)) {
                    val (handled, fallbackUrl) = ExternalSchemeHandler.handleExternalUrl(context, targetUrl)
                    if (handled) {
                        if (!fallbackUrl.isNullOrBlank()) {
                            webView.loadUrl(fallbackUrl)
                        }
                        return@NovaWebViewClient true
                    }
                }

                val upgradedUrl = if (com.gintama.novabrowser.adblock.AdBlockEngine.isHttpsOnlyMode() && targetUrl.startsWith("http://", ignoreCase = true)) {
                    targetUrl.replaceFirst("http://", "https://", ignoreCase = true)
                } else {
                    targetUrl
                }

                val (sanitized, decision) = controller.evaluateNavigation(upgradedUrl, isRedirect = true)
                if (decision.action == GateAction.BLOCK || decision.action == GateAction.WARN) {
                    listener.onSecurityIntervention(decision, sanitized) {
                        webView.loadUrl(sanitized)
                    }
                    true
                } else {
                    webView.loadUrl(sanitized)
                    true
                }
            },
            onSubresourceCheck = { subresourceUri ->
                val requestHost = try {
                    java.net.URI(subresourceUri).host?.lowercase()
                } catch (e: Exception) {
                    null
                }

                val currentSiteHost = try {
                    java.net.URI(tab.url).host?.lowercase()
                } catch (e: Exception) {
                    null
                }

                if (requestHost != null &&
                    com.gintama.novabrowser.adblock.AdBlockEngine.isAdBlockEnabledForSite(currentSiteHost) &&
                    com.gintama.novabrowser.adblock.AdBlockEngine.isAdOrTracker(requestHost)
                ) {
                    tab.blockedAdsCount++
                    com.gintama.novabrowser.adblock.AdBlockEngine.recordBlockedAd(1)
                    if (tab.id == activeTabId) {
                        listener.onBlockedAdsUpdated(tab, tab.blockedAdsCount)
                    }
                    true
                } else {
                    val (_, decision) = controller.evaluateNavigation(subresourceUri)
                    decision.action == GateAction.BLOCK
                }
            },
            onRenderProcessGoneCallback = { _, _ ->
                tab.webView.post {
                    recoverDeadTab(tab)
                }
                true
            },
            onReceivedSslErrorCallback = { _, handler, error ->
                if (error != null) {
                    listener.onReceivedSslError(
                        error = error,
                        onProceed = { handler?.proceed() },
                        onCancel = { handler?.cancel() }
                    )
                } else {
                    handler?.cancel()
                }
            }
        )
        val dbHelper = com.gintama.novabrowser.core.db.NovaDatabaseHelper.getInstance(context)

        webView.webChromeClient = NovaWebChromeClient(
            callback = navCallback,
            onPermissionRequestPrompt = { request, canonicalOrigin, resources ->
                val mainFrameOrigin = com.gintama.novabrowser.core.security.UrlCanonicalizer.canonicalOrigin(tab.url)

                if (canonicalOrigin != mainFrameOrigin) {
                    request.deny()
                    return@NovaWebChromeClient
                }

                val mappedPermissions = mutableListOf<String>()
                for (res in resources) {
                    when (res) {
                        android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> mappedPermissions.add(SitePermissionType.MICROPHONE)
                        android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> mappedPermissions.add(SitePermissionType.CAMERA)
                        android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> mappedPermissions.add(SitePermissionType.PROTECTED_MEDIA)
                    }
                }

                if (mappedPermissions.isEmpty()) {
                    request.deny()
                    return@NovaWebChromeClient
                }

                val savedStatus = dbHelper.getSitePermission(canonicalOrigin, mappedPermissions.first())
                if (savedStatus == true) {
                    request.grant(resources.toTypedArray())
                    return@NovaWebChromeClient
                } else if (savedStatus == false) {
                    request.deny()
                    return@NovaWebChromeClient
                }

                listener.onSitePermissionPrompt(
                    canonicalOrigin = canonicalOrigin,
                    permissions = mappedPermissions,
                    onAllow = {
                        for (perm in mappedPermissions) {
                            dbHelper.setSitePermission(canonicalOrigin, perm, true)
                        }
                        request.grant(resources.toTypedArray())
                    },
                    onDeny = {
                        for (perm in mappedPermissions) {
                            dbHelper.setSitePermission(canonicalOrigin, perm, false)
                        }
                        request.deny()
                    }
                )
            },
            onGeolocationPrompt = { origin, canonicalOrigin, callback ->
                val mainFrameOrigin = com.gintama.novabrowser.core.security.UrlCanonicalizer.canonicalOrigin(tab.url)

                if (canonicalOrigin != mainFrameOrigin) {
                    callback.invoke(origin, false, false)
                    return@NovaWebChromeClient
                }

                val savedStatus = dbHelper.getSitePermission(canonicalOrigin, SitePermissionType.GEOLOCATION)
                if (savedStatus == true) {
                    callback.invoke(origin, true, true)
                    return@NovaWebChromeClient
                } else if (savedStatus == false) {
                    callback.invoke(origin, false, true)
                    return@NovaWebChromeClient
                }

                listener.onSitePermissionPrompt(
                    canonicalOrigin = canonicalOrigin,
                    permissions = listOf(SitePermissionType.GEOLOCATION),
                    onAllow = {
                        dbHelper.setSitePermission(canonicalOrigin, SitePermissionType.GEOLOCATION, true)
                        callback.invoke(origin, true, true)
                    },
                    onDeny = {
                        dbHelper.setSitePermission(canonicalOrigin, SitePermissionType.GEOLOCATION, false)
                        callback.invoke(origin, false, true)
                    }
                )
            },
            onShowFileChooserCallback = { filePathCallback, fileChooserParams ->
                listener.onShowFileChooser(filePathCallback, fileChooserParams)
            },
            onShowCustomViewCallback = { view, customViewCallback ->
                listener.onShowCustomView(view, customViewCallback)
            },
            onHideCustomViewCallback = {
                listener.onHideCustomView()
            },
            onCreateWindowCallback = { _, isDialog, isUserGesture, resultMsg ->
                listener.onCreateWindow(isDialog, isUserGesture, resultMsg)
            },
            onCloseWindowCallback = { window ->
                listener.onCloseWindow(window)
            },
            onJsAlertCallback = { _, _, message, result ->
                if (result != null) {
                    listener.onJsAlert(message.orEmpty(), result)
                    true
                } else false
            },
            onJsConfirmCallback = { _, _, message, result ->
                if (result != null) {
                    listener.onJsConfirm(message.orEmpty(), result)
                    true
                } else false
            },
            onJsPromptCallback = { _, _, message, defaultValue, result ->
                if (result != null) {
                    listener.onJsPrompt(message.orEmpty(), defaultValue.orEmpty(), result)
                    true
                } else false
            }
        )
    }

    fun recoverDeadTab(tab: BrowserTab) {
        val lastUrl = tab.url
        try {
            webViewContainer.removeView(tab.webView)
            tab.webView.cleanUp()
        } catch (e: Exception) {
            // Suppress errors cleaning up dead WebView
        }

        val freshWebView = NovaWebView(context, tab.id, tab.isPrivate).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setDownloadListener(downloadHandler)
        }

        tab.webView = freshWebView
        setupClientsForTab(tab)

        if (tab.id == activeTabId) {
            if (freshWebView.parent == null) {
                webViewContainer.addView(freshWebView)
            }
            freshWebView.bringToFront()
            listener.onActiveTabChanged(tab)
        }

        if (lastUrl.isNotBlank() && lastUrl != "about:blank") {
            freshWebView.loadUrl(lastUrl)
        }
        listener.onRendererRecovered(tab)
    }

    fun switchTab(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        if (activeTabId != null && activeTabId != tabId) {
            captureActiveTabThumbnail()
        }
        activeTabId = target.id

        // Zero-flicker swap: attach new view first, bring to front, then remove other views
        if (target.webView.parent == null) {
            webViewContainer.addView(target.webView)
        }
        target.webView.bringToFront()
        for (i in webViewContainer.childCount - 1 downTo 0) {
            val child = webViewContainer.getChildAt(i)
            if (child != target.webView) {
                webViewContainer.removeViewAt(i)
            }
        }

        listener.onActiveTabChanged(target)
        listener.onBlockedAdsUpdated(target, target.blockedAdsCount)
        saveTabs()
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val tabToRemove = tabs.removeAt(index)
        tabToRemove.thumbnail?.recycle()
        tabToRemove.thumbnail = null
        tabToRemove.webView.cleanUp()

        if (tabs.isEmpty()) {
            // Open a fresh tab if all closed
            createTab()
        } else if (activeTabId == tabId) {
            // Switch to previous or adjacent tab
            val nextIndex = (index - 1).coerceAtLeast(0)
            switchTab(tabs[nextIndex].id)
        } else {
            listener.onTabsUpdated(tabs)
            saveTabs()
        }
    }

    fun closeAllTabs(isPrivateOnly: Boolean? = null) {
        val toRemove = when (isPrivateOnly) {
            true -> tabs.filter { it.isPrivate }
            false -> tabs.filter { !it.isPrivate }
            null -> tabs.toList()
        }

        for (tab in toRemove) {
            tab.thumbnail?.recycle()
            tab.thumbnail = null
            tab.webView.cleanUp()
            tabs.remove(tab)
        }

        if (tabs.isEmpty()) {
            createTab("about:blank", isPrivate = false)
        } else if (activeTab == null) {
            switchTab(tabs.last().id)
        } else {
            listener.onTabsUpdated(tabs)
            saveTabs()
        }
    }

    private fun saveTabs() {
        // Privacy Invariant: Private tabs must NEVER be persisted to disk/sessions table!
        val sessions = tabs.filter { !it.isPrivate }.map {
            TabSession(
                tabId = it.id,
                url = it.url,
                title = it.title,
                isPrivate = false
            )
        }
        controller.saveSessions(sessions)
    }
}
