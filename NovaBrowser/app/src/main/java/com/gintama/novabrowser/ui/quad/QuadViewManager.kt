package com.gintama.novabrowser.ui.quad

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.gintama.novabrowser.R
import com.gintama.novabrowser.browser.BrowserTab
import com.gintama.novabrowser.browser.NovaWebView
import com.gintama.novabrowser.browser.TabManager

/**
 * QuadViewManager: Orchestrates the Nova QuadView workspace.
 *
 * Implements "Same browser sessions. New spatial arrangement."
 * Uses TabManager as the sole source of truth and manages presentation slots without duplicating tab state.
 */
class QuadViewManager(
    private val context: Context,
    private val tabManager: TabManager,
    private val workspaceRoot: View,
    private val onExitQuadView: (BrowserTab?) -> Unit,
    private val onActiveTabChanged: (BrowserTab) -> Unit
) {
    val state = QuadViewState()

    private val tvQuadWorkspaceActiveLabel: TextView = workspaceRoot.findViewById(R.id.tvQuadWorkspaceActiveLabel)
    private val btnExitQuadView: View = workspaceRoot.findViewById(R.id.btnExitQuadView)

    private val quadGridHost: LinearLayout = workspaceRoot.findViewById(R.id.quadGridHost)
    private val rowTopQuad: LinearLayout = workspaceRoot.findViewById(R.id.rowTopQuad)
    private val rowBottomQuad: LinearLayout = workspaceRoot.findViewById(R.id.rowBottomQuad)

    val paneSlotA: QuadPaneView = workspaceRoot.findViewById(R.id.paneSlotA)
    val paneSlotB: QuadPaneView = workspaceRoot.findViewById(R.id.paneSlotB)
    val paneSlotC: QuadPaneView = workspaceRoot.findViewById(R.id.paneSlotC)
    val paneSlotD: QuadPaneView = workspaceRoot.findViewById(R.id.paneSlotD)

    init {
        paneSlotA.initSlot(QuadSlot.SLOT_A)
        paneSlotB.initSlot(QuadSlot.SLOT_B)
        paneSlotC.initSlot(QuadSlot.SLOT_C)
        paneSlotD.initSlot(QuadSlot.SLOT_D)

        setupPaneListeners(paneSlotA)
        setupPaneListeners(paneSlotB)
        setupPaneListeners(paneSlotC)
        setupPaneListeners(paneSlotD)

        btnExitQuadView.setOnClickListener {
            exitQuadView()
        }
    }

    private fun setupPaneListeners(pane: QuadPaneView) {
        pane.onPaneSelectListener = { slot ->
            selectSlot(slot)
        }

        pane.onPaneDoubleTapListener = { slot ->
            toggleFocusMode(slot)
        }

        pane.onPaneMenuListener = { slot, anchor ->
            showPaneContextMenu(slot, anchor)
        }

        pane.onPaneCloseListener = { slot ->
            closeSlot(slot)
        }

        pane.onPaneEmptySlotListener = { slot ->
            showEmptySlotActionDialog(slot)
        }
    }

    /**
     * Enters QuadView workspace with selected tab as Slot A, filling other slots intelligently.
     */
    fun enterQuadView(selectedTab: BrowserTab) {
        state.isQuadActive = true
        state.focusedSlot = null
        workspaceRoot.visibility = View.VISIBLE

        // Populate Slot A with selected tab
        state.slotATabId = selectedTab.id
        state.activeSlot = QuadSlot.SLOT_A

        // Select candidates from same privacy mode
        val candidates = if (selectedTab.isPrivate) {
            tabManager.getPrivateTabs().filter { it.id != selectedTab.id }
        } else {
            tabManager.getStandardTabs().filter { it.id != selectedTab.id }
        }

        // Fill remaining slots without duplicate sessions
        state.slotBTabId = candidates.getOrNull(0)?.id
        state.slotCTabId = candidates.getOrNull(1)?.id
        state.slotDTabId = candidates.getOrNull(2)?.id

        applyGridLayout(null)
        bindAllPanes()
        selectSlot(QuadSlot.SLOT_A)
    }

    /**
     * Exits QuadView and returns to single-tab viewport with the active tab.
     */
    fun exitQuadView() {
        if (!state.isQuadActive) return

        val activeTab = resolveTab(state.getTabId(state.activeSlot)) ?: tabManager.activeTab

        // Detach all WebViews safely from their pane containers
        paneSlotA.detachWebView()
        paneSlotB.detachWebView()
        paneSlotC.detachWebView()
        paneSlotD.detachWebView()

        state.isQuadActive = false
        state.focusedSlot = null
        workspaceRoot.visibility = View.GONE

        onExitQuadView(activeTab)
    }

    fun selectSlot(slot: QuadSlot) {
        state.activeSlot = slot
        val tab = resolveTab(state.getTabId(slot))
        if (tab != null) {
            tabManager.setActiveTabIdSilently(tab.id)
        }

        // Update active highlight rims
        paneSlotA.setActive(slot == QuadSlot.SLOT_A, tab?.isPrivate == true)
        paneSlotB.setActive(slot == QuadSlot.SLOT_B, tab?.isPrivate == true)
        paneSlotC.setActive(slot == QuadSlot.SLOT_C, tab?.isPrivate == true)
        paneSlotD.setActive(slot == QuadSlot.SLOT_D, tab?.isPrivate == true)

        val title = tab?.title?.ifBlank { tab.url } ?: "Empty Slot"
        tvQuadWorkspaceActiveLabel.text = "Active: Slot ${slot.label} • $title"

        if (tab != null) {
            onActiveTabChanged(tab)
        }
    }

    /**
     * Toggles between 2x2 grid and dominant Focus Mode without recreating sessions.
     */
    fun toggleFocusMode(slot: QuadSlot) {
        if (state.focusedSlot == slot) {
            // Restore normal 2x2 grid
            state.focusedSlot = null
            applyGridLayout(null)
        } else {
            // Focus on this slot
            state.focusedSlot = slot
            selectSlot(slot)
            applyGridLayout(slot)
        }
        updateFocusButtons()
    }

    /**
     * Responsively adjusts layout for 2x2 grid or Focus Mode across portrait and landscape.
     * WebViews remain attached in their QuadPaneViews; only layout weights and row arrangements adjust.
     */
    fun applyGridLayout(focusedSlot: QuadSlot?) {
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val panes = mapOf(
            QuadSlot.SLOT_A to paneSlotA,
            QuadSlot.SLOT_B to paneSlotB,
            QuadSlot.SLOT_C to paneSlotC,
            QuadSlot.SLOT_D to paneSlotD
        )

        rowTopQuad.removeAllViews()
        rowBottomQuad.removeAllViews()

        val density = context.resources.displayMetrics.density
        val gapPx = (3 * density).toInt()

        if (isLandscape) {
            quadGridHost.orientation = LinearLayout.HORIZONTAL
            rowTopQuad.orientation = LinearLayout.VERTICAL
            rowBottomQuad.orientation = LinearLayout.VERTICAL

            if (focusedSlot == null) {
                // Landscape 2x2: Column 1 (Slots A, C) and Column 2 (Slots B, D)
                rowTopQuad.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = gapPx
                }
                rowBottomQuad.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = gapPx
                }
                rowBottomQuad.visibility = View.VISIBLE

                paneSlotA.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    bottomMargin = gapPx
                }
                paneSlotC.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = gapPx
                }
                rowTopQuad.addView(paneSlotA)
                rowTopQuad.addView(paneSlotC)

                paneSlotB.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    bottomMargin = gapPx
                }
                paneSlotD.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = gapPx
                }
                rowBottomQuad.addView(paneSlotB)
                rowBottomQuad.addView(paneSlotD)
            } else {
                // Landscape Focus Mode: Column 1 = dominant focused pane (weight 3.5f), Column 2 = 3 compact panes stacked vertically (weight 1.5f)
                rowTopQuad.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3.5f).apply {
                    marginEnd = gapPx
                }
                rowBottomQuad.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f).apply {
                    marginStart = gapPx
                }
                rowBottomQuad.visibility = View.VISIBLE

                val focusedPane = panes[focusedSlot]!!
                focusedPane.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                rowTopQuad.addView(focusedPane)

                val otherPanes = panes.filter { it.key != focusedSlot }.values
                for (p in otherPanes) {
                    p.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        setMargins(0, (2 * density).toInt(), 0, (2 * density).toInt())
                    }
                    rowBottomQuad.addView(p)
                }
            }
        } else {
            // Portrait
            quadGridHost.orientation = LinearLayout.VERTICAL
            rowTopQuad.orientation = LinearLayout.HORIZONTAL
            rowBottomQuad.orientation = LinearLayout.HORIZONTAL

            if (focusedSlot == null) {
                // Portrait 2x2: Row 1 (Slots A, B) and Row 2 (Slots C, D)
                rowTopQuad.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    bottomMargin = gapPx
                }
                rowBottomQuad.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = gapPx
                }
                rowBottomQuad.visibility = View.VISIBLE

                paneSlotA.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = gapPx
                }
                paneSlotB.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = gapPx
                }
                rowTopQuad.addView(paneSlotA)
                rowTopQuad.addView(paneSlotB)

                paneSlotC.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = gapPx
                }
                paneSlotD.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = gapPx
                }
                rowBottomQuad.addView(paneSlotC)
                rowBottomQuad.addView(paneSlotD)
            } else {
                // Portrait Focus Mode: Row 1 = dominant focused pane (weight 3.8f), Row 2 = 3 compact panes horizontal (weight 1.2f)
                rowTopQuad.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3.8f).apply {
                    bottomMargin = gapPx
                }
                rowBottomQuad.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f).apply {
                    topMargin = gapPx
                }
                rowBottomQuad.visibility = View.VISIBLE

                val focusedPane = panes[focusedSlot]!!
                focusedPane.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                rowTopQuad.addView(focusedPane)

                val otherPanes = panes.filter { it.key != focusedSlot }.values
                for (p in otherPanes) {
                    p.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
                    }
                    rowBottomQuad.addView(p)
                }
            }
        }
    }

    fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        if (state.isQuadActive) {
            applyGridLayout(state.focusedSlot)
        }
    }

    fun swapSlots(slot1: QuadSlot, slot2: QuadSlot) {
        if (slot1 == slot2) return

        state.swap(slot1, slot2)
        bindAllPanes()
        selectSlot(state.activeSlot)

        Toast.makeText(context, "Swapped Slot ${slot1.label} with Slot ${slot2.label}", Toast.LENGTH_SHORT).show()
    }

    fun closeSlot(slot: QuadSlot) {
        val tabId = state.getTabId(slot)
        if (tabId != null) {
            state.setTabId(slot, null)
            val pane = getPaneForSlot(slot)
            pane.bindTab(null)

            tabManager.closeTab(tabId)

            // If active slot was closed, select another available slot
            if (state.activeSlot == slot) {
                val nextSlot = QuadSlot.values().firstOrNull { state.getTabId(it) != null } ?: QuadSlot.SLOT_A
                selectSlot(nextSlot)
            }

            // If all slots empty, exit QuadView
            if (state.getAllAssignedTabIds().isEmpty()) {
                exitQuadView()
            }
        }
    }

    private fun showEmptySlotActionDialog(slot: QuadSlot) {
        val availableTabs = tabManager.getTabsList().filter { tab ->
            !state.getAllAssignedTabIds().contains(tab.id)
        }

        val options = ArrayList<String>()
        options.add("+ Create New Tab in Slot ${slot.label}")
        for (t in availableTabs) {
            val title = t.title.ifBlank { t.url }
            options.add("Open: $title")
        }

        AlertDialog.Builder(context)
            .setTitle("Assign Slot ${slot.label}")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    val newTab = tabManager.createTab("about:blank", isPrivate = tabManager.activeTab?.isPrivate == true)
                    state.setTabId(slot, newTab.id)
                    bindAllPanes()
                    selectSlot(slot)
                } else {
                    val chosen = availableTabs[which - 1]
                    state.setTabId(slot, chosen.id)
                    bindAllPanes()
                    selectSlot(slot)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPaneContextMenu(slot: QuadSlot, anchor: View) {
        val popup = PopupMenu(context, anchor)
        val tab = resolveTab(state.getTabId(slot))

        val focusTitle = if (state.focusedSlot == slot) "Restore 2x2 Grid" else "Focus Mode (Expand)"
        popup.menu.add(0, 1, 0, focusTitle)

        if (tab != null) {
            val otherSlots = QuadSlot.values().filter { it != slot }
            val subMenu = popup.menu.addSubMenu(0, 2, 1, "Swap with Slot...")
            for (other in otherSlots) {
                val otherTab = resolveTab(state.getTabId(other))
                val otherDesc = if (otherTab != null) "Slot ${other.label} (${otherTab.title.take(12)})" else "Slot ${other.label} (Empty)"
                subMenu.add(0, 10 + other.index, 0, otherDesc)
            }

            popup.menu.add(0, 3, 2, "Reload Tab")
            popup.menu.add(0, 4, 3, "Open as Single Tab")
            popup.menu.add(0, 5, 4, "Close Tab in Slot")
        } else {
            popup.menu.add(0, 6, 1, "Open Tab in this Slot")
        }

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                1 -> {
                    toggleFocusMode(slot)
                    true
                }
                3 -> {
                    tab?.webView?.reload()
                    true
                }
                4 -> {
                    state.activeSlot = slot
                    exitQuadView()
                    true
                }
                5 -> {
                    closeSlot(slot)
                    true
                }
                6 -> {
                    showEmptySlotActionDialog(slot)
                    true
                }
                in 10..13 -> {
                    val targetIndex = item.itemId - 10
                    val targetSlot = QuadSlot.fromIndex(targetIndex)
                    swapSlots(slot, targetSlot)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun bindAllPanes() {
        paneSlotA.bindTab(resolveTab(state.slotATabId))
        paneSlotB.bindTab(resolveTab(state.slotBTabId))
        paneSlotC.bindTab(resolveTab(state.slotCTabId))
        paneSlotD.bindTab(resolveTab(state.slotDTabId))
        updateFocusButtons()
    }

    private fun updateFocusButtons() {
        paneSlotA.setFocusModeState(state.focusedSlot == QuadSlot.SLOT_A)
        paneSlotB.setFocusModeState(state.focusedSlot == QuadSlot.SLOT_B)
        paneSlotC.setFocusModeState(state.focusedSlot == QuadSlot.SLOT_C)
        paneSlotD.setFocusModeState(state.focusedSlot == QuadSlot.SLOT_D)
    }

    fun getActiveTab(): BrowserTab? {
        return resolveTab(state.getTabId(state.activeSlot))
    }

    fun getPaneForSlot(slot: QuadSlot): QuadPaneView = when (slot) {
        QuadSlot.SLOT_A -> paneSlotA
        QuadSlot.SLOT_B -> paneSlotB
        QuadSlot.SLOT_C -> paneSlotC
        QuadSlot.SLOT_D -> paneSlotD
    }

    fun resolveTab(tabId: String?): BrowserTab? {
        if (tabId == null) return null
        return tabManager.getTabsList().firstOrNull { it.id == tabId }
    }

    fun onTabUpdated(tab: BrowserTab) {
        if (!state.isQuadActive) return
        val slot = state.findSlotForTabId(tab.id) ?: return
        val pane = getPaneForSlot(slot)
        val title = tab.title.ifBlank { tab.url }
        pane.findViewById<TextView>(R.id.tvPaneTitle)?.text = title

        if (slot == state.activeSlot) {
            tvQuadWorkspaceActiveLabel.text = "Active: Slot ${slot.label} • $title"
        }
    }

    fun onTabClosed(tabId: String) {
        if (!state.isQuadActive) return
        val slot = state.findSlotForTabId(tabId) ?: return
        state.setTabId(slot, null)
        getPaneForSlot(slot).bindTab(null)
        if (state.activeSlot == slot) {
            val nextSlot = QuadSlot.values().firstOrNull { state.getTabId(it) != null } ?: QuadSlot.SLOT_A
            selectSlot(nextSlot)
        }
        if (state.getAllAssignedTabIds().isEmpty()) {
            exitQuadView()
        }
    }

    fun onBackPressed(): Boolean {
        if (!state.isQuadActive) return false

        // If in Focus Mode, back press restores 2x2 grid first
        if (state.isFocusMode) {
            toggleFocusMode(state.focusedSlot!!)
            return true
        }

        // In 2x2 grid, if active tab's webView can go back, go back in that pane
        val activeTab = getActiveTab()
        if (activeTab?.webView?.canGoBack() == true) {
            activeTab.webView.goBack()
            return true
        }

        // If active tab cannot go back, ask or exit QuadView
        exitQuadView()
        return true
    }
}
