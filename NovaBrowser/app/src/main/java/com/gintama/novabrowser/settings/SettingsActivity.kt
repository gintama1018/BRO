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
