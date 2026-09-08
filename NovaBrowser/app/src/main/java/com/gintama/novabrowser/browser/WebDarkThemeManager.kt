package com.gintama.novabrowser.browser

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * WebDarkThemeManager: Night Owl Dark Theme Engine.
 * Combines Chromium's algorithmic WebSettingsCompat force dark with a non-destructive
 * AMOLED CSS filter injector to guarantee eye-friendly dark mode across 100% of websites.
 */
object WebDarkThemeManager {

    const val PREF_GLOBAL_FORCE_DARK = "pref_force_dark_web"

    const val DARK_CSS = """
        html {
            filter: invert(90%) hue-rotate(180deg) !important;
            background: #0f1117 !important;
        }
        img, video, iframe, canvas, svg, [style*="background-image"] {
            filter: invert(100%) hue-rotate(180deg) !important;
        }
    """

    fun getDarkScript(enabled: Boolean): String {
        return """
            (function() {
                var styleId = '__nova_dark_theme__';
                var existing = document.getElementById(styleId);
                if ($enabled) {
                    if (!existing) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = `
                            html {
                                filter: invert(90%) hue-rotate(180deg) !important;
                                background: #0f1117 !important;
                            }
                            img, video, iframe, canvas, svg, [style*="background-image"] {
                                filter: invert(100%) hue-rotate(180deg) !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(style);
                    }
                } else {
                    if (existing) {
                        existing.remove();
                    }
                }
            })();
        """.trimIndent()
    }

    fun isGlobalForceDarkEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_GLOBAL_FORCE_DARK, false)
    }

    fun setGlobalForceDarkEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_GLOBAL_FORCE_DARK, enabled).apply()
    }

    fun applyDarkTheme(webView: WebView, enabled: Boolean) {
        // 1. AndroidX Webkit Algorithmic Dark
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            try {
                val mode = if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                WebSettingsCompat.setForceDark(webView.settings, mode)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                    WebSettingsCompat.setForceDarkStrategy(
                        webView.settings,
                        WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                    )
                }
            } catch (_: Exception) {
            }
        }

        // 2. High-Contrast AMOLED CSS Fallback/Enhancement
        try {
            webView.evaluateJavascript(getDarkScript(enabled), null)
        } catch (_: Exception) {
        }
    }
}
