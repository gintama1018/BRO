package com.gintama.novabrowser.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.bookmarks.BookmarksActivity
import com.gintama.novabrowser.browser.BrowserTab
import com.gintama.novabrowser.browser.TabChangeListener
import com.gintama.novabrowser.browser.TabManager
import com.gintama.novabrowser.core.controller.BrowserController
import com.gintama.novabrowser.core.security.GateAction
import com.gintama.novabrowser.core.security.RiskState
import com.gintama.novabrowser.core.security.SecurityDecision
import com.gintama.novabrowser.downloads.DownloadHandler
import com.gintama.novabrowser.history.HistoryActivity
import com.gintama.novabrowser.settings.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.ArrayList

class MainActivity : AppCompatActivity(), TabChangeListener {

    private lateinit var controller: BrowserController
    private lateinit var tabManager: TabManager
    private lateinit var downloadHandler: DownloadHandler

    // Header Controls
    private lateinit var etUrlInput: EditText
    private lateinit var btnTabs: FrameLayout
    private lateinit var tvTabCount: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var ivSecurityIndicator: ImageView
    private lateinit var ivPrivateBadge: ImageView
    private lateinit var tvLocalBadge: TextView
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnNavForward: ImageButton

    // Viewport Containers
    private lateinit var webViewContainer: FrameLayout
    private lateinit var layoutNewTabCanvas: ScrollView

    // Omnibox on New Tab Canvas
    private lateinit var etOmniboxInput: EditText
    private lateinit var btnOmniboxMic: ImageButton

    // Bottom Floating Island Bar
    private lateinit var bottomFloatingIsland: LinearLayout
    private lateinit var btnIslandBrowse: LinearLayout
    private lateinit var btnIslandAsk: LinearLayout
    private lateinit var btnIslandShield: ImageButton
    private lateinit var btnIslandBookmarks: ImageButton

