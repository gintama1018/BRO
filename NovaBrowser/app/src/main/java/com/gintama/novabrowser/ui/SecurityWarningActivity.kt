package com.gintama.novabrowser.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gintama.novabrowser.R

/**
 * Explainable Security Intervention Screen
 * Faithfully recreating the approved Liquid System reference design (novabrowser_security_warning).
 * Explains WHY a page was blocked or warned rather than giving an ambiguous error.
 */
class SecurityWarningActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_URL = "extra_target_url"
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_RISK_STATE = "extra_risk_state"
        const val EXTRA_REASONS = "extra_reasons"
        const val EXTRA_RULE_ID = "extra_rule_id"
        const val EXTRA_RISK_SCORE = "extra_risk_score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_warning)

        val targetUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: "about:blank"
        val action = intent.getStringExtra(EXTRA_ACTION) ?: "BLOCK"
        val riskState = intent.getStringExtra(EXTRA_RISK_STATE) ?: "BLOCKED"
        val reasons = intent.getStringArrayListExtra(EXTRA_REASONS) ?: arrayListOf("Threat policy violation")
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: "LOCAL_SENTINEL_GATE"
        val riskScore = intent.getDoubleExtra(EXTRA_RISK_SCORE, 1.0)

        val btnBack = findViewById<ImageButton>(R.id.btnWarningBack)
        val ivIcon = findViewById<ImageView>(R.id.ivWarningIcon)
        val tvTitle = findViewById<TextView>(R.id.tvWarningTitle)
        val tvBadge = findViewById<TextView>(R.id.tvRiskLevelBadge)
        val tvInterceptedBadge = findViewById<TextView>(R.id.tvInterceptedStatusBadge)
        val tvUrl = findViewById<TextView>(R.id.tvTargetUrl)
        val tvReasons = findViewById<TextView>(R.id.tvThreatReasons)
        val tvSnapshot = findViewById<TextView>(R.id.tvSnapshotInfo)
        val btnReturn = findViewById<Button>(R.id.btnReturnToSafety)
        val btnDiagnostics = findViewById<Button>(R.id.btnToggleDiagnostics)
        val layoutDiagnostics = findViewById<LinearLayout>(R.id.layoutDiagnosticsDrawer)
        val tvDiagnosticsHex = findViewById<TextView>(R.id.tvDiagnosticsHex)
        val btnProceed = findViewById<Button>(R.id.btnProceedAnyway)

        tvUrl.text = targetUrl

        // Format detection breakdown reasons
        val formattedReasons = reasons.joinToString("\n") { "• $it" }
        tvReasons.text = formattedReasons

        tvSnapshot.text = "Threat DB: Active Snapshot | Score: ${String.format("%.2f", riskScore)}"

        // Security diagnostics details
        val ruleLabel = if (ruleId.isNotBlank()) ruleId else "LOCAL_HEURISTIC_RULE"
        tvDiagnosticsHex.text = "ENGINE: Offline Deterministic Security Gate\nRULE_ID: $ruleLabel\nACTION: $action\nRISK_STATE: $riskState\nRISK_SCORE: ${String.format("%.2f", riskScore)}\nEVALUATION: Top-Level Request Filter\nSTORAGE: App-Private Database (WAL Mode)"

        var isDiagnosticsOpen = false
        btnDiagnostics.setOnClickListener {
            isDiagnosticsOpen = !isDiagnosticsOpen
            layoutDiagnostics.visibility = if (isDiagnosticsOpen) View.VISIBLE else View.GONE
            btnDiagnostics.text = if (isDiagnosticsOpen) "Hide Technical Details" else "Advanced Technical Details"
        }

        btnBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        if (action == "BLOCK" || riskState == "BLOCKED") {
            tvTitle.text = "ACCESS BLOCKED"
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.risk_blocked))
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.risk_blocked))
            tvBadge.text = "LOCAL SENTINEL HARD BLOCK"
            tvInterceptedBadge.text = "BLOCKED • THREAT FEED"

            // Non-bypassable for known malware per SECURITY.md
            btnProceed.visibility = View.GONE
        } else {
            val isHighRisk = riskState == "HIGH_RISK"
            val color = if (isHighRisk) R.color.risk_blocked else R.color.risk_suspicious
            tvTitle.text = if (isHighRisk) "Security Warning" else "Suspicious Site"
            tvTitle.setTextColor(ContextCompat.getColor(this, color))
            ivIcon.setColorFilter(ContextCompat.getColor(this, color))
            tvBadge.text = "LOCAL HEURISTIC SHIELD ($riskState)"
            tvInterceptedBadge.text = "SUSPICIOUS • HEURISTIC"

            btnProceed.visibility = View.VISIBLE
            btnProceed.setOnClickListener {
                val result = Intent().apply {
                    putExtra("override_url", targetUrl)
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            }
        }

        btnReturn.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
