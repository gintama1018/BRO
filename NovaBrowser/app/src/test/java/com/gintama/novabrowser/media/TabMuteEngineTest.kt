package com.gintama.novabrowser.media

import org.junit.Assert.assertTrue
import org.junit.Test

class TabMuteEngineTest {

    @Test
    fun `mute script targets both video and audio elements`() {
        val script = TabMuteEngine.MUTE_SCRIPT
        assertTrue(script.contains("querySelectorAll('video, audio')"))
        assertTrue(script.contains("el.muted = true"))
        assertTrue(script.contains("window.__novaMuted = true"))
        assertTrue(script.contains("MutationObserver"))
    }

    @Test
    fun `unmute script clears mute flag and sets audio to active`() {
        val script = TabMuteEngine.UNMUTE_SCRIPT
        assertTrue(script.contains("querySelectorAll('video, audio')"))
        assertTrue(script.contains("el.muted = false"))
        assertTrue(script.contains("window.__novaMuted = false"))
    }
}