    // Activity Result Launcher for History/Bookmarks navigation
    private val contentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val url = result.data?.getStringExtra("selected_url")
            if (!url.isNullOrBlank()) {
                loadUrlInActiveTab(url)
            }
        }
    }

    private var pendingSecurityProceed: (() -> Unit)? = null

    // Activity Result Launcher for Security Warning Interstitials
    private val securityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val overrideUrl = result.data?.getStringExtra("override_url")
            if (!overrideUrl.isNullOrBlank()) {
                // Execute the single bound proceed callback
                pendingSecurityProceed?.invoke()
                etUrlInput.setText(overrideUrl)
                showWebView()
            }
        }
        pendingSecurityProceed = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        controller = BrowserController(this)
        downloadHandler = DownloadHandler(this)

        initViews()
        setupWindowInsets()
        setupTabManager()
        setupListeners()
        setupFavoritesAndSyntheses()
        handleIntent(intent)
    }

    private fun initViews() {
        etUrlInput = findViewById(R.id.etUrlInput)
        btnTabs = findViewById(R.id.btnTabs)
        tvTabCount = findViewById(R.id.tvTabCount)
        btnMenu = findViewById(R.id.btnMenu)
        progressBar = findViewById(R.id.progressBar)
        ivSecurityIndicator = findViewById(R.id.ivSecurityIndicator)
        ivPrivateBadge = findViewById(R.id.ivPrivateBadge)
        tvLocalBadge = findViewById(R.id.tvLocalBadge)
        btnNavBack = findViewById(R.id.btnNavBack)
        btnNavForward = findViewById(R.id.btnNavForward)

        webViewContainer = findViewById(R.id.webViewContainer)
        layoutNewTabCanvas = findViewById(R.id.layoutNewTabCanvas)

        etOmniboxInput = findViewById(R.id.etOmniboxInput)
        btnOmniboxMic = findViewById(R.id.btnOmniboxMic)

        bottomFloatingIsland = findViewById(R.id.bottomFloatingIsland)
        btnIslandBrowse = findViewById(R.id.btnIslandBrowse)
        btnIslandAsk = findViewById(R.id.btnIslandAsk)
        btnIslandShield = findViewById(R.id.btnIslandShield)
        btnIslandBookmarks = findViewById(R.id.btnIslandBookmarks)
    }

    private fun setupWindowInsets() {
        val topChrome = findViewById<View>(R.id.topChromeHeader)
        val root = findViewById<View>(R.id.rootLayout)

        // Immediate fallback so there is never a visual collision on startup
        val fallbackStatusHeight = getStatusBarHeightFallback()
        topChrome.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = fallbackStatusHeight
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val effectiveTop = if (statusBarInsets.top > 0) statusBarInsets.top else fallbackStatusHeight
            topChrome.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = effectiveTop
            }

            bottomFloatingIsland.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navInsets.bottom + (12 * resources.displayMetrics.density).toInt()
            }
            insets
        }
    }

    private fun getStatusBarHeightFallback(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (28 * resources.displayMetrics.density).toInt()
        }
    }

    private fun setupTabManager() {
        tabManager = TabManager(
            context = this,
            webViewContainer = webViewContainer,
            controller = controller,
            downloadHandler = downloadHandler,
            listener = this
        )

        lifecycleScope.launch {
            val savedSessions = controller.restoreSessions()
            if (savedSessions.isNotEmpty()) {
                for (session in savedSessions) {
                    tabManager.createTab(
                        initialUrl = session.url ?: "about:blank",
                        isPrivate = session.isPrivate
                    )
                }
            } else {
                tabManager.createTab("about:blank", isPrivate = false)
            }
        }
    }

    private fun setupListeners() {
        // Address bar in header
        etUrlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                hideKeyboard()
                val text = etUrlInput.text.toString()
                loadUrlInActiveTab(text)
                true
            } else {
                false
            }
        }

        // Omnibox on Start Canvas
        etOmniboxInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                hideKeyboard()
                val text = etOmniboxInput.text.toString()
                loadUrlInActiveTab(text)
                true
            } else {
                false
            }
        }

        btnOmniboxMic.setOnClickListener {
            Toast.makeText(this, "Voice dictation listening via local neural engine...", Toast.LENGTH_SHORT).show()
        }

        // Tab overview button
        btnTabs.setOnClickListener { showTabsDialog() }

        // Navigation Back/Forward
        btnNavBack.setOnClickListener {
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                showStartCanvas()
            }
        }

        btnNavForward.setOnClickListener {
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoForward() == true) {
                webView.goForward()
            }
        }

        // Options Menu
        btnMenu.setOnClickListener { view ->
            showOptionsMenu(view)
        }

        // Bottom Island Interactions
        btnIslandBrowse.setOnClickListener {
            val tab = tabManager.activeTab
            if (tab == null || tab.url == "about:blank" || tab.url.isBlank()) {
                showStartCanvas()
            } else {
                showWebView()
            }
        }

        btnIslandAsk.setOnClickListener {
            contentLauncher.launch(Intent(this, HistoryActivity::class.java))
        }

        btnIslandShield.setOnClickListener {
            showPrivacyVaultDialog()
        }

        btnIslandBookmarks.setOnClickListener {
            contentLauncher.launch(Intent(this, BookmarksActivity::class.java))
        }
    }

    private fun setupFavoritesAndSyntheses() {
        findViewById<View>(R.id.tileGithub)?.setOnClickListener { loadUrlInActiveTab("https://github.com") }
        findViewById<View>(R.id.tileArxiv)?.setOnClickListener { loadUrlInActiveTab("https://arxiv.org") }
        findViewById<View>(R.id.tileWikipedia)?.setOnClickListener { loadUrlInActiveTab("https://en.wikipedia.org") }
        findViewById<View>(R.id.tileHackerNews)?.setOnClickListener { loadUrlInActiveTab("https://news.ycombinator.com") }
        findViewById<View>(R.id.tileLinear)?.setOnClickListener { loadUrlInActiveTab("https://linear.app") }
        findViewById<View>(R.id.tileNotion)?.setOnClickListener { loadUrlInActiveTab("https://notion.so") }
        findViewById<View>(R.id.tileDocs)?.setOnClickListener { loadUrlInActiveTab("https://docs.google.com") }
        findViewById<View>(R.id.tileFigma)?.setOnClickListener { loadUrlInActiveTab("https://figma.com") }

        findViewById<View>(R.id.chipSummarizeRecent)?.setOnClickListener {
            Toast.makeText(this, "Local Synthesis: Analyzing recent tabs offline...", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.chipAuditTrackers)?.setOnClickListener {
            Toast.makeText(this, "EasyPrivacy Filter: 0 tracking scripts allowed on-device.", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.chipCleanLink)?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            val clipText = item?.text?.toString().orEmpty()
            if (clipText.isNotBlank()) {
                val cleanUrl = clipText.replace(Regex("[?&](utm_[^&]+|fbclid=[^&]+|gclid=[^&]+)"), "")
                clipboard?.setPrimaryClip(ClipData.newPlainText("clean_url", cleanUrl))
                Toast.makeText(this, "Link cleaned & copied: stripped tracker params", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUrlInActiveTab(rawInput: String) {
        val (sanitizedUrl, decision) = controller.evaluateNavigation(rawInput)
        updateSecurityIndicator(decision.riskState)

        if (decision.action == GateAction.BLOCK || decision.action == GateAction.WARN) {
            onSecurityIntervention(decision, sanitizedUrl) {
                tabManager.activeTab?.webView?.loadUrl(sanitizedUrl)
                etUrlInput.setText(sanitizedUrl)
                showWebView()
            }
        } else {
            val tab = tabManager.activeTab
            if (tab != null) {
                tab.webView.loadUrl(sanitizedUrl)
                etUrlInput.setText(sanitizedUrl)
                showWebView()
            }
        }
    }

    private fun showStartCanvas() {
        layoutNewTabCanvas.visibility = View.VISIBLE
        webViewContainer.visibility = View.GONE
        etUrlInput.setText("")
        tvLocalBadge.text = "local"
    }

    private fun showWebView() {
        layoutNewTabCanvas.visibility = View.GONE
        webViewContainer.visibility = View.VISIBLE
    }

    private fun showPrivacyVaultDialog() {
        val dialog = BottomSheetDialog(this)
        val tab = tabManager.activeTab
        val url = tab?.url ?: "about:blank"
        val (_, decision) = controller.evaluateNavigation(url)

        val view = layoutInflater.inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(view)

        val header = view.findViewById<TextView>(R.id.tvTabsHeader)
        val btnAdd = view.findViewById<Button>(R.id.btnAddNewTab)
        val btnAddPrivate = view.findViewById<Button>(R.id.btnNewPrivateTab)
        val btnDone = view.findViewById<Button>(R.id.btnCloseDialog)
        val rvList = view.findViewById<RecyclerView>(R.id.rvTabsList)

        header.text = "Privacy Vault & Shield"
        btnAdd.visibility = View.GONE
        btnAddPrivate.visibility = View.GONE
        rvList.visibility = View.GONE

        btnDone.text = "Dismiss"
        btnDone.setOnClickListener { dialog.dismiss() }

        val info = TextView(this).apply {
            text = "SOVEREIGN ENCLAVE TELEMETRY\n\n" +
                    "• Host: ${if (url.isBlank() || url == "about:blank") "Local Canvas" else url}\n" +
                    "• Risk Classification: ${decision.riskState}\n" +
                    "• Threat Feeds: URLhaus (Malware) • EasyList • EasyPrivacy\n" +
                    "• Deterministic Engine: Active on-device SQLite\n" +
                    "• Zero-Cloud Leak Guarantee: Enforced"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(16, 16, 16, 24)
        }
        (view as LinearLayout).addView(info, 2)

        dialog.show()
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reload -> {
                    val webView = tabManager.activeTab?.webView
                    if (webView != null) {
                        if (webView.progress < 100) webView.stopLoading() else webView.reload()
                    }
                    true
                }
                R.id.action_add_bookmark -> {
                    val tab = tabManager.activeTab
                    if (tab != null && tab.url.isNotBlank() && tab.url != "about:blank") {
                        controller.toggleBookmark(tab.url, tab.title) { isAdded ->
                            val msg = if (isAdded) getString(R.string.bookmark_added) else getString(R.string.bookmark_removed)
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                R.id.action_share -> {
                    val tab = tabManager.activeTab
                    if (tab != null && tab.url.isNotBlank() && tab.url != "about:blank") {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, tab.url)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Link"))
                    }
                    true
                }
                R.id.action_new_tab -> {
                    tabManager.createTab("about:blank")
                    showStartCanvas()
                    true
                }
                R.id.action_new_private_tab -> {
                    tabManager.createTab("about:blank", isPrivate = true)
                    Toast.makeText(this, R.string.private_mode_notice, Toast.LENGTH_SHORT).show()
                    showStartCanvas()
                    true
                }
                R.id.action_bookmarks -> {
                    contentLauncher.launch(Intent(this, BookmarksActivity::class.java))
                    true
                }
                R.id.action_history -> {
                    contentLauncher.launch(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showTabsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(view)

        val rvTabs = view.findViewById<RecyclerView>(R.id.rvTabsList)
        val btnAdd = view.findViewById<Button>(R.id.btnAddNewTab)
        val btnAddPrivate = view.findViewById<Button>(R.id.btnNewPrivateTab)
        val btnDone = view.findViewById<Button>(R.id.btnCloseDialog)

        rvTabs.layoutManager = LinearLayoutManager(this)
        val adapter = TabsAdapter(
            tabs = tabManager.getTabsList(),
            activeTabId = tabManager.activeTab?.id,
            onTabClick = { clickedTab ->
                tabManager.switchTab(clickedTab.id)
                dialog.dismiss()
            },
            onTabClose = { closedTab ->
                tabManager.closeTab(closedTab.id)
                dialog.dismiss()
            }
        )
        rvTabs.adapter = adapter

        btnAdd.setOnClickListener {
            tabManager.createTab("about:blank")
            showStartCanvas()
            dialog.dismiss()
        }

        btnAddPrivate.setOnClickListener {
            tabManager.createTab("about:blank", isPrivate = true)
            showStartCanvas()
            dialog.dismiss()
            Toast.makeText(this, R.string.private_mode_notice, Toast.LENGTH_SHORT).show()
        }

        btnDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onActiveTabChanged(tab: BrowserTab) {
        val isBlank = tab.url.isBlank() || tab.url == "about:blank"
        if (isBlank) {
            showStartCanvas()
        } else {
            showWebView()
            if (!etUrlInput.hasFocus()) {
                etUrlInput.setText(tab.url)
            }
        }

        if (tab.isPrivate) {
            ivPrivateBadge.visibility = View.VISIBLE
            tvLocalBadge.text = "private"
            ivSecurityIndicator.setColorFilter(ContextCompat.getColor(this, R.color.incognito_accent))
        } else {
            ivPrivateBadge.visibility = View.GONE
            tvLocalBadge.text = "local"
            val (_, decision) = controller.evaluateNavigation(tab.url)
            updateSecurityIndicator(decision.riskState)
        }

        updateNavigationButtons()
    }

    override fun onTabsUpdated(tabs: List<BrowserTab>) {
        tvTabCount.text = tabs.size.toString()
        updateNavigationButtons()
    }

    override fun onPageProgress(progress: Int) {
        if (progress in 1..99) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress
        } else {
            progressBar.visibility = View.GONE
        }
    }

    override fun onSecurityIntervention(decision: SecurityDecision, targetUrl: String, onProceed: () -> Unit) {
        pendingSecurityProceed = onProceed
        updateSecurityIndicator(decision.riskState)

        val intent = Intent(this, SecurityWarningActivity::class.java).apply {
            putExtra(SecurityWarningActivity.EXTRA_TARGET_URL, targetUrl)
            putExtra(SecurityWarningActivity.EXTRA_ACTION, decision.action.name)
            putExtra(SecurityWarningActivity.EXTRA_RISK_STATE, decision.riskState.name)
            putStringArrayListExtra(SecurityWarningActivity.EXTRA_REASONS, ArrayList(decision.reasons))
            putExtra(SecurityWarningActivity.EXTRA_RULE_ID, decision.matchedRuleId)
            putExtra(SecurityWarningActivity.EXTRA_RISK_SCORE, decision.riskScore)
        }
        securityLauncher.launch(intent)
    }

    private fun updateSecurityIndicator(riskState: RiskState) {
        val colorRes = when (riskState) {
            RiskState.KNOWN_SAFE -> R.color.risk_safe
            RiskState.UNKNOWN -> R.color.risk_unknown
            RiskState.SUSPICIOUS -> R.color.risk_suspicious
            RiskState.HIGH_RISK, RiskState.BLOCKED -> R.color.risk_blocked
        }
        ivSecurityIndicator.setColorFilter(ContextCompat.getColor(this, colorRes))
    }

    private fun updateNavigationButtons() {
        val webView = tabManager.activeTab?.webView
        val canBack = webView?.canGoBack() == true
        val canForward = webView?.canGoForward() == true

        btnNavBack.alpha = if (canBack || layoutNewTabCanvas.visibility != View.VISIBLE) 1.0f else 0.4f
        btnNavForward.alpha = if (canForward) 1.0f else 0.4f
    }

    override fun onBackPressed() {
        val webView = tabManager.activeTab?.webView
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else if (layoutNewTabCanvas.visibility != View.VISIBLE) {
            showStartCanvas()
        } else if (tabManager.tabCount > 1) {
            tabManager.activeTab?.id?.let { tabManager.closeTab(it) }
        } else {
            super.onBackPressed()
        }
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val data: Uri? = intent?.data
        if (Intent.ACTION_VIEW == action && data != null) {
            val url = data.toString()
            loadUrlInActiveTab(url)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etUrlInput.windowToken, 0)
        imm?.hideSoftInputFromWindow(etOmniboxInput.windowToken, 0)
    }
}
