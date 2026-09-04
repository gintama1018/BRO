package com.gintama.novabrowser.core.navigation

/**
 * Tracks current navigation status for an active tab.
 */
data class NavigationState(
    val currentUrl: String = "about:blank",
    val title: String = "New Tab",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val isPrivate: Boolean = false
)
