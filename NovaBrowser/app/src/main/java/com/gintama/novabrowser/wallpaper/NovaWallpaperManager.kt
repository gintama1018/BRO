package com.gintama.novabrowser.wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.gintama.novabrowser.R
import java.io.File

/**
 * NovaWallpaperManager: Manages Start Canvas wallpaper themes, custom gallery photos,
 * ambient gradients, and persistence.
 */
object NovaWallpaperManager {

    enum class WallpaperPreset(val displayName: String) {
        DEEP_SPACE("Deep Space Obsidian"),
        COSMIC_AURORA("Cosmic Aurora"),
        CYBERPUNK_NEON("Cyberpunk Neon"),
        MIDNIGHT_SAPPHIRE("Midnight Sapphire"),
        CUSTOM_IMAGE("Custom Gallery Photo")
    }

    private const val PREF_NAME = "nova_wallpaper_prefs"
    private const val KEY_PRESET = "active_wallpaper_preset"
    private const val CUSTOM_FILE_NAME = "custom_wallpaper.jpg"

    fun getActivePreset(context: Context): WallpaperPreset {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_PRESET, WallpaperPreset.DEEP_SPACE.name) ?: WallpaperPreset.DEEP_SPACE.name
        return try {
            WallpaperPreset.valueOf(name)
        } catch (_: Exception) {
            WallpaperPreset.DEEP_SPACE
        }
    }

    fun setActivePreset(context: Context, preset: WallpaperPreset) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PRESET, preset.name).apply()
    }

    fun saveCustomWallpaper(context: Context, uri: Uri): Boolean {
        return try {
            val file = File(context.filesDir, CUSTOM_FILE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            setActivePreset(context, WallpaperPreset.CUSTOM_IMAGE)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyWallpaper(context: Context, wallpaperView: ImageView, dimmerView: View) {
        val preset = getActivePreset(context)
        when (preset) {
            WallpaperPreset.DEEP_SPACE -> {
                wallpaperView.setImageDrawable(null)
                wallpaperView.setBackgroundColor(context.getColor(R.color.canvas_base))
                dimmerView.visibility = View.GONE
            }
            WallpaperPreset.COSMIC_AURORA -> {
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xFF06141D.toInt(), 0xFF0D2838.toInt(), 0xFF081C15.toInt())
                )
                wallpaperView.setImageDrawable(gradient)
                dimmerView.visibility = View.GONE
            }
            WallpaperPreset.CYBERPUNK_NEON -> {
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xFF13091F.toInt(), 0xFF1C1335.toInt(), 0xFF0F0B18.toInt())
                )
                wallpaperView.setImageDrawable(gradient)
                dimmerView.visibility = View.GONE
            }
            WallpaperPreset.MIDNIGHT_SAPPHIRE -> {
                val gradient = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xFF070B19.toInt(), 0xFF0F1B38.toInt(), 0xFF0A0F24.toInt())
                )
                wallpaperView.setImageDrawable(gradient)
                dimmerView.visibility = View.GONE
            }
            WallpaperPreset.CUSTOM_IMAGE -> {
                val customFile = File(context.filesDir, CUSTOM_FILE_NAME)
                if (customFile.exists()) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(customFile.absolutePath)
                        wallpaperView.setImageBitmap(bitmap)
                        wallpaperView.scaleType = ImageView.ScaleType.CENTER_CROP
                        dimmerView.visibility = View.VISIBLE
                    } catch (_: Exception) {
                        applyWallpaperDefault(context, wallpaperView, dimmerView)
                    }
                } else {
                    applyWallpaperDefault(context, wallpaperView, dimmerView)
                }
            }
        }
    }

    private fun applyWallpaperDefault(context: Context, wallpaperView: ImageView, dimmerView: View) {
        wallpaperView.setImageDrawable(null)
        wallpaperView.setBackgroundColor(context.getColor(R.color.canvas_base))
        dimmerView.visibility = View.GONE
    }
}
