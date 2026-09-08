package com.gintama.novabrowser.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gintama.novabrowser.R
import com.gintama.novabrowser.ui.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * PwaShortcutManager: Generates home screen shortcuts for Progressive Web Apps (PWAs)
 * and websites, enabling standalone fullscreen app-like execution.
 */
object PwaShortcutManager {

    const val EXTRA_STANDALONE = "extra_standalone_pwa"
    const val ACTION_OPEN_PWA = "com.gintama.novabrowser.ACTION_OPEN_PWA"

    fun isSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }

    fun generateBadgedIcon(initial: String, favicon: Bitmap? = null): Bitmap {
        if (favicon != null && !favicon.isRecycled && favicon.width > 16) {
            return favicon
        }
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background rounded squircle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#151821")
            style = Paint.Style.FILL
        }
        val rect = RectF(8f, 8f, (size - 8).toFloat(), (size - 8).toFloat())
        canvas.drawRoundRect(rect, 44f, 44f, bgPaint)

        // Border ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(rect, 44f, 44f, borderPaint)

        // Text initial
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F9FAFB")
            textSize = 80f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val char = if (initial.isNotBlank()) initial.take(1).uppercase() else "N"
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(char, canvas.width / 2f, yPos, textPaint)

        return bitmap
    }

    fun pinShortcut(
        context: Context,
        url: String,
        title: String,
        isStandalone: Boolean,
        favicon: Bitmap? = null
    ): Boolean {
        if (!isSupported(context)) return false

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
            putExtra(EXTRA_STANDALONE, isStandalone)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val iconBitmap = generateBadgedIcon(title.ifBlank { url }, favicon)
        val shortcutId = "nova_pwa_${url.hashCode()}"

        val pinShortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
            .setIcon(IconCompat.createWithBitmap(iconBitmap))
            .setShortLabel(title.take(25))
            .setLongLabel(title.take(50))
            .setIntent(launchIntent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
    }

    fun showAddToHomeDialog(
        activity: Activity,
        currentUrl: String,
        currentTitle: String,
        favicon: Bitmap? = null,
        onAdded: (() -> Unit)? = null
    ) {
        if (currentUrl.isBlank() || currentUrl.startsWith("about:")) {
            Toast.makeText(activity, "Open a website first to add to home screen", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_add_to_home, null)
        dialog.setContentView(view)

        val tvInitial = view.findViewById<TextView>(R.id.tvShortcutInitial)
        val ivFavicon = view.findViewById<ImageView>(R.id.ivShortcutFavicon)
        val tvUrl = view.findViewById<TextView>(R.id.tvShortcutUrl)
        val etName = view.findViewById<EditText>(R.id.etShortcutName)
        val switchStandalone = view.findViewById<SwitchMaterial>(R.id.switchStandaloneMode)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelShortcut)
        val btnAdd = view.findViewById<Button>(R.id.btnAddShortcut)

        val cleanTitle = currentTitle.ifBlank {
            try {
                java.net.URI(currentUrl).host ?: currentUrl
            } catch (_: Exception) {
                currentUrl
            }
        }
        etName.setText(cleanTitle)
        tvUrl.text = currentUrl

        if (favicon != null && !favicon.isRecycled) {
            ivFavicon.setImageBitmap(favicon)
            ivFavicon.visibility = View.VISIBLE
            tvInitial.visibility = View.GONE
        } else {
            tvInitial.text = cleanTitle.take(1).uppercase()
            tvInitial.visibility = View.VISIBLE
            ivFavicon.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val titleToUse = etName.text.toString().trim().ifBlank { cleanTitle }
            val isStandalone = switchStandalone.isChecked

            val success = pinShortcut(activity, currentUrl, titleToUse, isStandalone, favicon)
            if (success) {
                Toast.makeText(activity, "Shortcut created for $titleToUse", Toast.LENGTH_SHORT).show()
                onAdded?.invoke()
            } else {
                Toast.makeText(activity, "Could not add shortcut: launcher not supported", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }
}
