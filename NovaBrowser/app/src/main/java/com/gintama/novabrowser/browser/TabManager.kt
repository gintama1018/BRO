package com.gintama.novabrowser.browser

import android.content.Context
import android.view.ViewGroup
import com.gintama.novabrowser.core.controller.BrowserController
import com.gintama.novabrowser.core.model.TabSession
import com.gintama.novabrowser.downloads.DownloadHandler
import com.gintama.novabrowser.core.security.GateAction
import com.gintama.novabrowser.core.security.SecurityDecision
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val webView: NovaWebView,
    var title: String = "New Tab",
    var url: String = "about:blank",
    val isPrivate: Boolean = false,
    var blockedAdsCount: Int = 0
)

interface TabChangeListener {
    fun onActiveTabChanged(tab: BrowserTab)
    fun onTabsUpdated(tabs: List<BrowserTab>)
    fun onPageProgress(progress: Int)
    fun onSecurityIntervention(decision: SecurityDecision, targetUrl: String, onProceed: () -> Unit)
    fun onBlockedAdsUpdated(tab: BrowserTab, blockedCount: Int)
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

        // Setup navigation callbacks for this tab
        val navCallback = object : NavigationCallback {
            override fun onPageStarted(url: String) {
                tab.url = url
                tab.blockedAdsCount = 0
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

                // Phase 1: Fast O(labels) check FIRST before heavy security gate
                if (requestHost != null &&
                    com.gintama.novabrowser.adblock.AdBlockEngine.isAdBlockEnabledForSite(currentSiteHost) &&
                    com.gintama.novabrowser.adblock.AdBlockEngine.isAdOrTracker(requestHost)
                ) {
                    tab.blockedAdsCount++
                    com.gintama.novabrowser.adblock.AdBlockEngine.recordBlockedAd(1)
                    if (tab.id == activeTabId) {
                        listener.onBlockedAdsUpdated(tab, tab.blockedAdsCount)
                    }
                    true // Drop resource immediately!
                } else {
                    // Fallback to threat feed / malware gate only for non-ad requests
                    val (_, decision) = controller.evaluateNavigation(subresourceUri)
                    decision.action == GateAction.BLOCK
                }
            }
        )
        webView.webChromeClient = NovaWebChromeClient(navCallback)

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

    fun switchTab(tabId: String) {
        val target = tabs.firstOrNull { it.id == tabId } ?: return
        activeTabId = target.id

        // Swap view in container
        webViewContainer.removeAllViews()
        webViewContainer.addView(target.webView)

        listener.onActiveTabChanged(target)
        listener.onBlockedAdsUpdated(target, target.blockedAdsCount)
        saveTabs()
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val tabToRemove = tabs.removeAt(index)
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

    fun closeAllTabs() {
        for (tab in tabs) {
            tab.webView.cleanUp()
        }
        tabs.clear()
        createTab("about:blank", isPrivate = false)
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
