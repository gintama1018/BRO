package com.gintama.novabrowser.reader

import android.content.Context
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

object ReaderExtractor {

    private const val CANDIDATE_PROBE_SCRIPT = """
        (function() {
            try {
                var ps = document.querySelectorAll('p');
                var textLen = 0;
                for (var i = 0; i < ps.length; i++) {
                    textLen += ps[i].innerText ? ps[i].innerText.length : 0;
                }
                return (ps.length >= 3 && textLen > 250);
            } catch (e) {
                return false;
            }
        })()
    """

    fun isArticleCandidate(webView: WebView, callback: (Boolean) -> Unit) {
        val currentUrl = webView.url.orEmpty()
        if (currentUrl.isBlank() || currentUrl == "about:blank" || currentUrl.startsWith("file://")) {
            callback(false)
            return
        }

        webView.evaluateJavascript(CANDIDATE_PROBE_SCRIPT) { result ->
            val isCandidate = result != null && result.equals("true", ignoreCase = true)
            callback(isCandidate)
        }
    }

    fun extractArticle(context: Context, webView: WebView, callback: (ArticleContent?) -> Unit) {
        val script = try {
            context.assets.open("reader_extract.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            callback(null)
            return
        }

        val originalUrl = webView.url.orEmpty()

        webView.evaluateJavascript(script) { rawResult ->
            if (rawResult.isNullOrBlank() || rawResult == "null") {
                callback(null)
                return@evaluateJavascript
            }

            try {
                // evaluateJavascript returns a JSON string, which may be escaped as a quoted JSON string
                val tokener = JSONTokener(rawResult)
                val firstValue = tokener.nextValue()

                val jsonObject = if (firstValue is String) {
                    JSONObject(firstValue)
                } else if (firstValue is JSONObject) {
                    firstValue
                } else {
                    JSONObject(rawResult)
                }

                val title = jsonObject.optString("title").ifBlank { webView.title.orEmpty().ifBlank { "Article" } }
                val byline = jsonObject.optString("byline")
                val siteName = jsonObject.optString("siteName")
                val contentHtml = jsonObject.optString("contentHtml")
                val wordCount = jsonObject.optInt("wordCount", 0)
                val readTimeMinutes = jsonObject.optInt("readTimeMinutes", 1)

                if (contentHtml.isBlank()) {
                    callback(null)
                } else {
                    callback(
                        ArticleContent(
                            title = title,
                            byline = byline,
                            siteName = siteName,
                            originalUrl = originalUrl,
                            contentHtml = contentHtml,
                            wordCount = wordCount,
                            readTimeMinutes = readTimeMinutes
                        )
                    )
                }
            } catch (e: Exception) {
                callback(null)
            }
        }
    }
}
