package com.gintama.novabrowser.search

import android.content.Context
import com.gintama.novabrowser.core.navigation.SearchEngine

/**
 * Manages user's selected search engine preference and custom search URLs.
 */
object SearchEngineManager {

    private const val PREFS_NAME = "nova_settings"
    private const val KEY_SEARCH_ENGINE_ID = "pref_search_engine_id"
    private const val KEY_CUSTOM_SEARCH_URL = "pref_custom_search_url"

    fun getActiveEngine(context: Context): SearchEngine {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_SEARCH_ENGINE_ID, SearchEngine.DEFAULT.id)
        return SearchEngine.fromId(id)
    }

    fun getCustomUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_SEARCH_URL, "https://duckduckgo.com/?q=%s").orEmpty()
    }

    fun getActiveSearchTemplate(context: Context): String {
        val engine = getActiveEngine(context)
        return if (engine == SearchEngine.CUSTOM) {
            val custom = getCustomUrl(context)
            if (custom.isNotBlank()) custom else SearchEngine.DEFAULT.queryUrlTemplate
        } else {
            engine.queryUrlTemplate
        }
    }

    fun setActiveEngine(context: Context, engine: SearchEngine, customUrl: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_SEARCH_ENGINE_ID, engine.id)
        if (!customUrl.isNullOrBlank()) {
            editor.putString(KEY_CUSTOM_SEARCH_URL, customUrl.trim())
        }
        editor.apply()
    }

    fun buildSearchUrl(context: Context, query: String): String {
        val template = getActiveSearchTemplate(context)
        return SearchEngine.buildSearchUrl(query, template)
    }
}
