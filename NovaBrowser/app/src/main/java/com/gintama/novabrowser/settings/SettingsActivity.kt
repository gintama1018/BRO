package com.gintama.novabrowser.settings

import android.content.Context
import android.os.Bundle
import android.webkit.WebStorage
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gintama.novabrowser.R
import com.gintama.novabrowser.ai.DeviceTierDetector
import com.gintama.novabrowser.core.controller.BrowserController
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var controller: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        controller = BrowserController(this)

        val btnBack = findViewById<ImageButton>(R.id.btnSettingsBack)
        btnBack.setOnClickListener { finish() }

        val switchLowMemory = findViewById<SwitchMaterial>(R.id.switchLowMemory)
        val tvTierInfo = findViewById<TextView>(R.id.tvDeviceTierInfo)
        val btnClearData = findViewById<Button>(R.id.btnClearBrowsingData)

        // SharedPreferences for Low Memory Mode
        val prefs = getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        val isLowMemory = prefs.getBoolean("pref_low_memory_mode", false)
        switchLowMemory.isChecked = isLowMemory

        // Device RAM tier detection
        val detectedTier = DeviceTierDetector.detectTier(this)
        tvTierInfo.text = "Detected: ${detectedTier.description}\nMax AI Target: ${detectedTier.maxModelSize}"

        switchLowMemory.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_low_memory_mode", isChecked).apply()
            val msg = if (isChecked) {
                "Low Memory Mode Enabled: AI generation disabled, RAM minimized."
            } else {
                "Automatic Tier Mode Restored."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ==========================================
        // Phase 4 & Phase 5: Ad-Block & Privacy Controls
        // ==========================================
        com.gintama.novabrowser.adblock.AdBlockEngine.init(this)

        val tvLifetimeBlocked = findViewById<TextView>(R.id.tvSettingsLifetimeBlocked)
        val switchAdBlockMaster = findViewById<SwitchMaterial>(R.id.switchAdBlockMaster)
        val switchCosmeticFiltering = findViewById<SwitchMaterial>(R.id.switchCosmeticFiltering)
        val switchBlockThirdPartyCookies = findViewById<SwitchMaterial>(R.id.switchBlockThirdPartyCookies)
        val switchHttpsOnly = findViewById<SwitchMaterial>(R.id.switchHttpsOnly)
        val switchDoNotTrack = findViewById<SwitchMaterial>(R.id.switchDoNotTrack)

        val lifetimeCount = com.gintama.novabrowser.adblock.AdBlockEngine.getLifetimeBlockedCount()
        tvLifetimeBlocked.text = "$lifetimeCount Ads & Trackers Blocked"

        switchAdBlockMaster.isChecked = com.gintama.novabrowser.adblock.AdBlockEngine.isMasterAdBlockEnabled()
        switchAdBlockMaster.setOnCheckedChangeListener { _, isChecked ->
            com.gintama.novabrowser.adblock.AdBlockEngine.setMasterAdBlockEnabled(isChecked)
            val msg = if (isChecked) "Ad & Tracker blocking enabled" else "Ad & Tracker blocking disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        switchCosmeticFiltering.isChecked = com.gintama.novabrowser.adblock.AdBlockEngine.isMasterCosmeticEnabled()
        switchCosmeticFiltering.setOnCheckedChangeListener { _, isChecked ->
            com.gintama.novabrowser.adblock.AdBlockEngine.setMasterCosmeticEnabled(isChecked)
        }

        switchBlockThirdPartyCookies.isChecked = com.gintama.novabrowser.adblock.AdBlockEngine.isThirdPartyCookiesBlocked()
        switchBlockThirdPartyCookies.setOnCheckedChangeListener { _, isChecked ->
            com.gintama.novabrowser.adblock.AdBlockEngine.setThirdPartyCookiesBlocked(isChecked)
            val msg = if (isChecked) "Third-party cookies blocked by default" else "Third-party cookies allowed"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        switchHttpsOnly.isChecked = com.gintama.novabrowser.adblock.AdBlockEngine.isHttpsOnlyMode()
        switchHttpsOnly.setOnCheckedChangeListener { _, isChecked ->
            com.gintama.novabrowser.adblock.AdBlockEngine.setHttpsOnlyMode(isChecked)
            val msg = if (isChecked) "HTTPS-Only mode enabled: http:// links will be upgraded" else "HTTPS-Only mode disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        switchDoNotTrack.isChecked = com.gintama.novabrowser.adblock.AdBlockEngine.isDntEnabled()
        switchDoNotTrack.setOnCheckedChangeListener { _, isChecked ->
            com.gintama.novabrowser.adblock.AdBlockEngine.setDntEnabled(isChecked)
        }

        btnClearData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Browsing Data")
                .setMessage("Clear all stored history, database records, and WebView web storage cache?")
                .setPositiveButton("Clear All") { _, _ ->
                    lifecycleScope.launch {
                        controller.clearHistory()
                        WebStorage.getInstance().deleteAllData()
                        Toast.makeText(this@SettingsActivity, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
