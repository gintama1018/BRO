package com.gintama.novabrowser.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gintama.novabrowser.R
import com.google.android.material.bottomsheet.BottomSheetDialog

class ReaderActivity : AppCompatActivity() {

    private lateinit var rootLayout: View
    private lateinit var tvDomain: TextView
    private lateinit var tvMeta: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnAppearance: ImageButton
    private lateinit var btnCopy: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var wvContent: WebView

    private var article: ArticleContent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        article = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_ARTICLE, ArticleContent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_ARTICLE) as? ArticleContent
        }

        if (article == null) {
            Toast.makeText(this, "Could not load reader view", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        renderArticle()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.readerRootLayout)
        tvDomain = findViewById(R.id.tvReaderDomain)
        tvMeta = findViewById(R.id.tvReaderMeta)
        btnBack = findViewById(R.id.btnReaderBack)
        btnAppearance = findViewById(R.id.btnReaderAppearance)
        btnCopy = findViewById(R.id.btnReaderCopy)
        btnShare = findViewById(R.id.btnReaderShare)
        wvContent = findViewById(R.id.wvReaderContent)

        val art = article!!
        tvDomain.text = art.siteName.ifBlank { "Article" }
        tvMeta.text = "${art.readTimeMinutes} min read • Distraction-Free"

        // Sandboxed WebView with strictly NO JavaScript
        wvContent.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnAppearance.setOnClickListener { showAppearanceDialog() }

        btnCopy.setOnClickListener {
            val art = article ?: return@setOnClickListener
            val plainText = art.contentHtml.replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("article_text", "${art.title}\n\n$plainText"))
            Toast.makeText(this, "Clean article text copied", Toast.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener {
            val art = article ?: return@setOnClickListener
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, art.title)
                putExtra(Intent.EXTRA_TEXT, "${art.title}\n${art.originalUrl}")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Article"))
        }
    }

    private fun renderArticle() {
        val art = article ?: return
        val theme = ReaderPreferences.getTheme(this)
        val font = ReaderPreferences.getFont(this)
        val fontScale = ReaderPreferences.getFontScale(this)

        try {
            rootLayout.setBackgroundColor(Color.parseColor(theme.bgColor))
            wvContent.setBackgroundColor(Color.parseColor(theme.bgColor))
        } catch (e: Exception) {
            // Ignore color parsing fallback
        }

        val html = ReaderPreferences.generateHtmlDocument(
            article = art,
            theme = theme,
            font = font,
            fontScalePercent = fontScale
        )

        wvContent.loadDataWithBaseURL(art.originalUrl, html, "text/html", "UTF-8", null)
    }

    private fun showAppearanceDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_reader_appearance, null)
        dialog.setContentView(view)

        val btnDark = view.findViewById<View>(R.id.btnThemeDark)
        val btnSepia = view.findViewById<View>(R.id.btnThemeSepia)
        val btnLight = view.findViewById<View>(R.id.btnThemeLight)
        val btnSlate = view.findViewById<View>(R.id.btnThemeSlate)

        val btnFontSmaller = view.findViewById<Button>(R.id.btnFontSmaller)
        val btnFontLarger = view.findViewById<Button>(R.id.btnFontLarger)
        val tvFontScale = view.findViewById<TextView>(R.id.tvFontScaleLabel)

        val btnSans = view.findViewById<TextView>(R.id.btnFontSans)
        val btnSerif = view.findViewById<TextView>(R.id.btnFontSerif)
        val btnMono = view.findViewById<TextView>(R.id.btnFontMono)

        val btnDone = view.findViewById<Button>(R.id.btnDoneAppearance)

        fun updateFontControls() {
            val scale = ReaderPreferences.getFontScale(this)
            tvFontScale.text = "$scale%"

            val activeFont = ReaderPreferences.getFont(this)
            val activeColor = ContextCompat.getColor(this, R.color.text_primary)
            val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

            btnSans.setTextColor(if (activeFont == ReaderFont.SANS_SERIF) activeColor else inactiveColor)
            btnSans.setBackgroundResource(if (activeFont == ReaderFont.SANS_SERIF) R.drawable.bg_glass_pill_dark else android.R.color.transparent)

            btnSerif.setTextColor(if (activeFont == ReaderFont.SERIF) activeColor else inactiveColor)
            btnSerif.setBackgroundResource(if (activeFont == ReaderFont.SERIF) R.drawable.bg_glass_pill_dark else android.R.color.transparent)

            btnMono.setTextColor(if (activeFont == ReaderFont.MONOSPACE) activeColor else inactiveColor)
            btnMono.setBackgroundResource(if (activeFont == ReaderFont.MONOSPACE) R.drawable.bg_glass_pill_dark else android.R.color.transparent)
        }

        updateFontControls()

        btnDark.setOnClickListener {
            ReaderPreferences.setTheme(this, ReaderTheme.DARK)
            renderArticle()
        }
        btnSepia.setOnClickListener {
            ReaderPreferences.setTheme(this, ReaderTheme.SEPIA)
            renderArticle()
        }
        btnLight.setOnClickListener {
            ReaderPreferences.setTheme(this, ReaderTheme.LIGHT)
            renderArticle()
        }
        btnSlate.setOnClickListener {
            ReaderPreferences.setTheme(this, ReaderTheme.SLATE)
            renderArticle()
        }

        btnFontSmaller.setOnClickListener {
            val current = ReaderPreferences.getFontScale(this)
            ReaderPreferences.setFontScale(this, current - 10)
            updateFontControls()
            renderArticle()
        }

        btnFontLarger.setOnClickListener {
            val current = ReaderPreferences.getFontScale(this)
            ReaderPreferences.setFontScale(this, current + 10)
            updateFontControls()
            renderArticle()
        }

        btnSans.setOnClickListener {
            ReaderPreferences.setFont(this, ReaderFont.SANS_SERIF)
            updateFontControls()
            renderArticle()
        }

        btnSerif.setOnClickListener {
            ReaderPreferences.setFont(this, ReaderFont.SERIF)
            updateFontControls()
            renderArticle()
        }

        btnMono.setOnClickListener {
            ReaderPreferences.setFont(this, ReaderFont.MONOSPACE)
            updateFontControls()
            renderArticle()
        }

        btnDone.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    companion object {
        const val EXTRA_ARTICLE = "extra_article_content"

        fun createIntent(context: Context, article: ArticleContent): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra(EXTRA_ARTICLE, article)
            }
        }
    }
}
