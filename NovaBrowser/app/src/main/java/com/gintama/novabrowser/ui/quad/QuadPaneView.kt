package com.gintama.novabrowser.ui.quad

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gintama.novabrowser.R
import com.gintama.novabrowser.browser.BrowserTab
import com.gintama.novabrowser.browser.NovaWebView
import com.gintama.novabrowser.ui.motion.NovaMotion

/**
 * QuadPaneView: Manages presentation, header controls, and safe WebView attachment for one QuadSlot.
 */
class QuadPaneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    lateinit var slot: QuadSlot
        private set

    private val layoutPaneRoot: LinearLayout
    private val layoutPaneHeader: LinearLayout
    private val tvPaneSlotBadge: TextView
    private val tvPaneTitle: TextView
    private val btnPaneFocus: ImageButton
    private val btnPaneMenu: ImageButton
    private val btnPaneClose: ImageButton
    val paneWebContainer: FrameLayout
    private val paneEmptyContainer: LinearLayout

    var onPaneSelectListener: ((QuadSlot) -> Unit)? = null
    var onPaneDoubleTapListener: ((QuadSlot) -> Unit)? = null
    var onPaneMenuListener: ((QuadSlot, View) -> Unit)? = null
    var onPaneCloseListener: ((QuadSlot) -> Unit)? = null
    var onPaneEmptySlotListener: ((QuadSlot) -> Unit)? = null

    private val gestureDetector: GestureDetector

    init {
        LayoutInflater.from(context).inflate(R.layout.view_quad_pane, this, true)

        layoutPaneRoot = findViewById(R.id.layoutPaneRoot)
        layoutPaneHeader = findViewById(R.id.layoutPaneHeader)
        tvPaneSlotBadge = findViewById(R.id.tvPaneSlotBadge)
        tvPaneTitle = findViewById(R.id.tvPaneTitle)
        btnPaneFocus = findViewById(R.id.btnPaneFocus)
        btnPaneMenu = findViewById(R.id.btnPaneMenu)
        btnPaneClose = findViewById(R.id.btnPaneClose)
        paneWebContainer = findViewById(R.id.paneWebContainer)
        paneEmptyContainer = findViewById(R.id.paneEmptyContainer)

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onPaneSelectListener?.invoke(slot)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                onPaneDoubleTapListener?.invoke(slot)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onPaneMenuListener?.invoke(slot, layoutPaneHeader)
            }
        })

        layoutPaneHeader.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        btnPaneFocus.setOnClickListener {
            onPaneDoubleTapListener?.invoke(slot)
        }

        btnPaneMenu.setOnClickListener { v ->
            onPaneMenuListener?.invoke(slot, v)
        }

        btnPaneClose.setOnClickListener {
            onPaneCloseListener?.invoke(slot)
        }

        paneEmptyContainer.setOnClickListener {
            onPaneEmptySlotListener?.invoke(slot)
        }

        NovaMotion.attachSpringTouchFeedback(btnPaneFocus, btnPaneMenu, btnPaneClose)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            onPaneSelectListener?.invoke(slot)
        }
        return false
    }

    fun initSlot(quadSlot: QuadSlot) {
        this.slot = quadSlot
        tvPaneSlotBadge.text = quadSlot.label
    }

    fun bindTab(tab: BrowserTab?) {
        if (tab == null) {
            paneEmptyContainer.visibility = View.VISIBLE
            paneWebContainer.visibility = View.GONE
            tvPaneTitle.text = "Empty Slot ${slot.label}"
            btnPaneFocus.visibility = View.GONE
            btnPaneMenu.visibility = View.GONE
            btnPaneClose.visibility = View.GONE
            paneWebContainer.removeAllViews()
        } else {
            paneEmptyContainer.visibility = View.GONE
            paneWebContainer.visibility = View.VISIBLE
            val displayTitle = when {
                tab.title.isNotBlank() && tab.title != "about:blank" -> tab.title
                tab.url.isNotBlank() && tab.url != "about:blank" -> tab.url
                else -> "New Tab"
            }
            tvPaneTitle.text = displayTitle
            btnPaneFocus.visibility = View.VISIBLE
            btnPaneMenu.visibility = View.VISIBLE
            btnPaneClose.visibility = View.VISIBLE

            // Safe reparenting without reloading
            attachWebView(tab.webView)
        }
    }

    fun attachWebView(webView: NovaWebView) {
        if (webView.parent == paneWebContainer) return

        // Detach from previous parent if any
        (webView.parent as? ViewGroup)?.removeView(webView)

        paneWebContainer.removeAllViews()
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        paneWebContainer.addView(webView)
    }

    fun detachWebView(): NovaWebView? {
        val child = paneWebContainer.getChildAt(0) as? NovaWebView
        if (child != null) {
            paneWebContainer.removeView(child)
        }
        return child
    }

    fun setActive(isActive: Boolean, isPrivate: Boolean = false) {
        if (isActive) {
            layoutPaneRoot.setBackgroundResource(R.drawable.bg_quad_pane_active)
            if (isPrivate) {
                layoutPaneRoot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.incognito_accent)
            } else {
                layoutPaneRoot.backgroundTintList = null
            }
            layoutPaneRoot.elevation = 6f
        } else {
            layoutPaneRoot.setBackgroundResource(R.drawable.bg_quad_pane_inactive)
            layoutPaneRoot.backgroundTintList = null
            layoutPaneRoot.elevation = 2f
        }
    }

    fun setFocusModeState(isFocused: Boolean) {
        btnPaneFocus.setImageResource(
            if (isFocused) R.drawable.ic_close else R.drawable.ic_laptop
        )
        btnPaneFocus.contentDescription = if (isFocused) "Restore Grid" else "Focus Pane"
    }
}
