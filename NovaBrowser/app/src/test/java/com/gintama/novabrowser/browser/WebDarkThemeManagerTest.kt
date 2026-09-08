package com.gintama.novabrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDarkThemeManagerTest {

    @Test
    fun testDarkCssContainsInvertAndMediaProtection() {
        val css = WebDarkThemeManager.DARK_CSS

        // Inverts background to AMOLED dark
        assertTrue("CSS must invert root html", css.contains("filter: invert(90%) hue-rotate(180deg)"))
        assertTrue("CSS must set dark AMOLED background", css.contains("background: #0f1117"))

        // Media elements must be counter-inverted to preserve true colors
        assertTrue("CSS must protect images", css.contains("img"))
        assertTrue("CSS must protect videos", css.contains("video"))
        assertTrue("CSS must protect canvases", css.contains("canvas"))
        assertTrue("CSS must protect svgs", css.contains("svg"))
        assertTrue("CSS must counter-invert media", css.contains("filter: invert(100%) hue-rotate(180deg)"))
    }

    @Test
    fun testGetDarkScriptEnabledInjectsStyle() {
        val script = WebDarkThemeManager.getDarkScript(true)

        assertTrue("Script must specify styleId", script.contains("__nova_dark_theme__"))
        assertTrue("Script must append style element when enabled", script.contains("document.createElement('style')"))
        assertTrue("Script must attach to head or documentElement", script.contains("appendChild(style)"))
        assertTrue("Script must inject dark CSS", script.contains("filter: invert(90%) hue-rotate(180deg)"))
    }

    @Test
    fun testGetDarkScriptDisabledRemovesStyle() {
        val script = WebDarkThemeManager.getDarkScript(false)

        assertTrue("Script must locate existing element", script.contains("document.getElementById(styleId)"))
        assertTrue("Script must remove element when disabled", script.contains("existing.remove()"))
    }

    @Test
    fun testPreferenceKeyConstant() {
        assertEquals("pref_force_dark_web", WebDarkThemeManager.PREF_GLOBAL_FORCE_DARK)
    }
}
