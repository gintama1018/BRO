package com.gintama.novabrowser.ui.quad

/**
 * QuadSlot: Identifier for the 4 presentation panes in QuadView workspace.
 */
enum class QuadSlot(val label: String, val index: Int) {
    SLOT_A("A", 0),
    SLOT_B("B", 1),
    SLOT_C("C", 2),
    SLOT_D("D", 3);

    companion object {
        fun fromIndex(index: Int): QuadSlot = when (index) {
            0 -> SLOT_A
            1 -> SLOT_B
            2 -> SLOT_C
            3 -> SLOT_D
            else -> SLOT_A
        }
    }
}
