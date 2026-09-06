package com.gintama.novabrowser.reader

import android.content.Context

enum class ReaderTheme(
    val id: String,
    val displayName: String,
    val bgColor: String,
    val textColor: String,
    val linkColor: String,
    val cardBg: String
) {
    DARK("dark", "Dark", "#121212", "#E0E0E0", "#34D399", "#1E1E1E"),
    SEPIA("sepia", "Sepia", "#FBF0D9", "#433422", "#B45309", "#F3E5C8"),
    LIGHT("light", "Light", "#FAFAFA", "#1A1A1A", "#2563EB", "#F0F0F0"),
    SLATE("slate", "Slate", "#1E222B", "#F3F4F6", "#818CF8", "#2A2F3D");

    companion object {
        val DEFAULT = DARK
        fun fromId(id: String?): ReaderTheme {
            if (id.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class ReaderFont(
    val id: String,
    val displayName: String,
    val cssFontFamily: String
) {
    SANS_SERIF("sans", "Sans-Serif", "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"),
    SERIF("serif", "Serif", "Georgia, Cambria, 'Times New Roman', serif"),
    MONOSPACE("mono", "Monospace", "'JetBrains Mono', 'Fira Code', 'Courier New', monospace");

    companion object {
        val DEFAULT = SANS_SERIF
        fun fromId(id: String?): ReaderFont {
            if (id.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

object ReaderPreferences {
    private const val PREFS_NAME = "nova_reader_settings"
    private const val KEY_THEME = "pref_reader_theme"
    private const val KEY_FONT = "pref_reader_font"
    private const val KEY_FONT_SCALE = "pref_reader_font_scale"

    fun getTheme(context: Context): ReaderTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReaderTheme.fromId(prefs.getString(KEY_THEME, ReaderTheme.DEFAULT.id))
    }

    fun setTheme(context: Context, theme: ReaderTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply()
    }

    fun getFont(context: Context): ReaderFont {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReaderFont.fromId(prefs.getString(KEY_FONT, ReaderFont.DEFAULT.id))
    }

    fun setFont(context: Context, font: ReaderFont) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FONT, font.id)
            .apply()
    }

    fun getFontScale(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FONT_SCALE, 100).coerceIn(80, 160)
    }

    fun setFontScale(context: Context, scale: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FONT_SCALE, scale.coerceIn(80, 160))
            .apply()
    }

    fun generateHtmlDocument(
        article: ArticleContent,
        theme: ReaderTheme,
        font: ReaderFont,
        fontScalePercent: Int
    ): String {
        val baseFontSize = (18 * (fontScalePercent / 100.0f)).toInt()
        val bylineHtml = if (article.byline.isNotBlank()) {
            "<p class='reader-byline'>By ${escapeHtml(article.byline)}</p>"
        } else ""

        val siteHtml = if (article.siteName.isNotBlank()) {
            "<span class='reader-source'>${escapeHtml(article.siteName)}</span>"
        } else ""

        val readTimeHtml = "<span class='reader-read-time'>${article.readTimeMinutes} min read • ${article.wordCount} words</span>"

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0">
                <title>${escapeHtml(article.title)}</title>
                <style>
                    * {
                        box-sizing: border-box;
                    }
                    body {
                        background-color: ${theme.bgColor};
                        color: ${theme.textColor};
                        font-family: ${font.cssFontFamily};
                        font-size: ${baseFontSize}px;
                        line-height: 1.68;
                        margin: 0;
                        padding: 24px 20px 80px 20px;
                        max-width: 720px;
                        margin-left: auto;
                        margin-right: auto;
                        word-wrap: break-word;
                        text-rendering: optimizeLegibility;
                        -webkit-font-smoothing: antialiased;
                    }
                    h1.reader-title {
                        font-size: 1.75em;
                        line-height: 1.25;
                        margin-top: 0.4em;
                        margin-bottom: 0.4em;
                        font-weight: 800;
                        letter-spacing: -0.02em;
                    }
                    .reader-meta {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                        align-items: center;
                        font-size: 0.78em;
                        opacity: 0.75;
                        margin-bottom: 20px;
                        border-bottom: 1px solid rgba(128, 128, 128, 0.25);
                        padding-bottom: 12px;
                    }
                    .reader-byline {
                        margin: 0;
                        font-style: italic;
                    }
                    .reader-source {
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                    }
                    .reader-read-time {
                        margin-left: auto;
                    }
                    p {
                        margin-top: 0;
                        margin-bottom: 1.25em;
                    }
                    h2, h3, h4 {
                        margin-top: 1.5em;
                        margin-bottom: 0.5em;
                        line-height: 1.3;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                        border-radius: 12px;
                        display: block;
                        margin: 20px auto;
                    }
                    blockquote {
                        border-left: 3px solid ${theme.linkColor};
                        margin: 20px 0;
                        padding: 8px 16px;
                        background: ${theme.cardBg};
                        border-radius: 0 8px 8px 0;
                        font-style: italic;
                    }
                    pre, code {
                        font-family: ${ReaderFont.MONOSPACE.cssFontFamily};
                        background: ${theme.cardBg};
                        border-radius: 6px;
                        font-size: 0.88em;
                    }
                    pre {
                        padding: 14px;
                        overflow-x: auto;
                        line-height: 1.45;
                    }
                    code {
                        padding: 2px 6px;
                    }
                    a {
                        color: ${theme.linkColor};
                        text-decoration: underline;
                        text-underline-offset: 3px;
                    }
                    ul, ol {
                        padding-left: 24px;
                        margin-bottom: 1.25em;
                    }
                    li {
                        margin-bottom: 0.4em;
                    }
                </style>
            </head>
            <body>
                <header>
                    <h1 class="reader-title">${escapeHtml(article.title)}</h1>
                    <div class="reader-meta">
                        $siteHtml
                        $bylineHtml
                        $readTimeHtml
                    </div>
                </header>
                <article class="reader-content">
                    ${article.contentHtml}
                </article>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
