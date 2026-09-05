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
import android.speech.RecognizerIntent
import android.content.ActivityNotFoundException
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import com.gintama.novabrowser.adblock.AdBlockEngine
import com.google.android.material.switchmaterial.SwitchMaterial
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.ui.motion.NovaMotion
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
    private lateinit var layoutShieldBadge: LinearLayout
    private lateinit var tvShieldBadgeCount: TextView
    private lateinit var btnNavBack: ImageButton
    private lateinit var btnNavForward: ImageButton

    // Viewport Containers
    private lateinit var webViewContainer: FrameLayout
    private lateinit var layoutNewTabCanvas: ScrollView

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

    private fun initViews() {
        etUrlInput = findViewById(R.id.etUrlInput)
        btnTabs = findViewById(R.id.btnTabs)
        tvTabCount = findViewById(R.id.tvTabCount)
        btnMenu = findViewById(R.id.btnMenu)
        progressBar = findViewById(R.id.progressBar)
        ivSecurityIndicator = findViewById(R.id.ivSecurityIndicator)
        ivPrivateBadge = findViewById(R.id.ivPrivateBadge)
        tvLocalBadge = findViewById(R.id.tvLocalBadge)
        layoutShieldBadge = findViewById(R.id.layoutShieldBadge)
        tvShieldBadgeCount = findViewById(R.id.tvShieldBadgeCount)
        btnNavBack = findViewById(R.id.btnNavBack)
        btnNavForward = findViewById(R.id.btnNavForward)

        webViewContainer = findViewById(R.id.webViewContainer)
        layoutNewTabCanvas = findViewById(R.id.layoutNewTabCanvas)

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
            btnTabs, btnMenu, btnNavBack, btnNavForward, layoutShieldBadge,
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
        val prefs = getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        val engine = prefs.getString("pref_search_engine", "DuckDuckGo") ?: "DuckDuckGo"
        val encoded = Uri.encode(query)
        return when (engine) {
            "Brave" -> "https://search.brave.com/search?q=$encoded"
            "Startpage" -> "https://www.startpage.com/sp/search?query=$encoded"
            "Google" -> "https://www.google.com/search?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }

    private fun updateSearchEnginePickerLabel() {
        val prefs = getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        val engine = prefs.getString("pref_search_engine", "DuckDuckGo") ?: "DuckDuckGo"
        val code = when (engine) {
            "Brave" -> "Brave ▾"
            "Startpage" -> "SP ▾"
            "Google" -> "Google ▾"
            else -> "DDG ▾"
        }
        btnSearchEnginePicker.text = code
    }

    private fun showSearchEnginePicker() {
        val engines = arrayOf("DuckDuckGo (Privacy)", "Brave Search (Private Index)", "Startpage (Anonymous)", "Google")
        AlertDialog.Builder(this)
            .setTitle("Default Search Engine")
            .setItems(engines) { _, which ->
                val selected = when (which) {
                    1 -> "Brave"
                    2 -> "Startpage"
                    3 -> "Google"
                    else -> "DuckDuckGo"
                }
                getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putString("pref_search_engine", selected)
                    .apply()
                updateSearchEnginePickerLabel()
                Toast.makeText(this, "Search engine set to $selected", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun loadUrlInActiveTab(rawInput: String) {
        val trimmed = rawInput.trim()
        val targetInput = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            (trimmed.contains(".") && !trimmed.contains(" "))
        ) {
            trimmed
        } else {
            getSearchEngineUrl(trimmed)
        }

        val (sanitizedUrl, decision) = controller.evaluateNavigation(targetInput)
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
        NovaMotion.crossFade(webViewContainer, layoutNewTabCanvas)
        etUrlInput.setText("")
        tvLocalBadge.text = "local"
        NovaMotion.animateCountUp(
            tvCanvasBlockedCount,
            AdBlockEngine.getLifetimeBlockedCount(),
            "Ads & Trackers Neutralized"
        )
    }

    private fun showWebView() {
        NovaMotion.crossFade(layoutNewTabCanvas, webViewContainer)
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
            .show()
    }

    private fun showOptionsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

        // Set Desktop Site checkmark
        val desktopItem = popup.menu.findItem(R.id.action_desktop_site)
        val currentWebView = tabManager.activeTab?.webView
        desktopItem?.isChecked = currentWebView?.isDesktopMode == true

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
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
        val btnCloseAll = view.findViewById<Button>(R.id.btnCloseAllTabs)
        val btnDone = view.findViewById<Button>(R.id.btnCloseDialog)

        rvTabs.layoutManager = LinearLayoutManager(this)
        lateinit var adapter: TabsAdapter
        adapter = TabsAdapter(
            tabs = tabManager.getTabsList(),
            activeTabId = tabManager.activeTab?.id,
            onTabClick = { clickedTab ->
                tabManager.switchTab(clickedTab.id)
                dialog.dismiss()
            },
            onTabClose = { closedTab ->
                tabManager.closeTab(closedTab.id)
                adapter.updateTabs(tabManager.getTabsList(), tabManager.activeTab?.id)
                if (tabManager.tabCount == 0) {
                    tabManager.createTab("about:blank")
                    showStartCanvas()
                    dialog.dismiss()
                }
            }
        )
        rvTabs.adapter = adapter

        // Swipe to close tabs
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos in 0 until adapter.itemCount) {
                    val tab = adapter.getTabAt(pos)
                    tabManager.closeTab(tab.id)
                    adapter.updateTabs(tabManager.getTabsList(), tabManager.activeTab?.id)
                    if (tabManager.tabCount == 0) {
                        tabManager.createTab("about:blank")
                        showStartCanvas()
                        dialog.dismiss()
                    }
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(rvTabs)

        btnCloseAll.setOnClickListener {
            tabManager.closeAllTabs()
            showStartCanvas()
            dialog.dismiss()
            Toast.makeText(this, "All tabs closed", Toast.LENGTH_SHORT).show()
        }

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

    private fun showFindInPage() {
        val webView = tabManager.activeTab?.webView ?: return
        NovaMotion.slideDown(layoutFindInPage)
        etFindQuery.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT)

        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            val active = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
            tvFindMatches.text = "$active/$numberOfMatches"
        }

        etFindQuery.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            if (query.isNotBlank()) {
                webView.findAllAsync(query)
            } else {
                webView.clearMatches()
                tvFindMatches.text = "0/0"
            }
        }

        btnFindPrev.setOnClickListener { webView.findNext(false) }
        btnFindNext.setOnClickListener { webView.findNext(true) }
        btnFindClose.setOnClickListener {
            webView.clearMatches()
            NovaMotion.slideUp(layoutFindInPage) {
                hideKeyboard()
            }
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

        tvShieldBadgeCount.text = tab.blockedAdsCount.toString()
        updateNavigationButtons()
    }

    override fun onTabsUpdated(tabs: List<BrowserTab>) {
        tvTabCount.text = tabs.size.toString()
        updateNavigationButtons()
    }

    override fun onPageProgress(progress: Int) {
        NovaMotion.animateProgressBar(progressBar, progress)
    }

    override fun onBlockedAdsUpdated(tab: BrowserTab, blockedCount: Int) {
        if (tab.id == tabManager.activeTab?.id) {
            runOnUiThread {
                tvShieldBadgeCount.text = blockedCount.toString()
                NovaMotion.pulseBadge(layoutShieldBadge)
            }
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
