package com.gintama.novabrowser.ai

import android.content.Context

/**
 * Phase 3 AI Engine Contract.
 * In Phase 1 and 2, local AI is dormant and uninitialized to guarantee zero RAM bloat.
 */
class AiEngine(private val context: Context) {

    val currentTier: DeviceTier by lazy {
        DeviceTierDetector.detectTier(context)
    }

    fun isLlmAvailable(): Boolean {
        return currentTier != DeviceTier.MINIMAL
    }
}
