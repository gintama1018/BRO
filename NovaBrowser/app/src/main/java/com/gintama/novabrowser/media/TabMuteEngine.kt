package com.gintama.novabrowser.media

import com.gintama.novabrowser.browser.BrowserTab

/**
 * TabMuteEngine: Injects and manages per-tab media muting via DOM audio/video control
 * and MutationObserver enforcement.
 */
object TabMuteEngine {

    const val MUTE_SCRIPT = """
        (function() {
            window.__novaMuted = true;
            try {
                document.querySelectorAll('video, audio').forEach(function(el) {
                    el.muted = true;
                });
                if (!window.__novaMuteObserver) {
                    window.__novaMuteObserver = new MutationObserver(function(mutations) {
                        if (window.__novaMuted) {
                            document.querySelectorAll('video, audio').forEach(function(el) {
                                el.muted = true;
                            });
                        }
                    });
                    window.__novaMuteObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });
                }
            } catch (e) {}
        })();
    """

    const val UNMUTE_SCRIPT = """
        (function() {
            window.__novaMuted = false;
            try {
                document.querySelectorAll('video, audio').forEach(function(el) {
                    el.muted = false;
                });
            } catch (e) {}
        })();
    """

    fun applyMuteState(tab: BrowserTab, muted: Boolean) {
        tab.isMuted = muted
        val script = if (muted) MUTE_SCRIPT else UNMUTE_SCRIPT
        try {
            tab.webView.evaluateJavascript(script, null)
        } catch (_: Exception) {
        }
    }

    fun toggleTabMute(tab: BrowserTab): Boolean {
        val newState = !tab.isMuted
        applyMuteState(tab, newState)
        return newState
    }
}
