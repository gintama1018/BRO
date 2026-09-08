package com.gintama.novabrowser.ui.quad

/**
 * QuadViewState: Manages presentation mappings from QuadSlot to Tab ID.
 *
 * TabManager remains the sole authority and source of truth for BrowserTab lifecycle.
 * This state stores stable tab IDs only to prevent duplicate tab ownership or divergent state.
 */
class QuadViewState {
    var slotATabId: String? = null
    var slotBTabId: String? = null
    var slotCTabId: String? = null
    var slotDTabId: String? = null

    var activeSlot: QuadSlot = QuadSlot.SLOT_A
    var focusedSlot: QuadSlot? = null // When null: 2x2 grid. When non-null: that slot is in Focus Mode.
    var isQuadActive: Boolean = false

    fun getTabId(slot: QuadSlot): String? = when (slot) {
        QuadSlot.SLOT_A -> slotATabId
        QuadSlot.SLOT_B -> slotBTabId
        QuadSlot.SLOT_C -> slotCTabId
        QuadSlot.SLOT_D -> slotDTabId
    }

    fun setTabId(slot: QuadSlot, tabId: String?) {
        when (slot) {
            QuadSlot.SLOT_A -> slotATabId = tabId
            QuadSlot.SLOT_B -> slotBTabId = tabId
            QuadSlot.SLOT_C -> slotCTabId = tabId
            QuadSlot.SLOT_D -> slotDTabId = tabId
        }
    }

    fun findSlotForTabId(tabId: String): QuadSlot? = when (tabId) {
        slotATabId -> QuadSlot.SLOT_A
        slotBTabId -> QuadSlot.SLOT_B
        slotCTabId -> QuadSlot.SLOT_C
        slotDTabId -> QuadSlot.SLOT_D
        else -> null
    }

    fun getAllAssignedTabIds(): List<String> {
        val list = ArrayList<String>(4)
        slotATabId?.let { list.add(it) }
        slotBTabId?.let { list.add(it) }
        slotCTabId?.let { list.add(it) }
        slotDTabId?.let { list.add(it) }
        return list
    }

    fun swap(slot1: QuadSlot, slot2: QuadSlot) {
        if (slot1 == slot2) return
        val id1 = getTabId(slot1)
        val id2 = getTabId(slot2)
        setTabId(slot1, id2)
        setTabId(slot2, id1)

        // If one was active, keep active pointer with the tab
        if (activeSlot == slot1) {
            activeSlot = slot2
        } else if (activeSlot == slot2) {
            activeSlot = slot1
        }

        // If one was focused, keep focus pointer with the slot or tab
        if (focusedSlot == slot1) {
            focusedSlot = slot2
        } else if (focusedSlot == slot2) {
            focusedSlot = slot1
        }
    }

    fun clear() {
        slotATabId = null
        slotBTabId = null
        slotCTabId = null
        slotDTabId = null
        activeSlot = QuadSlot.SLOT_A
        focusedSlot = null
        isQuadActive = false
    }

    val isFocusMode: Boolean
        get() = focusedSlot != null
}
