package com.gintama.novabrowser.core.db

import java.io.Serializable

/**
 * SiteShieldRecord: Granular, per-domain security & privacy settings.
 *
 * Persisted in SQLite with O(1) in-memory cache resolution.
 */
data class SiteShieldRecord(
    val domain: String,
    val shieldsEnabled: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val cosmeticEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable {

    /**
     * Ad and tracker network interception is active only if master shields are UP
     * AND ad blocking is enabled for this domain.
     */
    fun isAdBlockActive(): Boolean = shieldsEnabled && adBlockEnabled

    /**
     * Cosmetic CSS element hiding is active only if master shields are UP
     * AND cosmetic filtering is enabled for this domain.
     */
    fun isCosmeticActive(): Boolean = shieldsEnabled && cosmeticEnabled

    /**
     * JavaScript is allowed if master shields are DOWN (bypassed) OR
     * if JavaScript is explicitly enabled.
     */
    fun isJavaScriptAllowed(): Boolean = !shieldsEnabled || javaScriptEnabled

    /**
     * Third-party cookies are blocked only if master shields are UP
     * AND third-party cookie blocking is enabled for this domain.
     */
    fun isThirdPartyCookiesBlocked(): Boolean = shieldsEnabled && blockThirdPartyCookies
}
