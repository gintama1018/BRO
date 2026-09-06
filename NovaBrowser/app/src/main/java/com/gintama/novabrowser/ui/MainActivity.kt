package com.gintama.novabrowser.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.GridLayoutManager
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
import com.gintama.novabrowser.core.navigation.SearchEngine
import com.gintama.novabrowser.search.SearchEngineManager
import com.gintama.novabrowser.settings.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.speech.RecognizerIntent
import android.content.pm.ActivityInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.gintama.novabrowser.downloads.DownloadsActivity
import android.content.ActivityNotFoundException
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import com.gintama.novabrowser.adblock.AdBlockEngine
import com.google.android.material.switchmaterial.SwitchMaterial
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.ui.motion.NovaMotion
import kotlinx.coroutines.launch
import java.util.ArrayList

import android.net.http.SslError
import android.os.Message
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity(), TabChangeListener {

    private lateinit var controller: BrowserController
    private lateinit var downloadHandler: DownloadHandler
    private lateinit var tabManager: TabManager

    // Top Header & Address Bar Views
    private lateinit var topChromeHeader: LinearLayout
    private lateinit var etUrlInput: EditText
    private lateinit var btnReloadPage: ImageButton
    private lateinit var btnTabs: FrameLayout
    private lateinit var tvTabCount: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var ivSecurityIndicator: ImageView
    private lateinit var ivPrivateBadge: ImageView
    private lateinit var layoutShieldBadge: View
    private lateinit var tvShieldBadgeCount: TextView
    private lateinit var btnNavBack: ImageButton

    // Viewport Containers
    private lateinit var mainViewportContainer: FrameLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webViewContainer: FrameLayout
    private lateinit var layoutNewTabCanvas: ScrollView
    private lateinit var fullscreenCustomViewContainer: FrameLayout

    // Fullscreen Video State
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility: Int = 0
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    // Web Uploads State
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Activity Result Launcher for HTML5 File Chooser / Web Uploads
    private val fileUploadLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val parsed = WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
            if (!parsed.isNullOrEmpty()) {
                parsed
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else if (data?.clipData != null) {
                val clipData = data.clipData!!
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else {
                null
            }
        } else {
            null
        }
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    // Omnibox & Search Engine Switcher
    private lateinit var etOmniboxInput: EditText
    private lateinit var btnOmniboxMic: ImageButton
    private lateinit var btnSearchEnginePicker: TextView
    private lateinit var tvCanvasBlockedCount: TextView

    // Hero Logo & Ambient Glow
    private lateinit var ivHeroLogo: ImageView
    private lateinit var ivHeroGlow: View

    // Find in Page Bar
    private lateinit var layoutFindInPage: LinearLayout
    private lateinit var etFindQuery: EditText
    private lateinit var tvFindMatches: TextView
    private lateinit var btnFindPrev: ImageButton
    private lateinit var btnFindNext: ImageButton
    private lateinit var btnFindClose: ImageButton

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

    // Speech Recognition Launcher
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                etOmniboxInput.setText(spoken)
                loadUrlInActiveTab(spoken)
            }
        }
    }

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
        AdBlockEngine.init(this)

        initViews()
        setupWindowInsets()
        setupTabManager()
        setupListeners()
        setupFavoritesAndSyntheses()
        setupMotionGraphics()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateSearchEnginePickerLabel()
    }

    private fun initViews() {
        topChromeHeader = findViewById(R.id.topChromeHeader)
        etUrlInput = findViewById(R.id.etUrlInput)
        btnReloadPage = findViewById(R.id.btnReloadPage)
        btnTabs = findViewById(R.id.btnTabs)
        tvTabCount = findViewById(R.id.tvTabCount)
        btnMenu = findViewById(R.id.btnMenu)
        progressBar = findViewById(R.id.progressBar)
        ivSecurityIndicator = findViewById(R.id.ivSecurityIndicator)
        ivPrivateBadge = findViewById(R.id.ivPrivateBadge)
        layoutShieldBadge = findViewById(R.id.layoutShieldBadge)
        tvShieldBadgeCount = findViewById(R.id.tvShieldBadgeCount)
        btnNavBack = findViewById(R.id.btnNavBack)

        mainViewportContainer = findViewById(R.id.mainViewportContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webViewContainer = findViewById(R.id.webViewContainer)
        layoutNewTabCanvas = findViewById(R.id.layoutNewTabCanvas)
        fullscreenCustomViewContainer = findViewById(R.id.fullscreenCustomViewContainer)

        etOmniboxInput = findViewById(R.id.etOmniboxInput)
        btnOmniboxMic = findViewById(R.id.btnOmniboxMic)
        btnSearchEnginePicker = findViewById(R.id.btnSearchEnginePicker)
        tvCanvasBlockedCount = findViewById(R.id.tvCanvasBlockedCount)

        ivHeroLogo = findViewById(R.id.ivHeroLogo)
        ivHeroGlow = findViewById(R.id.ivHeroGlow)

        layoutFindInPage = findViewById(R.id.layoutFindInPage)
        etFindQuery = findViewById(R.id.etFindQuery)
        tvFindMatches = findViewById(R.id.tvFindMatches)
        btnFindPrev = findViewById(R.id.btnFindPrev)
        btnFindNext = findViewById(R.id.btnFindNext)
        btnFindClose = findViewById(R.id.btnFindClose)

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

        // Address bar long-press for clean link copy
        etUrlInput.setOnLongClickListener {
            copyCleanLink(etUrlInput.text.toString())
            true
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

        // Real Speech Recognition (Phase 0 Fix)
        btnOmniboxMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search or speak URL...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Speech recognition is not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }

        // Search engine switcher
        updateSearchEnginePickerLabel()
        btnSearchEnginePicker.setOnClickListener { showSearchEnginePicker() }

        // Tab overview button
        btnTabs.setOnClickListener { showTabsDialog() }

        // Navigation Back & In-Pill Quick Reload
        btnNavBack.setOnClickListener {
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                showStartCanvas()
            }
        }

        btnReloadPage.setOnClickListener {
            val webView = tabManager.activeTab?.webView
            if (webView != null) {
                if (webView.progress < 100) {
                    webView.stopLoading()
                    btnReloadPage.setImageResource(R.drawable.ic_refresh)
                } else {
                    webView.reload()
                }
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

        btnIslandAsk.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Quick Clear History")
                .setMessage("Clear browsing history and cached sessions?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        controller.clearHistory()
                        Toast.makeText(this@MainActivity, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        btnIslandShield.setOnClickListener {
            showAdBlockShieldBottomSheet()
        }

        layoutShieldBadge.setOnClickListener {
            showAdBlockShieldBottomSheet()
        }

        ivSecurityIndicator.setOnClickListener {
            showSecurityInfoDialog()
        }

        btnIslandBookmarks.setOnClickListener {
            contentLauncher.launch(Intent(this, BookmarksActivity::class.java))
        }

        btnIslandBookmarks.setOnLongClickListener {
            val tab = tabManager.activeTab
            if (tab != null && tab.url.isNotBlank() && tab.url != "about:blank") {
                controller.toggleBookmark(tab.url, tab.title) { isAdded ->
                    val msg = if (isAdded) "Quick-bookmarked: ${tab.title}" else "Removed bookmark: ${tab.title}"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        // Pull to refresh
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.risk_safe),
            ContextCompat.getColor(this, R.color.accent_emerald)
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface_container_highest)
        )
        swipeRefreshLayout.setOnRefreshListener {
            val webView = tabManager.activeTab?.webView
            if (webView != null) {
                webView.reload()
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            val webView = tabManager.activeTab?.webView
            (webView?.scrollY ?: 0) > 0
        }
    }

    private fun setupFavoritesAndSyntheses() {
        bindEditableTile(R.id.tileGithub, "tile_gh", "GitHub", "https://github.com")
        bindEditableTile(R.id.tileArxiv, "tile_arxiv", "arXiv", "https://arxiv.org")
        bindEditableTile(R.id.tileWikipedia, "tile_wiki", "Wikipedia", "https://en.wikipedia.org")
        bindEditableTile(R.id.tileHackerNews, "tile_hn", "HN", "https://news.ycombinator.com")
        bindEditableTile(R.id.tileLinear, "tile_linear", "Linear", "https://linear.app")
        bindEditableTile(R.id.tileNotion, "tile_notion", "Notion", "https://notion.so")
        bindEditableTile(R.id.tileDocs, "tile_docs", "Docs", "https://docs.google.com")
        bindEditableTile(R.id.tileFigma, "tile_figma", "Figma", "https://figma.com")

        findViewById<View>(R.id.btnEditFavorites)?.setOnClickListener {
            Toast.makeText(this, "Long-press any shortcut tile to customize its title and URL", Toast.LENGTH_SHORT).show()
        }

        // Phase 0 Fix: Real state indicator, no fake synthesis claims
        findViewById<View>(R.id.chipSummarizeRecent)?.setOnClickListener {
            Toast.makeText(this, "Local AI Synthesis: Scheduled for Phase 3. Offline model dormant to preserve RAM.", Toast.LENGTH_LONG).show()
        }

        // Phase 0 Fix: Real computed counter readout
        findViewById<View>(R.id.chipAuditTrackers)?.setOnClickListener {
            val tab = tabManager.activeTab
            val pageCount = tab?.blockedAdsCount ?: 0
            val lifetime = AdBlockEngine.getLifetimeBlockedCount()
            Toast.makeText(this, "Local AdBlock: $pageCount ads/trackers blocked on this tab ($lifetime lifetime)", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.chipCleanLink)?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            val clipText = item?.text?.toString().orEmpty()
            if (clipText.isNotBlank()) {
                copyCleanLink(clipText)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindEditableTile(viewId: Int, prefKey: String, defaultName: String, defaultUrl: String) {
        val tileView = findViewById<ViewGroup>(viewId) ?: return
        val prefs = getSharedPreferences("nova_custom_tiles", Context.MODE_PRIVATE)
        val currentName = prefs.getString("${prefKey}_name", defaultName) ?: defaultName
        val currentUrl = prefs.getString("${prefKey}_url", defaultUrl) ?: defaultUrl

        // Update tile text label if present
        for (i in 0 until tileView.childCount) {
            val child = tileView.getChildAt(i)
            if (child is TextView && child.text.toString().isNotBlank() && child.id != View.NO_ID) {
                // Keep icon badge, update title
            }
        }

        tileView.setOnClickListener {
            val target = prefs.getString("${prefKey}_url", defaultUrl) ?: defaultUrl
            loadUrlInActiveTab(target)
        }

        tileView.setOnLongClickListener {
            showEditTileDialog(prefKey, currentName, currentUrl) {
                bindEditableTile(viewId, prefKey, defaultName, defaultUrl)
            }
            true
        }

        // Tactile spring micro-interaction
        NovaMotion.attachSpringTouchFeedback(tileView)
    }

    private fun setupMotionGraphics() {
        NovaMotion.startHeroBreathingAnimation(ivHeroLogo, ivHeroGlow)
        NovaMotion.attachSpringTouchFeedback(
            btnIslandBrowse, btnIslandAsk, btnIslandShield, btnIslandBookmarks,
            btnTabs, btnMenu, btnNavBack, btnReloadPage, layoutShieldBadge,
            btnSearchEnginePicker, btnOmniboxMic
        )
    }

    private fun showEditTileDialog(prefKey: String, curName: String, curUrl: String, onUpdated: () -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val etName = EditText(this).apply {
            hint = "Shortcut Name"
            setText(curName)
        }
        val etUrl = EditText(this).apply {
            hint = "https://example.com"
            setText(curUrl)
            inputType = EditorInfo.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(etName)
        layout.addView(etUrl)

        AlertDialog.Builder(this)
            .setTitle("Edit Favorite Shortcut")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newUrl = etUrl.text.toString().trim()
                if (newUrl.isNotBlank()) {
                    val prefs = getSharedPreferences("nova_custom_tiles", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("${prefKey}_name", newName)
                        .putString("${prefKey}_url", newUrl)
                        .apply()
                    onUpdated()
                    Toast.makeText(this, "Shortcut updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyCleanLink(url: String) {
        if (url.isBlank() || url == "about:blank") {
            Toast.makeText(this, "No valid link to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanUrl = url.replace(Regex("[?&](utm_[^&]+|fbclid=[^&]+|gclid=[^&]+|igshid=[^&]+|msclkid=[^&]+|mc_eid=[^&]+)"), "")
            .replace(Regex("\\?$"), "")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("clean_url", cleanUrl))
        Toast.makeText(this, "Clean link copied: tracker parameters removed", Toast.LENGTH_SHORT).show()
    }

    private fun getSearchEngineUrl(query: String): String {
        return SearchEngineManager.buildSearchUrl(this, query)
    }

    private fun updateSearchEnginePickerLabel() {
        val activeEngine = SearchEngineManager.getActiveEngine(this)
        controller.defaultSearchTemplate = SearchEngineManager.getActiveSearchTemplate(this)
        val code = when (activeEngine) {
            SearchEngine.DUCKDUCKGO -> "DDG ▾"
            SearchEngine.BRAVE -> "Brave ▾"
            SearchEngine.GOOGLE -> "Google ▾"
            SearchEngine.BING -> "Bing ▾"
            SearchEngine.STARTPAGE -> "SP ▾"
            SearchEngine.ECOSIA -> "Ecosia ▾"
            SearchEngine.CUSTOM -> "Custom ▾"
        }
        btnSearchEnginePicker.text = code
    }

    private fun showSearchEnginePicker() {
        val engines = SearchEngine.entries.toTypedArray()
        val names = engines.map { "${it.displayName}\n${it.description}" }.toTypedArray()
        val current = SearchEngineManager.getActiveEngine(this)
        val selectedIndex = engines.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Default Search Engine")
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                val chosen = engines[which]
                if (chosen == SearchEngine.CUSTOM) {
                    dialog.dismiss()
                    showCustomSearchEngineDialog {
                        updateSearchEnginePickerLabel()
                    }
                } else {
                    SearchEngineManager.setActiveEngine(this, chosen)
                    updateSearchEnginePickerLabel()
                    Toast.makeText(this, "Search engine: ${chosen.displayName}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomSearchEngineDialog(onSaved: () -> Unit) {
        val current = SearchEngineManager.getCustomUrl(this)
        val input = EditText(this).apply {
            hint = "https://example.com/search?q=%s"
            setText(current)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Custom Search URL")
            .setMessage("Enter the search engine URL template using %s for the query term:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    SearchEngineManager.setActiveEngine(this, SearchEngine.CUSTOM, url)
                    onSaved()
                    Toast.makeText(this, "Custom search engine configured", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadUrlInActiveTab(rawInput: String) {
        val searchTemplate = SearchEngineManager.getActiveSearchTemplate(this)
        val (sanitizedUrl, decision) = controller.evaluateNavigation(rawInput, searchEngineTemplate = searchTemplate)
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
        NovaMotion.crossFade(swipeRefreshLayout, layoutNewTabCanvas)
        etUrlInput.setText("")
        updateNavigationButtons()
        NovaMotion.animateCountUp(
            tvCanvasBlockedCount,
            AdBlockEngine.getLifetimeBlockedCount(),
            "Ads & Trackers Neutralized"
        )
    }

    private fun showWebView() {
        NovaMotion.crossFade(layoutNewTabCanvas, swipeRefreshLayout)
        updateNavigationButtons()
    }

    private fun showSiteDataTransparencyDialog() {
        val tab = tabManager.activeTab
        val currentUrl = tab?.url.orEmpty()
        val siteHost = try {
            val host = java.net.URI(currentUrl).host
            if (host.isNullOrBlank()) "Local Storage" else host
        } catch (e: Exception) {
            if (currentUrl.isBlank() || currentUrl == "about:blank") "Local Storage" else currentUrl
        }

        val canonicalOrigin = if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            com.gintama.novabrowser.core.security.UrlCanonicalizer.canonicalOrigin(currentUrl)
        } else {
            "sandbox://local"
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_site_transparency, null)
        dialog.setContentView(view)

        val tvHost = view.findViewById<TextView>(R.id.tvTransparencyHost)
        val tvOrigin = view.findViewById<TextView>(R.id.tvTransparencyOrigin)
        val tvPermissions = view.findViewById<TextView>(R.id.tvTransparencyPermissions)
        val tvTelemetry = view.findViewById<TextView>(R.id.tvTransparencyTelemetry)
        val btnClear = view.findViewById<Button>(R.id.btnClearSiteData)
        val btnDone = view.findViewById<Button>(R.id.btnDoneTransparency)

        tvHost.text = siteHost
        tvOrigin.text = "Origin: $canonicalOrigin"

        val dbHelper = NovaDatabaseHelper.getInstance(this)
        val perms = dbHelper.getSitePermissions(canonicalOrigin)
        if (perms.isNotEmpty()) {
            tvPermissions.text = perms.entries.joinToString("\n") { (k, v) ->
                val permName = when (k) {
                    com.gintama.novabrowser.browser.SitePermissionType.CAMERA -> "Camera Access"
                    com.gintama.novabrowser.browser.SitePermissionType.MICROPHONE -> "Microphone Access"
                    com.gintama.novabrowser.browser.SitePermissionType.GEOLOCATION -> "Geolocation Access"
                    else -> k
                }
                "• $permName: ${if (v) "GRANTED" else "BLOCKED"}"
            }
        } else {
            tvPermissions.text = "• No hardware or device permissions stored for this origin"
        }

        val blockedCount = tab?.blockedAdsCount ?: 0
        tvTelemetry.text = "• Storage Model: App-Private Sandbox (WAL SQLite)\n• Trackers Neutralized: $blockedCount on active tab\n• External Telemetry: 0 bytes (Zero remote tracking)"

        btnClear.setOnClickListener {
            try {
                for (k in perms.keys) {
                    dbHelper.setSitePermission(canonicalOrigin, k, false)
                }
                val cookieManager = android.webkit.CookieManager.getInstance()
                if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                    cookieManager.setCookie(currentUrl, "")
                }
                Toast.makeText(this, "Cleared local data and permissions for $siteHost", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(this, "Error clearing data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnDone.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showAdBlockShieldBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_adblock_shield, null)
        dialog.setContentView(view)

        val tab = tabManager.activeTab
        val currentUrl = tab?.url.orEmpty()
        val siteHost = try {
            val host = java.net.URI(currentUrl).host
            if (host.isNullOrBlank()) "Local Canvas" else host
        } catch (e: Exception) {
            if (currentUrl.isBlank() || currentUrl == "about:blank") "Local Canvas" else currentUrl
        }

        val tvSiteDomain = view.findViewById<TextView>(R.id.tvShieldSiteDomain)
        val tvStatus = view.findViewById<TextView>(R.id.tvShieldProtectionStatus)
        val tvBlockedThisPage = view.findViewById<TextView>(R.id.tvShieldBlockedThisPage)
        val tvLifetimeTotal = view.findViewById<TextView>(R.id.tvShieldLifetimeTotal)
        val switchSiteAdBlock = view.findViewById<SwitchMaterial>(R.id.switchSiteAdBlock)
        val switchSiteCosmetic = view.findViewById<SwitchMaterial>(R.id.switchSiteCosmetic)
        val tvTelemetry = view.findViewById<TextView>(R.id.tvShieldRulesTelemetry)
        val btnReport = view.findViewById<Button>(R.id.btnReportBrokenSite)
        val btnSettings = view.findViewById<Button>(R.id.btnOpenAdBlockSettings)
        val btnDone = view.findViewById<Button>(R.id.btnDoneShield)

        tvSiteDomain.text = siteHost
        val blockedThisPage = tab?.blockedAdsCount ?: 0
        tvBlockedThisPage.text = blockedThisPage.toString()
        tvLifetimeTotal.text = AdBlockEngine.getLifetimeBlockedCount().toString()

        val isLocalCanvas = siteHost == "Local Canvas"
        if (isLocalCanvas) {
            switchSiteAdBlock.isEnabled = false
            switchSiteCosmetic.isEnabled = false
            tvStatus.text = "Local Canvas • No External Trackers"
            btnReport.visibility = View.GONE
        } else {
            val (adBlockEnabled, cosmeticEnabled) = AdBlockEngine.getSiteRule(siteHost)
            switchSiteAdBlock.isChecked = adBlockEnabled
            switchSiteCosmetic.isChecked = cosmeticEnabled

            tvStatus.text = if (adBlockEnabled) "Shield Active • On-Device Protection" else "Shield Paused on This Site"
            tvStatus.setTextColor(ContextCompat.getColor(this, if (adBlockEnabled) R.color.accent_emerald else R.color.risk_suspicious))

            switchSiteAdBlock.setOnCheckedChangeListener { _, isChecked ->
                AdBlockEngine.setSiteRule(siteHost, isChecked, switchSiteCosmetic.isChecked)
                tvStatus.text = if (isChecked) "Shield Active • On-Device Protection" else "Shield Paused on This Site"
                tvStatus.setTextColor(ContextCompat.getColor(this, if (isChecked) R.color.accent_emerald else R.color.risk_suspicious))
            }

            switchSiteCosmetic.setOnCheckedChangeListener { _, isChecked ->
                AdBlockEngine.setSiteRule(siteHost, switchSiteAdBlock.isChecked, isChecked)
            }

            btnReport.setOnClickListener {
                NovaDatabaseHelper.getInstance(this).reportBrokenSite(currentUrl)
                Toast.makeText(this, "Site reported locally for filter adjustments", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnSettings.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSecurityInfoDialog() {
        val tab = tabManager.activeTab
        val currentUrl = tab?.url ?: "about:blank"
        val (_, decision) = controller.evaluateNavigation(currentUrl)

        val host = try { java.net.URI(currentUrl).host ?: currentUrl } catch (e: Exception) { currentUrl }
        val reasons = if (decision.reasons.isNotEmpty()) {
            decision.reasons.joinToString("\n• ")
        } else {
            "Passed offline canonical validation\n• No typosquatting detected\n• Threat feed patterns clear"
        }

        AlertDialog.Builder(this)
            .setTitle("Security Audit: $host")
            .setMessage(
                "Risk Classification: ${decision.riskState}\n" +
                "Risk Score: ${decision.riskScore}\n" +
                "Rule Matched: ${if (decision.matchedRuleId.isNullOrBlank()) "Standard Heuristics" else decision.matchedRuleId}\n\n" +
                "Audit Reasons:\n• $reasons"
            )
            .setPositiveButton("Close", null)
            .setNeutralButton("Privacy Settings") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Site Local Data") { _, _ ->
                showSiteDataTransparencyDialog()
            }
            .show()
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

        // Set Desktop Site checkmark
        val desktopItem = popup.menu.findItem(R.id.action_desktop_site)
        val currentWebView = tabManager.activeTab?.webView
        desktopItem?.isChecked = currentWebView?.isDesktopMode == true

        // Forward navigation state
        val forwardItem = popup.menu.findItem(R.id.action_forward)
        forwardItem?.isEnabled = currentWebView?.canGoForward() == true

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_forward -> {
                    val webView = tabManager.activeTab?.webView
                    if (webView?.canGoForward() == true) {
                        webView.goForward()
                    }
                    true
                }
                R.id.action_reload -> {
                    val webView = tabManager.activeTab?.webView
                    if (webView != null) {
                        if (webView.progress < 100) webView.stopLoading() else webView.reload()
                    }
                    true
                }
                R.id.action_find_in_page -> {
                    showFindInPage()
                    true
                }
                R.id.action_desktop_site -> {
                    val webView = tabManager.activeTab?.webView
                    if (webView != null) {
                        val newMode = !webView.isDesktopMode
                        webView.setDesktopMode(newMode)
                        val msg = if (newMode) "Desktop mode enabled" else "Mobile mode restored"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_copy_clean_link -> {
                    copyCleanLink(tabManager.activeTab?.url.orEmpty())
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
                R.id.action_downloads -> {
                    startActivity(Intent(this, DownloadsActivity::class.java))
                    true
                }
                R.id.action_site_transparency -> {
                    showSiteDataTransparencyDialog()
                    true
                }
                R.id.action_search_engine -> {
                    showSearchEnginePicker()
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
        tabManager.captureActiveTabThumbnail()

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }

        val btnTabFilterStandard = view.findViewById<TextView>(R.id.btnTabFilterStandard)
        val btnTabFilterPrivate = view.findViewById<TextView>(R.id.btnTabFilterPrivate)
        val rvTabs = view.findViewById<RecyclerView>(R.id.rvTabsList)
        val layoutTabsEmpty = view.findViewById<View>(R.id.layoutTabsEmpty)
        val ivEmptyIcon = view.findViewById<ImageView>(R.id.ivEmptyIcon)
        val tvEmptyTitle = view.findViewById<TextView>(R.id.tvEmptyTitle)
        val tvEmptySubtitle = view.findViewById<TextView>(R.id.tvEmptySubtitle)
        val btnEmptyCreateTab = view.findViewById<Button>(R.id.btnEmptyCreateTab)
        val btnCloseAll = view.findViewById<Button>(R.id.btnCloseAllTabs)
        val btnAddNewTab = view.findViewById<Button>(R.id.btnAddNewTab)
        val btnDone = view.findViewById<Button>(R.id.btnCloseDialog)

        var showPrivateOnly = tabManager.activeTab?.isPrivate == true

        rvTabs.layoutManager = GridLayoutManager(this, 2)
        lateinit var adapter: TabsAdapter
        lateinit var refreshTabList: () -> Unit

        adapter = TabsAdapter(
            tabs = emptyList(),
            activeTabId = tabManager.activeTab?.id,
            onTabClick = { clickedTab ->
                tabManager.switchTab(clickedTab.id)
                dialog.dismiss()
            },
            onTabClose = { closedTab ->
                tabManager.closeTab(closedTab.id)
                if (tabManager.tabCount == 0) {
                    tabManager.createTab("about:blank")
                    showStartCanvas()
                    dialog.dismiss()
                } else {
                    refreshTabList()
                }
            }
        )
        rvTabs.adapter = adapter

        refreshTabList = {
            val standardTabs = tabManager.getStandardTabs()
            val privateTabs = tabManager.getPrivateTabs()
            val currentList = if (showPrivateOnly) privateTabs else standardTabs

            btnTabFilterStandard.text = "Standard (${standardTabs.size})"
            btnTabFilterPrivate.text = "Private (${privateTabs.size})"

            if (showPrivateOnly) {
                btnTabFilterPrivate.setBackgroundResource(R.drawable.bg_glass_pill_dark)
                btnTabFilterPrivate.setTextColor(ContextCompat.getColor(this, R.color.incognito_accent))
                btnTabFilterStandard.setBackgroundResource(android.R.color.transparent)
                btnTabFilterStandard.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                btnAddNewTab.text = "+ New Private Tab"
                btnAddNewTab.setTextColor(ContextCompat.getColor(this, R.color.incognito_accent))
                ivEmptyIcon.setImageResource(R.drawable.ic_incognito)
                ivEmptyIcon.setColorFilter(ContextCompat.getColor(this, R.color.incognito_accent))
                tvEmptyTitle.text = "No Private Tabs"
                tvEmptySubtitle.text = "Private browsing leaves zero history, cache, or cookies"
                btnEmptyCreateTab.text = "+ Open Private Tab"
            } else {
                btnTabFilterStandard.setBackgroundResource(R.drawable.bg_glass_pill_dark)
                btnTabFilterStandard.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
                btnTabFilterPrivate.setBackgroundResource(android.R.color.transparent)
                btnTabFilterPrivate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                btnAddNewTab.text = "+ New Tab"
                btnAddNewTab.setTextColor(ContextCompat.getColor(this, R.color.text_on_primary))
                ivEmptyIcon.setImageResource(R.drawable.ic_tabs)
                ivEmptyIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
                tvEmptyTitle.text = "No Standard Tabs"
                tvEmptySubtitle.text = "Open a standard tab to browse with offline adblock & security"
                btnEmptyCreateTab.text = "+ Open Standard Tab"
            }

            if (currentList.isEmpty()) {
                rvTabs.visibility = View.GONE
                layoutTabsEmpty.visibility = View.VISIBLE
            } else {
                rvTabs.visibility = View.VISIBLE
                layoutTabsEmpty.visibility = View.GONE
                adapter.updateTabs(currentList, tabManager.activeTab?.id)
            }
        }

        // Swipe to close tabs
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos in 0 until adapter.itemCount) {
                    val tab = adapter.getTabAt(pos)
                    tabManager.closeTab(tab.id)
                    if (tabManager.tabCount == 0) {
                        tabManager.createTab("about:blank")
                        showStartCanvas()
                        dialog.dismiss()
                    } else {
                        refreshTabList()
                    }
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvTabs)

        btnTabFilterStandard.setOnClickListener {
            if (showPrivateOnly) {
                showPrivateOnly = false
                refreshTabList()
            }
        }

        btnTabFilterPrivate.setOnClickListener {
            if (!showPrivateOnly) {
                showPrivateOnly = true
                refreshTabList()
            }
        }

        btnCloseAll.setOnClickListener {
            val title = if (showPrivateOnly) "Close All Private Tabs?" else "Close All Tabs?"
            val msg = if (showPrivateOnly) {
                "All private tabs and their in-memory data will be cleared."
            } else {
                "All standard tabs will be closed."
            }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Close All") { _, _ ->
                    tabManager.closeAllTabs(isPrivateOnly = if (showPrivateOnly) true else null)
                    showStartCanvas()
                    dialog.dismiss()
                    Toast.makeText(this, "Tabs closed", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnAddNewTab.setOnClickListener {
            if (showPrivateOnly) {
                tabManager.createTab("about:blank", isPrivate = true)
                Toast.makeText(this, R.string.private_mode_notice, Toast.LENGTH_SHORT).show()
            } else {
                tabManager.createTab("about:blank", isPrivate = false)
            }
            showStartCanvas()
            dialog.dismiss()
        }

        btnEmptyCreateTab.setOnClickListener {
            btnAddNewTab.performClick()
        }

        btnDone.setOnClickListener { dialog.dismiss() }

        refreshTabList()
        dialog.show()
    }

    private fun showFindInPage() {
        val webView = tabManager.activeTab?.webView ?: return
        layoutFindInPage.visibility = View.VISIBLE
        NovaMotion.slideDown(layoutFindInPage)
        etFindQuery.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT)

        attachFindListenerToActiveWebView()

        etFindQuery.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                hideKeyboard()
                tabManager.activeTab?.webView?.findNext(true)
                true
            } else {
                false
            }
        }

        etFindQuery.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            val currentWv = tabManager.activeTab?.webView
            if (query.isNotBlank()) {
                currentWv?.findAllAsync(query)
            } else {
                currentWv?.clearMatches()
                tvFindMatches.text = "0/0"
            }
        }

        btnFindPrev.setOnClickListener { tabManager.activeTab?.webView?.findNext(false) }
        btnFindNext.setOnClickListener { tabManager.activeTab?.webView?.findNext(true) }
        btnFindClose.setOnClickListener { closeFindInPage() }
    }

    private fun closeFindInPage() {
        tabManager.activeTab?.webView?.clearMatches()
        etFindQuery.setText("")
        tvFindMatches.text = "0/0"
        NovaMotion.slideUp(layoutFindInPage) {
            hideKeyboard()
        }
    }

    private fun attachFindListenerToActiveWebView() {
        val webView = tabManager.activeTab?.webView ?: return
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            val active = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
            tvFindMatches.text = "$active/$numberOfMatches"
        }
        val currentQuery = etFindQuery.text.toString()
        if (currentQuery.isNotBlank() && layoutFindInPage.visibility == View.VISIBLE) {
            webView.findAllAsync(currentQuery)
        }
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

        if (layoutFindInPage.visibility == View.VISIBLE) {
            attachFindListenerToActiveWebView()
        }

        if (tab.isPrivate) {
            ivPrivateBadge.visibility = View.VISIBLE
            ivSecurityIndicator.setColorFilter(ContextCompat.getColor(this, R.color.incognito_accent))
        } else {
            ivPrivateBadge.visibility = View.GONE
            val (_, decision) = controller.evaluateNavigation(tab.url)
            updateSecurityIndicator(decision.riskState)
        }

        updateShieldBadgeCount(tab.blockedAdsCount)
        updateNavigationButtons()
    }

    override fun onTabsUpdated(tabs: List<BrowserTab>) {
        tvTabCount.text = tabs.size.toString()
        updateNavigationButtons()
    }

    override fun onPageProgress(progress: Int) {
        NovaMotion.animateProgressBar(progressBar, progress)
        if (progress < 100) {
            btnReloadPage.setImageResource(R.drawable.ic_close)
            btnReloadPage.contentDescription = "Stop Loading"
        } else {
            swipeRefreshLayout.isRefreshing = false
            btnReloadPage.setImageResource(R.drawable.ic_refresh)
            btnReloadPage.contentDescription = "Reload"
        }
    }

    override fun onBlockedAdsUpdated(tab: BrowserTab, blockedCount: Int) {
        if (tab.id == tabManager.activeTab?.id) {
            runOnUiThread {
                updateShieldBadgeCount(blockedCount)
                if (blockedCount > 0) {
                    NovaMotion.pulseBadge(layoutShieldBadge)
                }
            }
        }
    }

    private fun updateShieldBadgeCount(count: Int) {
        if (count > 0) {
            tvShieldBadgeCount.visibility = View.VISIBLE
            tvShieldBadgeCount.text = if (count > 99) "99+" else count.toString()
        } else {
            tvShieldBadgeCount.visibility = View.GONE
        }
    }

    override fun onSecurityIntervention(decision: SecurityDecision, targetUrl: String, onProceed: () -> Unit) {
        pendingSecurityProceed = onProceed
        updateSecurityIndicator(decision.riskState)

        val intent = Intent(this, SecurityWarningActivity::class.java).apply {
            putExtra(SecurityWarningActivity.EXTRA_TARGET_URL, targetUrl)
            putExtra(SecurityWarningActivity.EXTRA_CANONICAL_URL, decision.canonicalUrl)
            putExtra(SecurityWarningActivity.EXTRA_ACTION, decision.action.name)
            putExtra(SecurityWarningActivity.EXTRA_RISK_STATE, decision.riskState.name)
            putStringArrayListExtra(SecurityWarningActivity.EXTRA_REASONS, ArrayList(decision.reasons))
            putExtra(SecurityWarningActivity.EXTRA_RULE_ID, decision.matchedRuleId)
            putExtra(SecurityWarningActivity.EXTRA_FEED_SOURCE, decision.feedSource)
            putExtra(SecurityWarningActivity.EXTRA_RISK_SCORE, decision.riskScore)
        }
        securityLauncher.launch(intent)
    }

    override fun onSitePermissionPrompt(
        canonicalOrigin: String,
        permissions: List<String>,
        onAllow: () -> Unit,
        onDeny: () -> Unit
    ) {
        runOnUiThread {
            val readablePerms = permissions.joinToString(", ") { p ->
                when (p) {
                    com.gintama.novabrowser.browser.SitePermissionType.CAMERA -> "Camera"
                    com.gintama.novabrowser.browser.SitePermissionType.MICROPHONE -> "Microphone"
                    com.gintama.novabrowser.browser.SitePermissionType.GEOLOCATION -> "Location"
                    com.gintama.novabrowser.browser.SitePermissionType.PROTECTED_MEDIA -> "Protected Media ID"
                    else -> p
                }
            }

            // Check if native Android runtime permissions are needed
            val neededAndroidPerms = mutableListOf<String>()
            if (permissions.contains(com.gintama.novabrowser.browser.SitePermissionType.CAMERA) &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                neededAndroidPerms.add(android.Manifest.permission.CAMERA)
            }
            if (permissions.contains(com.gintama.novabrowser.browser.SitePermissionType.MICROPHONE) &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                neededAndroidPerms.add(android.Manifest.permission.RECORD_AUDIO)
            }
            if (permissions.contains(com.gintama.novabrowser.browser.SitePermissionType.GEOLOCATION) &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededAndroidPerms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }

            AlertDialog.Builder(this)
                .setTitle("Site Permission Request")
                .setMessage("Website:\n$canonicalOrigin\n\nRequests access to:\n• $readablePerms\n\nPermission will be strictly locked to this canonical origin.")
                .setPositiveButton("Allow") { _, _ ->
                    if (neededAndroidPerms.isNotEmpty()) {
                        requestPermissions(neededAndroidPerms.toTypedArray(), 1001)
                    }
                    onAllow()
                }
                .setNegativeButton("Block") { _, _ ->
                    onDeny()
                }
                .setOnCancelListener {
                    onDeny()
                }
                .show()
        }
    }

    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = filePathCallback

        val intent = try {
            fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        } catch (e: Exception) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        }

        val title = fileChooserParams?.title?.takeIf { it.isNotBlank() } ?: "Choose File"
        return try {
            fileUploadLauncher.launch(Intent.createChooser(intent, title))
            true
        } catch (e: ActivityNotFoundException) {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            Toast.makeText(this, "No file manager found to select files", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            onHideCustomView()
        }

        customView = view
        customViewCallback = callback
        originalSystemUiVisibility = window.decorView.systemUiVisibility
        originalOrientation = requestedOrientation

        fullscreenCustomViewContainer.removeAllViews()
        fullscreenCustomViewContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenCustomViewContainer.visibility = View.VISIBLE

        topChromeHeader.visibility = View.GONE
        bottomFloatingIsland.visibility = View.GONE
        mainViewportContainer.visibility = View.GONE

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        fullscreenCustomViewContainer.removeView(view)
        fullscreenCustomViewContainer.visibility = View.GONE

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null

        window.decorView.systemUiVisibility = originalSystemUiVisibility
        requestedOrientation = originalOrientation

        topChromeHeader.visibility = View.VISIBLE
        bottomFloatingIsland.visibility = View.VISIBLE
        mainViewportContainer.visibility = View.VISIBLE
        updateNavigationButtons()
    }

    override fun onCreateWindow(isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        val newTab = tabManager.createTab("about:blank", isPrivate = tabManager.activeTab?.isPrivate == true)
        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = newTab.webView
        resultMsg?.sendToTarget()
        showWebView()
        return true
    }

    override fun onCloseWindow(window: WebView?) {
        val tabToClose = tabManager.getTabsList().firstOrNull { it.webView == window }
        if (tabToClose != null) {
            tabManager.closeTab(tabToClose.id)
            if (tabManager.tabCount == 0) {
                tabManager.createTab("about:blank")
                showStartCanvas()
            }
        }
    }

    override fun onJsAlert(message: String, result: JsResult) {
        AlertDialog.Builder(this)
            .setTitle("Page Notice")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result.confirm() }
            .setOnCancelListener { result.confirm() }
            .show()
    }

    override fun onJsConfirm(message: String, result: JsResult) {
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result.confirm() }
            .setNegativeButton("Cancel") { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
    }

    override fun onJsPrompt(message: String, defaultValue: String, result: JsPromptResult) {
        val input = EditText(this).apply {
            setText(defaultValue)
            if (defaultValue.isNotEmpty()) setSelection(defaultValue.length)
        }
        val container = FrameLayout(this).apply {
            setPadding(48, 16, 48, 16)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Prompt")
            .setMessage(message)
            .setView(container)
            .setPositiveButton("OK") { _, _ -> result.confirm(input.text.toString()) }
            .setNegativeButton("Cancel") { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
    }

    override fun onReceivedSslError(error: SslError, onProceed: () -> Unit, onCancel: () -> Unit) {
        val primaryError = when (error.primaryError) {
            SslError.SSL_NOTYETVALID -> "Certificate is not yet valid"
            SslError.SSL_EXPIRED -> "Certificate has expired"
            SslError.SSL_IDMISMATCH -> "Hostname does not match certificate"
            SslError.SSL_UNTRUSTED -> "Certificate authority is untrusted"
            SslError.SSL_DATE_INVALID -> "Certificate date is invalid"
            else -> "Certificate validation failed"
        }
        val targetUrl = error.url ?: "this site"
        AlertDialog.Builder(this)
            .setTitle("Security Alert: SSL Failure")
            .setMessage("The SSL certificate for $targetUrl failed verification:\n\n• $primaryError\n\nYour connection to this site is not secure.")
            .setNegativeButton("Back to Safety") { _, _ -> onCancel() }
            .setPositiveButton("Proceed Anyway (Unsafe)") { _, _ -> onProceed() }
            .setOnCancelListener { onCancel() }
            .show()
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
        val isBrowsing = layoutNewTabCanvas.visibility != View.VISIBLE

        btnNavBack.visibility = if (isBrowsing || canBack) View.VISIBLE else View.GONE
        btnNavBack.alpha = if (canBack || isBrowsing) 1.0f else 0.4f
        btnReloadPage.visibility = if (isBrowsing) View.VISIBLE else View.GONE
    }

    override fun onBackPressed() {
        if (customView != null) {
            onHideCustomView()
            return
        }
        if (layoutFindInPage.visibility == View.VISIBLE) {
            closeFindInPage()
            return
        }
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

    override fun onDestroy() {
        if (customView != null) {
            onHideCustomView()
        }
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        super.onDestroy()
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
