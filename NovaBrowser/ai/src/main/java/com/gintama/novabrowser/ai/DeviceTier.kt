package com.gintama.novabrowser.ai

import android.app.ActivityManager
import android.content.Context

/**
 * RAM-based device tiering defined in ARCHITECTURE.md §6.1
 */
enum class DeviceTier(val description: String, val maxModelSize: String) {
    MINIMAL("Minimal (<= 2GB RAM)", "No LLM loaded (Lexical FTS only)"),
    LIGHT("Light (3-4GB RAM)", "0.5B - 1.5B GGUF (On-Demand)"),
    STANDARD("Standard (6GB+ RAM)", "Up to 3B GGUF + Embeddings")
}

object DeviceTierDetector {

    fun detectTier(context: Context): DeviceTier {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return DeviceTier.MINIMAL

        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        return when {
            totalRamMb <= 2500 -> DeviceTier.MINIMAL
            totalRamMb <= 4500 -> DeviceTier.LIGHT
            else -> DeviceTier.STANDARD
        }
    }
}
