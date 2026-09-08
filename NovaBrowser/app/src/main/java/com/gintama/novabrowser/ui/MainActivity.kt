package com.gintama.novabrowser.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import com.gintama.novabrowser.reader.ReaderActivity
import com.gintama.novabrowser.reader.ReaderExtractor
import com.gintama.novabrowser.settings.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.speech.RecognizerIntent
import android.content.pm.ActivityInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.gintama.novabrowser.downloads.DownloadsActivity
import com.gintama.novabrowser.downloads.MediaSnifferEngine
import com.gintama.novabrowser.wallpaper.NovaWallpaperManager
import com.gintama.novabrowser.security.NovaBiometricHelper
import com.gintama.novabrowser.media.TabMuteEngine
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
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
import androidx.cardview.widget.CardView
import com.gintama.novabrowser.ui.omnibox.OmniboxState
import com.gintama.novabrowser.ui.omnibox.OmniboxSuggestion
import com.gintama.novabrowser.ui.omnibox.OmniboxSuggestionsAdapter
import com.gintama.novabrowser.ui.omnibox.SuggestionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity(), TabChangeListener {

    private lateinit var controller: BrowserController
    private lateinit var downloadHandler: DownloadHandler
    private lateinit var tabManager: TabManager
    private lateinit var quadViewManager: com.gintama.novabrowser.ui.quad.QuadViewManager

    // Top Header & Address Bar Views
    private lateinit var topChromeHeader: LinearLayout
    private lateinit var headerCapsulePill: LinearLayout
    private lateinit var etUrlInput: EditText
    private lateinit var btnClearUrl: ImageButton
    private lateinit var btnReloadPage: ImageButton
    private lateinit var btnReaderMode: ImageButton
    private lateinit var btnTabMute: ImageButton
    private lateinit var btnTabs: FrameLayout
    private lateinit var viewTabCountSquircle: View
    private lateinit var tvTabCount: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var ivSecurityIndicator: ImageView
    private lateinit var ivPrivateBadge: ImageView
    private lateinit var layoutShieldBadge: View
    private lateinit var tvShieldBadgeCount: TextView
    private lateinit var btnNavBack: ImageButton

    // Standalone PWA Mode Views
    private lateinit var layoutStandaloneHeader: LinearLayout
    private lateinit var btnStandaloneBack: ImageButton
    private lateinit var ivStandaloneLock: ImageView
    private lateinit var tvStandaloneTitle: TextView
    private lateinit var btnStandaloneReload: ImageButton
    private lateinit var btnStandaloneMenu: ImageButton
    private var isStandaloneMode: Boolean = false

    // Viewport Containers
    private lateinit var mainViewportContainer: FrameLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webViewContainer: FrameLayout
    private lateinit var layoutNewTabCanvas: ScrollView
    private lateinit var layoutPrivateCanvas: ScrollView
    private lateinit var ivStartCanvasWallpaper: ImageView
    private lateinit var viewWallpaperDimmer: View
    private lateinit var etPrivateSearchInput: EditText
    private lateinit var fullscreenCustomViewContainer: FrameLayout

    // Fullscreen Video State
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility: Int = 0
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    // Biometric Session States
    private var isPrivateSessionUnlocked = false
    private var isAppUnlocked = false

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

    // Activity Result Launcher for Custom Wallpaper Selection
    private val wallpaperGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = NovaWallpaperManager.saveCustomWallpaper(this, uri)
            if (saved) {
                NovaWallpaperManager.applyWallpaper(this, ivStartCanvasWallpaper, viewWallpaperDimmer)
                Toast.makeText(this, "Custom wallpaper applied!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to load custom wallpaper", Toast.LENGTH_SHORT).show()
            }
        }
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

    // Omnibox State Machine & Floating Suggestions
    private var omniboxState: OmniboxState = OmniboxState.IDLE
    private var suggestionQueryJob: Job? = null
    private lateinit var suggestionsAdapter: OmniboxSuggestionsAdapter
    private lateinit var layoutOmniboxSuggestions: CardView
    private lateinit var rvOmniboxSuggestions: RecyclerView

    // In-Page Error Recovery
    private lateinit var layoutPageErrorRecovery: View
    private lateinit var tvErrorTitle: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnErrorBack: Button
    private lateinit var btnErrorRetry: Button

    // Bottom Navigation Dock auto-hide state
    private var isDockHidden = false

    // Horizon Adaptive Navigation Dock
    private lateinit var bottomFloatingIsland: LinearLayout
    private lateinit var btnDockBack: ImageButton
    private lateinit var btnDockForward: ImageButton
    private lateinit var btnDockTabs: FrameLayout
    private lateinit var viewDockTabSquircle: View
    private lateinit var tvDockTabCount: TextView
    private lateinit var btnDockShare: ImageButton
    private lateinit var btnDockMenu: ImageButton

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
        com.gintama.novabrowser.shields.SiteShieldManager.init(this)
        com.gintama.novabrowser.notifications.NovaNotificationHelper.initChannels(this)

        // Security: Disable WebView debugging in production releases
        WebView.setWebContentsDebuggingEnabled(com.gintama.novabrowser.BuildConfig.DEBUG)

        // Initialize Google Safe Browsing provider
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.START_SAFE_BROWSING)) {
            androidx.webkit.WebViewCompat.startSafeBrowsing(applicationContext) { _ ->
                // Safe browsing service initialized
            }
        }

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
        if (::layoutNewTabCanvas.isInitialized && layoutNewTabCanvas.visibility == View.VISIBLE && tabManager.activeTab?.isPrivate != true) {
            NovaWallpaperManager.applyWallpaper(this, ivStartCanvasWallpaper, viewWallpaperDimmer)
        }
        if (NovaBiometricHelper.isAppLockEnabled(this) && !isAppUnlocked) {
            mainViewportContainer.visibility = View.INVISIBLE
            topChromeHeader.visibility = View.INVISIBLE
            bottomFloatingIsland.visibility = View.INVISIBLE
            NovaBiometricHelper.authenticate(
                activity = this,
                title = "Unlock NovaBrowser",
                subtitle = "Verify identity to open the browser",
                onSuccess = {
                    isAppUnlocked = true
                    mainViewportContainer.visibility = View.VISIBLE
                    topChromeHeader.visibility = View.VISIBLE
                    bottomFloatingIsland.visibility = View.VISIBLE
                },
                onError = {
                    Toast.makeText(this, "Authentication required to open browser", Toast.LENGTH_SHORT).show()
                    finish()
                }
            )
        }
    }

    private fun initViews() {
        topChromeHeader = findViewById(R.id.topChromeHeader)
        headerCapsulePill = findViewById(R.id.headerCapsulePill)
        etUrlInput = findViewById(R.id.etUrlInput)
        btnClearUrl = findViewById(R.id.btnClearUrl)
        btnReloadPage = findViewById(R.id.btnReloadPage)
        btnReaderMode = findViewById(R.id.btnReaderMode)
        btnTabMute = findViewById(R.id.btnTabMute)
        btnTabs = findViewById(R.id.btnTabs)
        viewTabCountSquircle = findViewById(R.id.viewTabCountSquircle)
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
        layoutPrivateCanvas = findViewById(R.id.layoutPrivateCanvas)
        ivStartCanvasWallpaper = findViewById(R.id.ivStartCanvasWallpaper)
        viewWallpaperDimmer = findViewById(R.id.viewWallpaperDimmer)
        etPrivateSearchInput = findViewById(R.id.etPrivateSearchInput)
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
        btnDockBack = findViewById(R.id.btnDockBack)
        btnDockForward = findViewById(R.id.btnDockForward)
        btnDockTabs = findViewById(R.id.btnDockTabs)
        viewDockTabSquircle = findViewById(R.id.viewDockTabSquircle)
        tvDockTabCount = findViewById(R.id.tvDockTabCount)
        btnDockShare = findViewById(R.id.btnDockShare)
        btnDockMenu = findViewById(R.id.btnDockMenu)

        // Standalone PWA views
        layoutStandaloneHeader = findViewById(R.id.layoutStandaloneHeader)
        btnStandaloneBack = findViewById(R.id.btnStandaloneBack)
        ivStandaloneLock = findViewById(R.id.ivStandaloneLock)
        tvStandaloneTitle = findViewById(R.id.tvStandaloneTitle)
        btnStandaloneReload = findViewById(R.id.btnStandaloneReload)
        btnStandaloneMenu = findViewById(R.id.btnStandaloneMenu)

        btnStandaloneBack.setOnClickListener {
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                finish()
            }
        }
        btnStandaloneReload.setOnClickListener {
            val webView = tabManager.activeTab?.webView
            if (webView != null) {
                if (webView.progress < 100) webView.stopLoading() else webView.reload()
            }
        }
        btnStandaloneMenu.setOnClickListener { view ->
            showOptionsMenu(view)
        }

        // Omnibox floating suggestions
        layoutOmniboxSuggestions = findViewById(R.id.layoutOmniboxSuggestions)
        rvOmniboxSuggestions = findViewById(R.id.rvOmniboxSuggestions)
        suggestionsAdapter = OmniboxSuggestionsAdapter(
            onItemClick = { suggestion -> onSuggestionSelected(suggestion) },
            onInsertClick = { suggestion -> onSuggestionInserted(suggestion) }
        )
        rvOmniboxSuggestions.layoutManager = LinearLayoutManager(this)
        rvOmniboxSuggestions.adapter = suggestionsAdapter

        // In-page error recovery card
        layoutPageErrorRecovery = findViewById(R.id.layoutPageErrorRecovery)
        tvErrorTitle = findViewById(R.id.tvErrorTitle)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnErrorBack = findViewById(R.id.btnErrorBack)
        btnErrorRetry = findViewById(R.id.btnErrorRetry)
    }

    private fun setupWindowInsets() {
        val topChrome = findViewById<View>(R.id.topChromeHeader)
        val root = findViewById<View>(R.id.rootLayout)

        // Immediate fallback so there is never a visual collision on startup
        val fallbackStatusHeight = getStatusBarHeightFallback()
        topChrome.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = fallbackStatusHeight
        }
        layoutStandaloneHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
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
            layoutStandaloneHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
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

        quadViewManager = com.gintama.novabrowser.ui.quad.QuadViewManager(
            context = this,
            tabManager = tabManager,
            workspaceRoot = findViewById(R.id.layoutQuadWorkspace),
            onExitQuadView = { activeTab ->
                swipeRefreshLayout.isEnabled = true
                webViewContainer.visibility = View.VISIBLE
                val target = activeTab ?: tabManager.activeTab
                if (target != null) {
                    tabManager.switchTab(target.id)
                    onActiveTabChanged(target)
                } else {
                    showStartCanvas()
                }
            },
            onActiveTabChanged = { activeTab ->
                onActiveTabChanged(activeTab)
            }
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

    fun enterQuadView(selectedTab: BrowserTab) {
        layoutPageErrorRecovery.visibility = View.GONE
        layoutOmniboxSuggestions.visibility = View.GONE
        layoutNewTabCanvas.visibility = View.GONE
        layoutPrivateCanvas.visibility = View.GONE
        ivStartCanvasWallpaper.visibility = View.GONE
        viewWallpaperDimmer.visibility = View.GONE
        swipeRefreshLayout.isEnabled = false
        webViewContainer.visibility = View.GONE

        // Safe reparenting: detach from single viewport container without resetting session
        (selectedTab.webView.parent as? ViewGroup)?.removeView(selectedTab.webView)

        quadViewManager.enterQuadView(selectedTab)
    }

    private fun setupListeners() {
        // Address bar in header with Omnibox State Machine
        etUrlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                setOmniboxState(OmniboxState.SUBMITTING)
                layoutOmniboxSuggestions.visibility = View.GONE
                hideKeyboard()
                etUrlInput.clearFocus()
                val text = etUrlInput.text.toString()
                loadUrlInActiveTab(text)
                true
            } else {
                false
            }
        }

        // Omnibox Click & Focus Behavior (Select all, expand URL, clear button)
        etUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setOmniboxState(OmniboxState.FOCUS)
                val activeTab = tabManager.activeTab
                if (activeTab != null && activeTab.url != "about:blank" && activeTab.url.isNotBlank()) {
                    etUrlInput.setText(activeTab.url)
                }
                etUrlInput.post { etUrlInput.selectAll() }
                if (etUrlInput.text.isNotEmpty()) {
                    btnClearUrl.visibility = View.VISIBLE
                    btnReloadPage.visibility = View.GONE
                }
            } else {
                setOmniboxState(OmniboxState.IDLE)
                layoutOmniboxSuggestions.visibility = View.GONE
                btnClearUrl.visibility = View.GONE
                val activeTab = tabManager.activeTab
                val isBrowsing = activeTab != null && activeTab.url != "about:blank" && activeTab.url.isNotBlank()
                btnReloadPage.visibility = if (isBrowsing) View.VISIBLE else View.GONE
                if (activeTab != null) {
                    etUrlInput.setText(if (isBrowsing) activeTab.url else "")
                }
            }
        }

        etUrlInput.setOnClickListener {
            etUrlInput.selectAll()
        }

        etUrlInput.doAfterTextChanged { s ->
            if (etUrlInput.hasFocus()) {
                if (!s.isNullOrEmpty()) {
                    btnClearUrl.visibility = View.VISIBLE
                    btnReloadPage.visibility = View.GONE
                    setOmniboxState(OmniboxState.EDITING)
                    suggestionQueryJob?.cancel()
                    suggestionQueryJob = lifecycleScope.launch {
                        delay(220)
                        queryOmniboxSuggestions(s.toString())
                    }
                } else {
                    btnClearUrl.visibility = View.GONE
                    layoutOmniboxSuggestions.visibility = View.GONE
                }
            }
        }

        btnClearUrl.setOnClickListener {
            etUrlInput.setText("")
            layoutOmniboxSuggestions.visibility = View.GONE
            etUrlInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etUrlInput, InputMethodManager.SHOW_IMPLICIT)
        }

        headerCapsulePill.setOnClickListener {
            etUrlInput.requestFocus()
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

        // Omnibox on Private Start Canvas
        etPrivateSearchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                hideKeyboard()
                val text = etPrivateSearchInput.text.toString()
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
        NovaMotion.attachThrottledClick(btnTabs, 400L) { showTabsDialog() }

        // Navigation Back & In-Pill Quick Reload
        NovaMotion.attachThrottledClick(btnNavBack, 300L) {
            if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
                quadViewManager.onBackPressed()
                return@attachThrottledClick
            }
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                showStartCanvas()
            }
        }

        NovaMotion.attachThrottledClick(btnReloadPage, 400L) {
            val webView = tabManager.activeTab?.webView
            if (webView != null) {
                if (webView.progress < 100) {
                    webView.stopLoading()
                    btnReloadPage.setImageResource(R.drawable.ic_refresh)
                } else {
                    NovaMotion.spinReloadIcon(btnReloadPage)
                    webView.reload()
                }
            }
        }

        btnReaderMode.setOnClickListener {
            launchReaderMode()
        }

        btnTabMute.setOnClickListener {
            toggleActiveTabMute()
        }

        // Horizon Adaptive Dock Interactions (with rapid-tap protection)
        NovaMotion.attachThrottledClick(btnDockBack, 300L) {
            if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
                quadViewManager.onBackPressed()
                return@attachThrottledClick
            }
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                showStartCanvas()
            }
        }

        NovaMotion.attachThrottledClick(btnDockForward, 300L) {
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoForward() == true) {
                webView.goForward()
            }
        }

        NovaMotion.attachThrottledClick(btnDockTabs, 400L) {
            showTabsDialog()
        }

        NovaMotion.attachThrottledClick(btnDockShare, 400L) {
            val tab = tabManager.activeTab
            val url = tab?.url.orEmpty()
            if (url.isNotBlank() && url != "about:blank") {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Link"))
            } else {
                Toast.makeText(this, "Open a website to share", Toast.LENGTH_SHORT).show()
            }
        }

        NovaMotion.attachThrottledClick(btnDockMenu, 400L) {
            showPageActionsSheet()
        }

        // Top Chrome & Indicator interactions
        NovaMotion.attachThrottledClick(btnMenu, 400L) {
            showPageActionsSheet()
        }

        layoutShieldBadge.setOnClickListener {
            showSiteShieldsBottomSheet()
        }

        ivSecurityIndicator.setOnClickListener {
            showSiteShieldsBottomSheet()
        }

        // Pull to refresh (Tuned with physical scroll bounds check)
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
            webView?.canScrollVertically(-1) == true
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

        findViewById<View>(R.id.chipCustomizeWallpaper)?.setOnClickListener {
            showWallpaperPickerDialog()
        }

        setupStartPageActions()
        setupFrequentlyVisited()
    }

    private fun setupStartPageActions() {
        val btnStartPrivateToggle = findViewById<View>(R.id.btnStartPrivateToggle)
        val btnStartBookmarks = findViewById<View>(R.id.btnStartBookmarks)
        val btnStartHistory = findViewById<View>(R.id.btnStartHistory)
        val btnShieldReport = findViewById<View>(R.id.btnShieldReport)
        val btnShieldInsights = findViewById<View>(R.id.btnShieldInsights)

        btnStartPrivateToggle?.setOnClickListener {
            val active = tabManager.activeTab
            if (active?.isPrivate == true) {
                val standardTab = tabManager.getStandardTabs().firstOrNull()
                if (standardTab != null) {
                    tabManager.switchTab(standardTab.id)
                } else {
                    tabManager.createTab("about:blank", isPrivate = false)
                }
            } else {
                val privateTab = tabManager.getPrivateTabs().firstOrNull()
                if (privateTab != null) {
                    tabManager.switchTab(privateTab.id)
                } else {
                    tabManager.createTab("about:blank", isPrivate = true)
                }
            }
        }

        btnStartBookmarks?.setOnClickListener {
            val intent = Intent(this, BookmarksActivity::class.java)
            contentLauncher.launch(intent)
        }

        btnStartHistory?.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            contentLauncher.launch(intent)
        }

        btnShieldReport?.setOnClickListener {
            showSiteShieldsBottomSheet()
        }

        btnShieldInsights?.setOnClickListener {
            showSiteShieldsBottomSheet()
        }
    }

    private fun updateStartPagePrivateCapsule(isPrivate: Boolean) {
        val btnStartPrivateToggle = findViewById<View>(R.id.btnStartPrivateToggle) ?: return
        val ivStartPrivateIcon = findViewById<ImageView>(R.id.ivStartPrivateIcon)
        val tvStartPrivateLabel = findViewById<TextView>(R.id.tvStartPrivateLabel)

        if (isPrivate) {
            btnStartPrivateToggle.setBackgroundResource(R.drawable.bg_pill_private_active)
            ivStartPrivateIcon?.setColorFilter(ContextCompat.getColor(this, R.color.incognito_accent))
            tvStartPrivateLabel?.setTextColor(ContextCompat.getColor(this, R.color.incognito_text))
            tvStartPrivateLabel?.text = "Private On"
        } else {
            btnStartPrivateToggle.setBackgroundResource(R.drawable.bg_pill_subtle)
            ivStartPrivateIcon?.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            tvStartPrivateLabel?.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvStartPrivateLabel?.text = "Private"
        }
    }

    private fun setupFrequentlyVisited() {
        val rvFrequentlyVisited = findViewById<RecyclerView>(R.id.rvFrequentlyVisited) ?: return
        val layoutFrequentlyVisitedHeader = findViewById<View>(R.id.layoutFrequentlyVisitedHeader)
        val btnFrequentlyVisitedClear = findViewById<View>(R.id.btnFrequentlyVisitedClear)

        rvFrequentlyVisited.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val sampleItems = listOf(
            FrequentlyVisitedItem(
                title = "Design Systems Repo",
                domain = "designsystemsrepo.com",
                url = "https://designsystemsrepo.com",
                relativeTime = "2 hours ago",
                monogram = "D"
            ),
            FrequentlyVisitedItem(
                title = "Hacker News — Best",
                domain = "news.ycombinator.com",
                url = "https://news.ycombinator.com",
                relativeTime = "Yesterday",
                monogram = "Y"
            ),
            FrequentlyVisitedItem(
                title = "Stripe Docs — API",
                domain = "docs.stripe.com",
                url = "https://docs.stripe.com",
                relativeTime = "3 days ago",
                monogram = "S"
            ),
            FrequentlyVisitedItem(
                title = "ArXiv Computer Science",
                domain = "arxiv.org",
                url = "https://arxiv.org",
                relativeTime = "This week",
                monogram = "A"
            ),
            FrequentlyVisitedItem(
                title = "Wikipedia Articles",
                domain = "wikipedia.org",
                url = "https://wikipedia.org",
                relativeTime = "Last week",
                monogram = "W"
            )
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = NovaDatabaseHelper.getInstance(this@MainActivity)
            val history = db.getRecentHistory(15)
            val visitedItems = ArrayList<FrequentlyVisitedItem>()
            val seenDomains = HashSet<String>()

            for (h in history) {
                val host = try {
                    java.net.URI(h.url).host?.removePrefix("www.")
                } catch (_: Exception) { null }
                if (!host.isNullOrBlank() && !seenDomains.contains(host)) {
                    seenDomains.add(host)
                    val mono = host.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                    val relativeSpan = android.text.format.DateUtils.getRelativeTimeSpanString(
                        h.visitedAt,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                    visitedItems.add(
                        FrequentlyVisitedItem(
                            title = h.title?.ifBlank { host } ?: host,
                            domain = host,
                            url = h.url,
                            relativeTime = relativeSpan,
                            monogram = mono
                        )
                    )
                }
                if (visitedItems.size >= 5) break
            }

            val finalItems = if (visitedItems.size >= 3) visitedItems else sampleItems

            withContext(Dispatchers.Main) {
                val adapter = FrequentlyVisitedAdapter(finalItems) { item ->
                    loadUrlInActiveTab(item.url)
                }
                rvFrequentlyVisited.adapter = adapter
            }
        }

        btnFrequentlyVisitedClear?.setOnClickListener {
            rvFrequentlyVisited.visibility = View.GONE
            layoutFrequentlyVisitedHeader?.visibility = View.GONE
            Toast.makeText(this, "Frequently visited cleared", Toast.LENGTH_SHORT).show()
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
            btnDockBack, btnDockForward, btnDockTabs, btnDockShare, btnDockMenu,
            btnTabs, btnMenu, btnNavBack, btnReloadPage, layoutShieldBadge,
            btnSearchEnginePicker, btnOmniboxMic
        )
        findViewById<View>(R.id.btnStartPrivateToggle)?.let { NovaMotion.attachSpringTouchFeedback(it) }
        findViewById<View>(R.id.btnStartBookmarks)?.let { NovaMotion.attachSpringTouchFeedback(it) }
        findViewById<View>(R.id.btnStartHistory)?.let { NovaMotion.attachSpringTouchFeedback(it) }
        findViewById<View>(R.id.btnShieldReport)?.let { NovaMotion.attachSpringTouchFeedback(it) }
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
        layoutPageErrorRecovery.visibility = View.GONE
        layoutOmniboxSuggestions.visibility = View.GONE
        val searchTemplate = SearchEngineManager.getActiveSearchTemplate(this)
        val (sanitizedUrl, decision) = controller.evaluateNavigation(rawInput, searchEngineTemplate = searchTemplate)
        updateSecurityIndicator(decision.riskState)

        val targetTab = if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
            quadViewManager.getActiveTab() ?: tabManager.activeTab
        } else {
            tabManager.activeTab
        }

        if (decision.action == GateAction.BLOCK || decision.action == GateAction.WARN) {
            onSecurityIntervention(decision, sanitizedUrl) {
                targetTab?.webView?.loadUrl(sanitizedUrl)
                etUrlInput.setText(sanitizedUrl)
                if (::quadViewManager.isInitialized && !quadViewManager.state.isQuadActive) {
                    showWebView()
                }
            }
        } else {
            if (targetTab != null) {
                targetTab.webView.loadUrl(sanitizedUrl)
                etUrlInput.setText(sanitizedUrl)
                if (::quadViewManager.isInitialized && !quadViewManager.state.isQuadActive) {
                    showWebView()
                }
            }
        }
    }

    private fun showStartCanvas() {
        layoutPageErrorRecovery.visibility = View.GONE
        layoutOmniboxSuggestions.visibility = View.GONE
        showDockIsland()
        val isPrivate = tabManager.activeTab?.isPrivate == true
        updateStartPagePrivateCapsule(isPrivate)
        if (isPrivate) {
            NovaMotion.crossFade(swipeRefreshLayout, layoutPrivateCanvas)
            layoutNewTabCanvas.visibility = View.GONE
            ivStartCanvasWallpaper.visibility = View.GONE
            viewWallpaperDimmer.visibility = View.GONE
        } else {
            NovaWallpaperManager.applyWallpaper(this, ivStartCanvasWallpaper, viewWallpaperDimmer)
            ivStartCanvasWallpaper.visibility = View.VISIBLE
            NovaMotion.crossFade(swipeRefreshLayout, layoutNewTabCanvas)
            layoutPrivateCanvas.visibility = View.GONE
        }
        btnReaderMode.visibility = View.GONE
        btnTabMute.visibility = View.GONE
        etUrlInput.setText("")
        updateNavigationButtons()
        if (!isPrivate) {
            NovaMotion.animateCountUp(
                tvCanvasBlockedCount,
                AdBlockEngine.getLifetimeBlockedCount()
            )
        }
    }

    private fun showWebView() {
        layoutPageErrorRecovery.visibility = View.GONE
        layoutOmniboxSuggestions.visibility = View.GONE
        if (layoutNewTabCanvas.visibility == View.VISIBLE) {
            NovaMotion.crossFade(layoutNewTabCanvas, swipeRefreshLayout)
        } else if (layoutPrivateCanvas.visibility == View.VISIBLE) {
            NovaMotion.crossFade(layoutPrivateCanvas, swipeRefreshLayout)
        } else {
            swipeRefreshLayout.visibility = View.VISIBLE
        }
        layoutNewTabCanvas.visibility = View.GONE
        layoutPrivateCanvas.visibility = View.GONE
        ivStartCanvasWallpaper.visibility = View.GONE
        viewWallpaperDimmer.visibility = View.GONE
        btnTabMute.visibility = View.VISIBLE
        updateTabMuteIndicator(tabManager.activeTab?.isMuted == true)
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

    private fun showSiteShieldsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_site_shields, null)
        dialog.setContentView(view)

        val tab = tabManager.activeTab
        val currentUrl = tab?.url.orEmpty()
        val siteHost = com.gintama.novabrowser.shields.SiteShieldManager.normalizeDomain(currentUrl).ifBlank { "Local Canvas" }
        val isLocalCanvas = siteHost == "Local Canvas" || currentUrl.isBlank() || currentUrl == "about:blank"

        val ivHeaderIcon = view.findViewById<ImageView>(R.id.ivShieldHeaderIcon)
        val tvSiteDomain = view.findViewById<TextView>(R.id.tvShieldSiteDomain)
        val ivLockIcon = view.findViewById<ImageView>(R.id.ivConnectionLockIcon)
        val tvConnectionStatus = view.findViewById<TextView>(R.id.tvShieldConnectionStatus)
        val btnDismiss = view.findViewById<ImageButton>(R.id.btnDismissShields)

        val tvMasterTitle = view.findViewById<TextView>(R.id.tvMasterShieldTitle)
        val tvMasterSubtitle = view.findViewById<TextView>(R.id.tvMasterShieldSubtitle)
        val switchMaster = view.findViewById<SwitchMaterial>(R.id.switchMasterShields)

        val tvBlockedPage = view.findViewById<TextView>(R.id.tvShieldBlockedThisPage)
        val tvJsStatus = view.findViewById<TextView>(R.id.tvShieldJsStatus)
        val tvCookiesStatus = view.findViewById<TextView>(R.id.tvShieldCookiesStatus)

        val switchAdBlock = view.findViewById<SwitchMaterial>(R.id.switchSiteAdBlock)
        val switchCosmetic = view.findViewById<SwitchMaterial>(R.id.switchSiteCosmetic)
        val switchJs = view.findViewById<SwitchMaterial>(R.id.switchSiteJavaScript)
        val switchThirdPartyCookies = view.findViewById<SwitchMaterial>(R.id.switchSiteThirdPartyCookies)

        val layoutPermissions = view.findViewById<View>(R.id.layoutPermissionsSection)
        val tvPermissions = view.findViewById<TextView>(R.id.tvSitePermissionsSummary)
        val btnResetPerms = view.findViewById<TextView>(R.id.btnResetPermissions)

        val btnClearData = view.findViewById<Button>(R.id.btnClearSiteData)
        val btnAudit = view.findViewById<Button>(R.id.btnTechnicalAudit)
        val btnReport = view.findViewById<Button>(R.id.btnReportBrokenSite)
        val btnDone = view.findViewById<Button>(R.id.btnDoneShields)

        tvSiteDomain.text = siteHost
        btnDismiss.setOnClickListener { dialog.dismiss() }
        btnDone.setOnClickListener { dialog.dismiss() }

        // Security / Connection Indicator
        if (isLocalCanvas) {
            ivLockIcon.setImageResource(R.drawable.ic_shield_verified)
            ivLockIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
            tvConnectionStatus.text = "Internal Protected Environment"
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
        } else if (currentUrl.startsWith("https://", ignoreCase = true)) {
            ivLockIcon.setImageResource(R.drawable.ic_lock)
            ivLockIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
            tvConnectionStatus.text = "HTTPS Secured • TLS Encrypted"
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
        } else {
            ivLockIcon.setImageResource(R.drawable.ic_link_off)
            ivLockIcon.setColorFilter(ContextCompat.getColor(this, R.color.risk_suspicious))
            tvConnectionStatus.text = "Insecure Connection (HTTP)"
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.risk_suspicious))
        }

        val blockedCount = tab?.blockedAdsCount ?: 0
        tvBlockedPage.text = blockedCount.toString()

        if (isLocalCanvas) {
            switchMaster.isEnabled = false
            switchAdBlock.isEnabled = false
            switchCosmetic.isEnabled = false
            switchJs.isEnabled = false
            switchThirdPartyCookies.isEnabled = false
            btnClearData.visibility = View.GONE
            btnReport.visibility = View.GONE
            layoutPermissions.visibility = View.GONE
            tvMasterTitle.text = "Local Canvas Protected"
            tvMasterSubtitle.text = "Zero remote tracking or network subresources"
        } else {
            var currentSettings = com.gintama.novabrowser.shields.SiteShieldManager.getSettingsForSite(siteHost)

            fun updateUIStates(s: com.gintama.novabrowser.shields.SiteShieldSettings) {
                switchMaster.isChecked = s.shieldsEnabled
                switchAdBlock.isChecked = s.adBlockEnabled
                switchCosmetic.isChecked = s.cosmeticEnabled
                switchJs.isChecked = s.javaScriptEnabled
                switchThirdPartyCookies.isChecked = s.blockThirdPartyCookies

                val isMasterUp = s.shieldsEnabled
                switchAdBlock.isEnabled = isMasterUp
                switchCosmetic.isEnabled = isMasterUp
                switchJs.isEnabled = isMasterUp
                switchThirdPartyCookies.isEnabled = isMasterUp

                if (isMasterUp) {
                    tvMasterTitle.text = "Nova Shields: Active"
                    tvMasterTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald))
                    tvMasterSubtitle.text = "Blocking network trackers, malvertising, and fingerprinting"
                    ivHeaderIcon.setBackgroundResource(R.drawable.bg_badge_emerald)
                    ivHeaderIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_emerald))
                } else {
                    tvMasterTitle.text = "Nova Shields: Paused"
                    tvMasterTitle.setTextColor(ContextCompat.getColor(this, R.color.risk_suspicious))
                    tvMasterSubtitle.text = "All protections disabled for this site to prevent layout issues"
                    ivHeaderIcon.setBackgroundResource(R.drawable.bg_badge_amber)
                    ivHeaderIcon.setColorFilter(ContextCompat.getColor(this, R.color.risk_suspicious))
                }

                val jsActive = s.isJavaScriptAllowed()
                tvJsStatus.text = if (jsActive) "Active" else "Blocked"
                tvJsStatus.setTextColor(ContextCompat.getColor(this, if (jsActive) R.color.accent_emerald else R.color.risk_blocked))

                val cookiesBlocked = s.isThirdPartyCookiesBlocked()
                tvCookiesStatus.text = if (cookiesBlocked) "Blocked" else "Allowed"
                tvCookiesStatus.setTextColor(ContextCompat.getColor(this, if (cookiesBlocked) R.color.accent_emerald else R.color.risk_suspicious))
            }

            updateUIStates(currentSettings)

            fun saveAndApply(newSettings: com.gintama.novabrowser.shields.SiteShieldSettings, reload: Boolean = false) {
                currentSettings = newSettings
                com.gintama.novabrowser.shields.SiteShieldManager.updateSettings(newSettings)
                updateUIStates(newSettings)
                tab?.webView?.applySiteShields(siteHost)
                if (tab?.isPrivate != true) {
                    if (!newSettings.shieldsEnabled) {
                        ivSecurityIndicator.setColorFilter(ContextCompat.getColor(this, R.color.risk_suspicious))
                    } else {
                        val (_, decision) = controller.evaluateNavigation(currentUrl)
                        updateSecurityIndicator(decision.riskState)
                    }
                }
                if (reload) {
                    tab?.webView?.reload()
                }
            }

            switchMaster.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != currentSettings.shieldsEnabled) {
                    saveAndApply(currentSettings.copy(shieldsEnabled = isChecked), reload = true)
                }
            }

            switchAdBlock.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != currentSettings.adBlockEnabled) {
                    saveAndApply(currentSettings.copy(adBlockEnabled = isChecked), reload = true)
                }
            }

            switchCosmetic.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != currentSettings.cosmeticEnabled) {
                    saveAndApply(currentSettings.copy(cosmeticEnabled = isChecked), reload = false)
                }
            }

            switchJs.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != currentSettings.javaScriptEnabled) {
                    saveAndApply(currentSettings.copy(javaScriptEnabled = isChecked), reload = true)
                }
            }

            switchThirdPartyCookies.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != currentSettings.blockThirdPartyCookies) {
                    saveAndApply(currentSettings.copy(blockThirdPartyCookies = isChecked), reload = false)
                }
            }

            // Permissions readout
            val dbHelper = NovaDatabaseHelper.getInstance(this)
            val canonicalOrigin = com.gintama.novabrowser.core.security.UrlCanonicalizer.canonicalOrigin(currentUrl)
            fun refreshPermissions() {
                val perms = dbHelper.getSitePermissions(canonicalOrigin)
                if (perms.isNotEmpty()) {
                    tvPermissions.text = perms.entries.joinToString("\n") { (k, v) ->
                        val permName = when (k) {
                            com.gintama.novabrowser.browser.SitePermissionType.CAMERA -> "Camera"
                            com.gintama.novabrowser.browser.SitePermissionType.MICROPHONE -> "Microphone"
                            com.gintama.novabrowser.browser.SitePermissionType.GEOLOCATION -> "Location"
                            else -> k
                        }
                        "• $permName: ${if (v) "GRANTED" else "BLOCKED"}"
                    }
                    btnResetPerms.visibility = View.VISIBLE
                } else {
                    tvPermissions.text = "• No hardware or device permissions requested"
                    btnResetPerms.visibility = View.GONE
                }
            }
            refreshPermissions()

            btnResetPerms.setOnClickListener {
                dbHelper.clearSitePermissions(canonicalOrigin)
                refreshPermissions()
                Toast.makeText(this, "Permissions reset for $siteHost", Toast.LENGTH_SHORT).show()
            }

            btnClearData.setOnClickListener {
                com.gintama.novabrowser.shields.SiteShieldManager.clearSiteData(this, currentUrl) {
                    Toast.makeText(this, "Cleared cookies and local storage for $siteHost", Toast.LENGTH_SHORT).show()
                    tab?.webView?.reload()
                    dialog.dismiss()
                }
            }

            btnReport.setOnClickListener {
                dbHelper.reportBrokenSite(currentUrl)
                Toast.makeText(this, "Site reported locally for shield rules tuning", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        btnAudit.setOnClickListener {
            dialog.dismiss()
            showSecurityInfoDialog()
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

    private fun getDisplayHost(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
    }

    private fun showPageActionsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_page_actions, null)
        dialog.setContentView(view)

        val activeTab = tabManager.activeTab
        val activeWebView = activeTab?.webView
        val currentUrl = activeTab?.url.orEmpty()
        val isBrowsing = currentUrl.isNotBlank() && currentUrl != "about:blank"

        val tvSheetPageTitle = view.findViewById<TextView>(R.id.tvSheetPageTitle)
        val tvSheetPageDomain = view.findViewById<TextView>(R.id.tvSheetPageDomain)
        val btnSheetClose = view.findViewById<ImageButton>(R.id.btnSheetClose)

        if (isBrowsing) {
            tvSheetPageTitle?.text = activeTab?.title?.takeIf { it.isNotBlank() } ?: getDisplayHost(currentUrl)
            tvSheetPageDomain?.text = getDisplayHost(currentUrl)
        } else {
            tvSheetPageTitle?.text = "NovaBrowser"
            tvSheetPageDomain?.text = "Horizon Start Canvas"
        }

        btnSheetClose?.setOnClickListener {
            dialog.dismiss()
        }

        // 4 Tools Squircle Grid
        val btnToolReader = view.findViewById<View>(R.id.btnToolReader)
        val btnToolShare = view.findViewById<View>(R.id.btnToolShare)
        val btnToolDesktop = view.findViewById<View>(R.id.btnToolDesktop)
        val btnToolForceDark = view.findViewById<View>(R.id.btnToolForceDark)

        btnToolReader?.setOnClickListener {
            dialog.dismiss()
            launchReaderMode()
        }

        btnToolShare?.setOnClickListener {
            dialog.dismiss()
            if (isBrowsing) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Link"))
            } else {
                Toast.makeText(this, "Open a website to share", Toast.LENGTH_SHORT).show()
            }
        }

        btnToolDesktop?.setOnClickListener {
            dialog.dismiss()
            if (activeWebView != null) {
                val newMode = !activeWebView.isDesktopMode
                activeWebView.setDesktopMode(newMode)
                val msg = if (newMode) "Desktop mode enabled" else "Mobile mode restored"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        btnToolForceDark?.setOnClickListener {
            dialog.dismiss()
            if (activeWebView != null) {
                val newMode = !activeWebView.isForceDarkMode
                activeWebView.setForceDarkMode(newMode)
                val msg = if (newMode) "Force dark web enabled" else "Default web styling restored"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Privacy Report Card - Strictly REAL data (User Rule #6)
        val layoutSheetPrivacyReport = view.findViewById<View>(R.id.layoutSheetPrivacyReport)
        val tvSheetTrackerStats = view.findViewById<TextView>(R.id.tvSheetTrackerStats)

        val blockedCount = activeTab?.blockedAdsCount ?: 0
        if (activeTab?.isPrivate == true) {
            tvSheetTrackerStats?.text = "$blockedCount trackers prevented • Strict Private Canvas"
        } else {
            tvSheetTrackerStats?.text = "$blockedCount trackers prevented • Advanced Shield Active"
        }

        layoutSheetPrivacyReport?.setOnClickListener {
            dialog.dismiss()
            showSiteShieldsBottomSheet()
        }

        // Grouped List Actions
        view.findViewById<View>(R.id.rowSheetBookmark)?.let { bmRow ->
            NovaMotion.attachThrottledClick(bmRow) {
                dialog.dismiss()
                if (isBrowsing) {
                    controller.toggleBookmark(currentUrl, activeTab?.title.orEmpty()) { isAdded ->
                        val msg = if (isAdded) getString(R.string.bookmark_added) else getString(R.string.bookmark_removed)
                        com.google.android.material.snackbar.Snackbar.make(
                            mainViewportContainer,
                            msg,
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    com.google.android.material.snackbar.Snackbar.make(
                        mainViewportContainer,
                        "Open a website to bookmark",
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        view.findViewById<View>(R.id.rowSheetAddToHome)?.setOnClickListener {
            dialog.dismiss()
            if (isBrowsing) {
                com.gintama.novabrowser.browser.PwaShortcutManager.showAddToHomeDialog(
                    this,
                    currentUrl,
                    activeTab?.title.takeIf { !it.isNullOrBlank() } ?: "Web App",
                    activeWebView?.favicon
                )
            } else {
                Toast.makeText(this, "Open a website first to add to home screen", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.rowSheetCopyLink)?.setOnClickListener {
            dialog.dismiss()
            if (isBrowsing) {
                copyCleanLink(currentUrl)
            } else {
                Toast.makeText(this, "No URL to copy", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.rowSheetSavePage)?.setOnClickListener {
            dialog.dismiss()
            if (isBrowsing) {
                showSavePageDialog()
            }
        }

        view.findViewById<View>(R.id.rowSheetFindInPage)?.setOnClickListener {
            dialog.dismiss()
            showFindInPage()
        }

        view.findViewById<View>(R.id.rowSheetSettings)?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        dialog.show()
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

        // Set Desktop Site checkmark
        val desktopItem = popup.menu.findItem(R.id.action_desktop_site)
        val currentWebView = tabManager.activeTab?.webView
        desktopItem?.isChecked = currentWebView?.isDesktopMode == true

        // Set Force Dark checkmark
        val forceDarkItem = popup.menu.findItem(R.id.action_force_dark)
        forceDarkItem?.isChecked = currentWebView?.isForceDarkMode == true

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
                R.id.action_reader_mode -> {
                    launchReaderMode()
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
                R.id.action_force_dark -> {
                    val webView = tabManager.activeTab?.webView
                    if (webView != null) {
                        val newMode = !webView.isForceDarkMode
                        webView.setForceDarkMode(newMode)
                        val msg = if (newMode) "Force dark web enabled" else "Default web styling restored"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_add_to_home -> {
                    val tab = tabManager.activeTab
                    val currentUrl = tab?.url.orEmpty()
                    val currentTitle = tab?.title.takeIf { !it.isNullOrBlank() } ?: "Web App"
                    val favicon = tab?.webView?.favicon
                    if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
                        com.gintama.novabrowser.browser.PwaShortcutManager.showAddToHomeDialog(
                            this,
                            currentUrl,
                            currentTitle,
                            favicon
                        )
                    } else {
                        Toast.makeText(this, "Open a website first to add to home screen", Toast.LENGTH_SHORT).show()
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
                            com.google.android.material.snackbar.Snackbar.make(
                                mainViewportContainer,
                                msg,
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                            ).show()
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
                R.id.action_save_page -> {
                    showSavePageDialog()
                    true
                }
                R.id.action_mute_tab -> {
                    toggleActiveTabMute()
                    true
                }
                R.id.action_pip -> {
                    val success = enterPipMode()
                    if (!success) {
                        Toast.makeText(this, "Picture-in-Picture requires Android 8.0+", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_new_tab -> {
                    tabManager.createTab("about:blank")
                    showStartCanvas()
                    true
                }
                R.id.action_new_private_tab -> {
                    checkPrivateAccess {
                        tabManager.createTab("about:blank", isPrivate = true)
                        Toast.makeText(this, R.string.private_mode_notice, Toast.LENGTH_SHORT).show()
                        showStartCanvas()
                    }
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
                    showSiteShieldsBottomSheet()
                    true
                }
                R.id.action_search_engine -> {
                    showSearchEnginePicker()
                    true
                }
                R.id.action_media_sniffer -> {
                    val webView = tabManager.activeTab?.webView
                    val url = tabManager.activeTab?.url.orEmpty()
                    if (webView != null && url.isNotBlank() && url != "about:blank") {
                        MediaSnifferEngine.showMediaSnifferDialog(this, webView)
                    } else {
                        Toast.makeText(this, "Open a web page first to sniff media and downloads", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_customize_wallpaper -> {
                    showWallpaperPickerDialog()
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
        val layoutPrivateTabsBanner = view.findViewById<View>(R.id.layoutPrivateTabsBanner)
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
                if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
                    quadViewManager.exitQuadView()
                }
                tabManager.switchTab(clickedTab.id)
                dialog.dismiss()
            },
            onTabClose = { closedTab ->
                if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
                    quadViewManager.onTabClosed(closedTab.id)
                }
                tabManager.closeTab(closedTab.id)
                if (tabManager.tabCount == 0) {
                    tabManager.createTab("about:blank")
                    showStartCanvas()
                    dialog.dismiss()
                } else {
                    refreshTabList()
                }
            }
        ).apply {
            onTabLongClick = { clickedTab ->
                val popup = PopupMenu(this@MainActivity, rvTabs)
                popup.menu.add(0, 1, 0, "Open in QuadView")
                popup.menu.add(0, 2, 1, "Close Tab")
                popup.menu.add(0, 3, 2, "Copy URL")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            dialog.dismiss()
                            enterQuadView(clickedTab)
                            true
                        }
                        2 -> {
                            if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
                                quadViewManager.onTabClosed(clickedTab.id)
                            }
                            tabManager.closeTab(clickedTab.id)
                            if (tabManager.tabCount == 0) {
                                tabManager.createTab("about:blank")
                                showStartCanvas()
                                dialog.dismiss()
                            } else {
                                refreshTabList()
                            }
                            true
                        }
                        3 -> {
                            val clip = ClipData.newPlainText("URL", clickedTab.url)
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@MainActivity, "URL copied", Toast.LENGTH_SHORT).show()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
        rvTabs.adapter = adapter

        refreshTabList = {
            val standardTabs = tabManager.getStandardTabs()
            val privateTabs = tabManager.getPrivateTabs()
            val currentList = if (showPrivateOnly) privateTabs else standardTabs

            btnTabFilterStandard.text = "Standard (${standardTabs.size})"
            btnTabFilterPrivate.text = "Private (${privateTabs.size})"

            if (showPrivateOnly) {
                layoutPrivateTabsBanner?.visibility = View.VISIBLE
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
                layoutPrivateTabsBanner?.visibility = View.GONE
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
                checkPrivateAccess {
                    showPrivateOnly = true
                    refreshTabList()
                }
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
                checkPrivateAccess {
                    tabManager.createTab("about:blank", isPrivate = true)
                    Toast.makeText(this, R.string.private_mode_notice, Toast.LENGTH_SHORT).show()
                    showStartCanvas()
                    dialog.dismiss()
                }
            } else {
                tabManager.createTab("about:blank", isPrivate = false)
                showStartCanvas()
                dialog.dismiss()
            }
        }

        btnEmptyCreateTab.setOnClickListener {
            btnAddNewTab.performClick()
        }

        val btnOpenQuadView = view.findViewById<Button>(R.id.btnOpenQuadView)
        btnOpenQuadView?.setOnClickListener {
            val tabsList = if (showPrivateOnly) tabManager.getPrivateTabs() else tabManager.getStandardTabs()
            val target = tabManager.activeTab?.takeIf { tabsList.contains(it) } ?: tabsList.firstOrNull()
            if (target != null) {
                dialog.dismiss()
                enterQuadView(target)
            } else {
                Toast.makeText(this, "No tabs available for QuadView", Toast.LENGTH_SHORT).show()
            }
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
        layoutPageErrorRecovery.visibility = View.GONE
        layoutOmniboxSuggestions.visibility = View.GONE
        tab.webView.onScrollDeltaListener = { deltaY, scrollY ->
            handleWebScrollDelta(deltaY, scrollY)
        }

        if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
            val isBlank = tab.url.isBlank() || tab.url == "about:blank"
            if (omniboxState != OmniboxState.EDITING && !etUrlInput.hasFocus()) {
                etUrlInput.setText(if (isBlank) "" else tab.url)
            }
            updateNavigationButtons()
            updateShieldBadgeCount(tab.blockedAdsCount)
            quadViewManager.onTabUpdated(tab)
            return
        }

        val isBlank = tab.url.isBlank() || tab.url == "about:blank"
        if (isBlank) {
            showStartCanvas()
            btnTabMute.visibility = View.GONE
        } else {
            showWebView()
            btnTabMute.visibility = View.VISIBLE
            updateTabMuteIndicator(tab.isMuted)
            if (omniboxState != OmniboxState.EDITING && !etUrlInput.hasFocus()) {
                etUrlInput.setText(tab.url)
            }
        }

        if (layoutFindInPage.visibility == View.VISIBLE) {
            attachFindListenerToActiveWebView()
        }

        if (tab.isPrivate) {
            ivPrivateBadge.visibility = View.VISIBLE
            headerCapsulePill.setBackgroundResource(R.drawable.bg_glass_pill_incognito)
            bottomFloatingIsland.setBackgroundResource(R.drawable.bg_nav_dock_horizon_private)
            viewTabCountSquircle.setBackgroundResource(R.drawable.bg_squircle_tab_count_incognito)
            viewDockTabSquircle.setBackgroundResource(R.drawable.bg_squircle_tab_count_incognito)
            ivSecurityIndicator.setColorFilter(ContextCompat.getColor(this, R.color.horizon_private))
            val currentEngine = SearchEngineManager.getActiveEngine(this)
            etPrivateSearchInput.hint = "${currentEngine.displayName} private search or enter URL..."
        } else {
            ivPrivateBadge.visibility = View.GONE
            headerCapsulePill.setBackgroundResource(R.drawable.bg_glass_pill)
            bottomFloatingIsland.setBackgroundResource(R.drawable.bg_nav_dock_horizon)
            viewTabCountSquircle.setBackgroundResource(R.drawable.bg_squircle_tab_count)
            viewDockTabSquircle.setBackgroundResource(R.drawable.bg_squircle_tab_count)
            val shield = com.gintama.novabrowser.shields.SiteShieldManager.getSettingsForSite(tab.url)
            if (!shield.shieldsEnabled) {
                ivSecurityIndicator.setColorFilter(ContextCompat.getColor(this, R.color.risk_suspicious))
            } else {
                val (_, decision) = controller.evaluateNavigation(tab.url)
                updateSecurityIndicator(decision.riskState)
            }
        }
        updateStartPagePrivateCapsule(tab.isPrivate)

        // Auto-dismiss URL input focus when touching the web page
        tab.webView.setOnTouchListener { _, _ ->
            if (etUrlInput.hasFocus()) {
                etUrlInput.clearFocus()
                hideKeyboard()
            }
            false
        }

        updateShieldBadgeCount(tab.blockedAdsCount)
        updateNavigationButtons()
        checkReaderCandidate(tab.webView)
        if (isStandaloneMode) {
            tvStandaloneTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
        }
    }

    override fun onPageCommitVisible(tab: BrowserTab) {
        if (tab.id == tabManager.activeTab?.id) {
            runOnUiThread {
                layoutPageErrorRecovery.visibility = View.GONE
                NovaMotion.animatePageEntrance(tab.webView)
                if (tab.webView.isForceDarkMode) {
                    com.gintama.novabrowser.browser.WebDarkThemeManager.applyDarkTheme(tab.webView, true)
                }
                if (isStandaloneMode) {
                    tvStandaloneTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
                }
            }
        }
    }

    override fun onTabsUpdated(tabs: List<BrowserTab>) {
        tvTabCount.text = tabs.size.toString()
        tvDockTabCount.text = tabs.size.toString()
        updateNavigationButtons()
    }

    override fun onTabStateUpdated(tab: BrowserTab) {
        if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
            quadViewManager.onTabUpdated(tab)
        }
    }

    override fun onPageProgress(progress: Int) {
        NovaMotion.animateProgressBar(progressBar, progress)
        if (progress < 100) {
            btnReloadPage.setImageResource(R.drawable.ic_close)
            btnReloadPage.contentDescription = "Stop Loading"
        } else {
            setOmniboxState(OmniboxState.LOADED)
            swipeRefreshLayout.isRefreshing = false
            btnReloadPage.setImageResource(R.drawable.ic_refresh)
            btnReloadPage.contentDescription = "Reload"
            checkReaderCandidate(tabManager.activeTab?.webView)
            tabManager.activeTab?.webView?.let { webView ->
                if (webView.isForceDarkMode) {
                    com.gintama.novabrowser.browser.WebDarkThemeManager.applyDarkTheme(webView, true)
                }
            }
            if (isStandaloneMode) {
                tabManager.activeTab?.let { tab ->
                    tvStandaloneTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
                }
            }
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
        val canForward = webView?.canGoForward() == true
        val isBrowsing = layoutNewTabCanvas.visibility != View.VISIBLE

        btnNavBack.visibility = if (isBrowsing || canBack) View.VISIBLE else View.GONE
        btnNavBack.alpha = if (canBack || isBrowsing) 1.0f else 0.4f
        btnReloadPage.visibility = if (isBrowsing) View.VISIBLE else View.GONE

        btnDockBack.isEnabled = canBack || isBrowsing
        btnDockBack.alpha = if (canBack || isBrowsing) 1.0f else 0.35f
        btnDockForward.isEnabled = canForward
        btnDockForward.alpha = if (canForward) 1.0f else 0.35f
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
        if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
            if (quadViewManager.onBackPressed()) {
                return
            }
        }
        if (layoutOmniboxSuggestions.visibility == View.VISIBLE || etUrlInput.hasFocus()) {
            layoutOmniboxSuggestions.visibility = View.GONE
            etUrlInput.clearFocus()
            hideKeyboard()
            val activeTab = tabManager.activeTab
            val isBrowsing = activeTab != null && activeTab.url != "about:blank" && activeTab.url.isNotBlank()
            etUrlInput.setText(if (isBrowsing) activeTab.url else "")
            return
        }
        if (layoutPageErrorRecovery.visibility == View.VISIBLE) {
            layoutPageErrorRecovery.visibility = View.GONE
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                showStartCanvas()
            }
            return
        }
        if (isStandaloneMode) {
            val webView = tabManager.activeTab?.webView
            if (webView?.canGoBack() == true) {
                webView.goBack()
            } else {
                finish()
            }
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

    override fun onPageLoadError(tab: BrowserTab, url: String, errorCode: Int, description: String) {
        if (tab.id == tabManager.activeTab?.id) {
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
                swipeRefreshLayout.visibility = View.GONE
                layoutPageErrorRecovery.visibility = View.VISIBLE
                tvErrorTitle.text = "Can't reach this page"
                tvErrorMessage.text = if (description.isNotBlank()) description else "Connection could not be established."
                btnErrorRetry.setOnClickListener {
                    layoutPageErrorRecovery.visibility = View.GONE
                    swipeRefreshLayout.visibility = View.VISIBLE
                    tab.webView.reload()
                }
                btnErrorBack.setOnClickListener {
                    layoutPageErrorRecovery.visibility = View.GONE
                    if (tab.webView.canGoBack()) {
                        swipeRefreshLayout.visibility = View.VISIBLE
                        tab.webView.goBack()
                    } else {
                        showStartCanvas()
                    }
                }
            }
        }
    }

    override fun onRendererRecovered(tab: BrowserTab) {
        if (tab.id == tabManager.activeTab?.id) {
            runOnUiThread {
                webViewContainer.removeAllViews()
                webViewContainer.addView(tab.webView)
                tab.webView.onScrollDeltaListener = { deltaY, scrollY ->
                    handleWebScrollDelta(deltaY, scrollY)
                }
                tab.webView.loadUrl(tab.url.ifBlank { "about:blank" })
                com.google.android.material.snackbar.Snackbar.make(
                    mainViewportContainer,
                    "Web process recovered",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setOmniboxState(state: OmniboxState) {
        omniboxState = state
    }

    private fun queryOmniboxSuggestions(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            runOnUiThread { layoutOmniboxSuggestions.visibility = View.GONE }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = NovaDatabaseHelper.getInstance(this@MainActivity)
            val historyMatches = db.searchHistory(trimmed, limit = 5)
            val bookmarkMatches = db.searchBookmarks(trimmed, limit = 5)

            val suggestions = mutableListOf<OmniboxSuggestion>()

            // 1. Search Engine suggestion
            val activeEngine = SearchEngineManager.getActiveEngine(this@MainActivity)
            suggestions.add(
                OmniboxSuggestion(
                    type = SuggestionType.SEARCH,
                    title = trimmed,
                    subtitle = "Search with ${activeEngine.displayName}",
                    targetUrl = trimmed,
                    queryToInsert = trimmed
                )
            )

            // 2. Bookmarks matches
            for (bm in bookmarkMatches) {
                suggestions.add(
                    OmniboxSuggestion(
                        type = SuggestionType.BOOKMARK,
                        title = bm.title?.ifBlank { bm.url } ?: bm.url,
                        subtitle = bm.url,
                        targetUrl = bm.url,
                        queryToInsert = bm.url
                    )
                )
            }

            // 3. History matches (deduplicated)
            val existingUrls = suggestions.map { it.targetUrl }.toSet()
            for (h in historyMatches) {
                if (!existingUrls.contains(h.url)) {
                    suggestions.add(
                        OmniboxSuggestion(
                            type = SuggestionType.HISTORY,
                            title = h.title?.takeIf { it.isNotBlank() } ?: h.domain,
                            subtitle = h.url,
                            targetUrl = h.url,
                            queryToInsert = h.url
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (omniboxState == OmniboxState.EDITING && etUrlInput.hasFocus()) {
                    suggestionsAdapter.submitList(suggestions.take(8))
                    layoutOmniboxSuggestions.visibility = if (suggestions.isNotEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun onSuggestionSelected(suggestion: OmniboxSuggestion) {
        setOmniboxState(OmniboxState.SUBMITTING)
        layoutOmniboxSuggestions.visibility = View.GONE
        hideKeyboard()
        etUrlInput.clearFocus()
        loadUrlInActiveTab(suggestion.targetUrl)
    }

    private fun onSuggestionInserted(suggestion: OmniboxSuggestion) {
        etUrlInput.setText(suggestion.queryToInsert)
        etUrlInput.setSelection(suggestion.queryToInsert.length)
    }

    private fun handleWebScrollDelta(deltaY: Int, scrollY: Int) {
        if (layoutNewTabCanvas.visibility == View.VISIBLE ||
            layoutPrivateCanvas.visibility == View.VISIBLE ||
            layoutFindInPage.visibility == View.VISIBLE ||
            isStandaloneMode
        ) {
            if (isDockHidden) showDockIsland()
            return
        }

        if (scrollY <= 15) {
            if (isDockHidden) showDockIsland()
            return
        }

        val density = resources.displayMetrics.density
        val threshold = (12 * density).toInt()

        if (deltaY > threshold && !isDockHidden) {
            hideDockIsland()
        } else if (deltaY < -threshold && isDockHidden) {
            showDockIsland()
        }
    }

    private fun hideDockIsland() {
        if (isDockHidden) return
        isDockHidden = true
        bottomFloatingIsland.animate()
            .translationY(bottomFloatingIsland.height.toFloat() + 40f * resources.displayMetrics.density)
            .alpha(0f)
            .setDuration(220L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()
    }

    private fun showDockIsland() {
        if (!isDockHidden) return
        isDockHidden = false
        bottomFloatingIsland.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(260L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    override fun onDestroy() {
        if (customView != null) {
            onHideCustomView()
        }
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            for (tab in tabManager.getTabsList()) {
                val isQuadTab = ::quadViewManager.isInitialized && quadViewManager.state.getAllAssignedTabIds().contains(tab.id)
                if (tab.id != tabManager.activeTab?.id && !isQuadTab) {
                    tab.thumbnail?.recycle()
                    tab.thumbnail = null
                }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        for (tab in tabManager.getTabsList()) {
            if (tab.id != tabManager.activeTab?.id) {
                tab.thumbnail?.recycle()
                tab.thumbnail = null
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::quadViewManager.isInitialized && quadViewManager.state.isQuadActive) {
            quadViewManager.onConfigurationChanged(newConfig)
        }
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val isStandalone = intent?.getBooleanExtra(com.gintama.novabrowser.browser.PwaShortcutManager.EXTRA_STANDALONE, false) == true ||
                action == com.gintama.novabrowser.browser.PwaShortcutManager.ACTION_OPEN_PWA
        if (isStandalone) {
            enableStandalonePwaMode()
        }

        val data: Uri? = intent?.data
        if ((Intent.ACTION_VIEW == action || isStandalone) && data != null) {
            val url = data.toString()
            loadUrlInActiveTab(url)
        }
    }

    private fun enableStandalonePwaMode() {
        isStandaloneMode = true
        topChromeHeader.visibility = View.GONE
        bottomFloatingIsland.visibility = View.GONE
        layoutStandaloneHeader.visibility = View.VISIBLE
        webViewContainer.setPadding(0, 0, 0, 0)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun launchReaderMode() {
        val webView = tabManager.activeTab?.webView
        val currentUrl = tabManager.activeTab?.url.orEmpty()
        if (webView == null || currentUrl.isBlank() || currentUrl == "about:blank") {
            Toast.makeText(this, "No article to read on this page", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Extracting clean article...", Toast.LENGTH_SHORT).show()
        ReaderExtractor.extractArticle(this, webView) { article ->
            if (article != null) {
                startActivity(ReaderActivity.createIntent(this, article))
            } else {
                Toast.makeText(this, "Could not extract readable article content", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSavePageDialog() {
        val tab = tabManager.activeTab
        val webView = tab?.webView
        val currentUrl = tab?.url.orEmpty()
        if (webView == null || currentUrl.isBlank() || currentUrl == "about:blank") {
            Toast.makeText(this, "No webpage loaded to save", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_save_page, null)
        dialog.setContentView(view)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvSavePageSubtitle)
        val optPdf = view.findViewById<View>(R.id.layoutOptionPdf)
        val optMht = view.findViewById<View>(R.id.layoutOptionMht)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelSavePage)

        val title = tab.title.ifBlank { tab.url }
        tvSubtitle.text = title

        optPdf.setOnClickListener {
            dialog.dismiss()
            val success = com.gintama.novabrowser.offline.OfflinePageManager.printOrSavePdf(this, webView, title)
            if (!success) {
                Toast.makeText(this, "Could not open print / PDF manager", Toast.LENGTH_SHORT).show()
            }
        }

        optMht.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Saving web archive...", Toast.LENGTH_SHORT).show()
            com.gintama.novabrowser.offline.OfflinePageManager.saveWebArchive(this, webView, title, currentUrl) { file ->
                if (file != null) {
                    val snackbar = com.google.android.material.snackbar.Snackbar.make(
                        mainViewportContainer,
                        "Saved: ${file.name}",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )
                    snackbar.setAction("Open") {
                        val fileUri = Uri.fromFile(file)
                        loadUrlInActiveTab(fileUri.toString())
                    }
                    snackbar.show()
                } else {
                    Toast.makeText(this, "Failed to save web archive", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun checkReaderCandidate(webView: WebView?) {
        if (webView == null) {
            btnReaderMode.visibility = View.GONE
            return
        }
        val currentUrl = webView.url.orEmpty()
        if (currentUrl.isBlank() || currentUrl == "about:blank" || layoutNewTabCanvas.visibility == View.VISIBLE) {
            btnReaderMode.visibility = View.GONE
            return
        }
        ReaderExtractor.isArticleCandidate(webView) { isCandidate ->
            runOnUiThread {
                if (layoutNewTabCanvas.visibility != View.VISIBLE) {
                    btnReaderMode.visibility = if (isCandidate) View.VISIBLE else View.GONE
                } else {
                    btnReaderMode.visibility = View.GONE
                }
            }
        }
    }

    private fun showWallpaperPickerDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_wallpaper_picker, null)
        dialog.setContentView(view)

        fun applyAndDismiss(preset: NovaWallpaperManager.WallpaperPreset) {
            NovaWallpaperManager.setActivePreset(this, preset)
            NovaWallpaperManager.applyWallpaper(this, ivStartCanvasWallpaper, viewWallpaperDimmer)
            dialog.dismiss()
            Toast.makeText(this, "Wallpaper set: ${preset.displayName}", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.cardPresetDeepSpace)?.setOnClickListener {
            applyAndDismiss(NovaWallpaperManager.WallpaperPreset.DEEP_SPACE)
        }
        view.findViewById<View>(R.id.cardPresetAurora)?.setOnClickListener {
            applyAndDismiss(NovaWallpaperManager.WallpaperPreset.COSMIC_AURORA)
        }
        view.findViewById<View>(R.id.cardPresetCyberpunk)?.setOnClickListener {
            applyAndDismiss(NovaWallpaperManager.WallpaperPreset.CYBERPUNK_NEON)
        }
        view.findViewById<View>(R.id.cardPresetSapphire)?.setOnClickListener {
            applyAndDismiss(NovaWallpaperManager.WallpaperPreset.MIDNIGHT_SAPPHIRE)
        }

        view.findViewById<View>(R.id.btnPickGalleryPhoto)?.setOnClickListener {
            dialog.dismiss()
            try {
                wallpaperGalleryLauncher.launch("image/*")
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot launch gallery picker", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun checkPrivateAccess(onGranted: () -> Unit) {
        if (NovaBiometricHelper.isPrivateTabLockEnabled(this) && !isPrivateSessionUnlocked) {
            NovaBiometricHelper.authenticate(
                activity = this,
                title = "Unlock Private Browsing",
                subtitle = "Verify fingerprint or PIN to access private session",
                onSuccess = {
                    isPrivateSessionUnlocked = true
                    onGranted()
                },
                onError = { err ->
                    Toast.makeText(this, "Authentication cancelled: $err", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            onGranted()
        }
    }

    private fun toggleActiveTabMute() {
        val tab = tabManager.activeTab ?: return
        val isMuted = TabMuteEngine.toggleTabMute(tab)
        updateTabMuteIndicator(isMuted)
        val msg = if (isMuted) "Tab audio muted" else "Tab audio restored"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun updateTabMuteIndicator(isMuted: Boolean) {
        btnTabMute.setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        btnTabMute.setColorFilter(
            ContextCompat.getColor(this, if (isMuted) R.color.risk_suspicious else R.color.text_secondary)
        )
    }

    private fun enterPipMode(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                return enterPictureInPictureMode(params)
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefs = getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        val autoPip = prefs.getBoolean("pref_auto_pip", true)
        if (autoPip && (customView != null || (tabManager.activeTab != null && layoutNewTabCanvas.visibility != View.VISIBLE))) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            topChromeHeader.visibility = View.GONE
            bottomFloatingIsland.visibility = View.GONE
            layoutFindInPage.visibility = View.GONE
            layoutStandaloneHeader.visibility = View.GONE
        } else {
            if (customView == null) {
                if (isStandaloneMode) {
                    layoutStandaloneHeader.visibility = View.VISIBLE
                } else {
                    topChromeHeader.visibility = View.VISIBLE
                    bottomFloatingIsland.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        isPrivateSessionUnlocked = false
        if (NovaBiometricHelper.isAppLockEnabled(this)) {
            isAppUnlocked = false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etUrlInput.windowToken, 0)
        imm?.hideSoftInputFromWindow(etOmniboxInput.windowToken, 0)
    }
}
