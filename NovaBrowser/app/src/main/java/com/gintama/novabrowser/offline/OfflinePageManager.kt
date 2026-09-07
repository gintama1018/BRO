package com.gintama.novabrowser.offline

import android.app.Activity
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OfflinePageManager: Manages exporting, archiving, and retrieving offline web content.
 *
 * Supported formats:
 * 1. High-fidelity paginated PDF via Android Print Framework (PrintManager)
 * 2. Standalone Web Archive (.mht / .mhtml) via WebView.saveWebArchive
 */
object OfflinePageManager {

    private const val TAG = "OfflinePageManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Triggers Android's native system print sheet pre-configured for PDF output.
     * Allows the user to save as PDF to Google Drive, local storage, or send to a physical printer.
     */
    fun printOrSavePdf(activity: Activity, webView: WebView, title: String): Boolean {
        return try {
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return false

            val sanitizedTitle = sanitizeFileName(title, "pdf").removeSuffix(".pdf")
            val jobName = "NovaBrowser_$sanitizedTitle"
            val printAdapter = webView.createPrintDocumentAdapter(jobName)

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(jobName, printAdapter, printAttributes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch print/PDF dialog", e)
            false
        }
    }

    /**
     * Saves the loaded webpage into an offline .mht web archive containing
     * complete HTML, CSS, images, and fonts.
     */
    fun saveWebArchive(
        activity: Activity,
        webView: WebView,
        title: String,
        url: String,
        onComplete: (File?) -> Unit
    ) {
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadDir = if (publicDir.exists() || publicDir.mkdirs()) {
                publicDir
            } else {
                activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = sanitizeFileName(title, "mht").removeSuffix(".mht")
            val filename = "${baseName}_$timestamp.mht"
            val targetFile = File(downloadDir, filename)

            webView.saveWebArchive(targetFile.absolutePath, false) { savedPath ->
                if (!savedPath.isNullOrBlank()) {
                    val file = File(savedPath)
                    val db = NovaDatabaseHelper.getInstance(activity)

                    scope.launch {
                        try {
                            db.recordDownload(
                                url = url,
                                filename = file.name,
                                mimeType = "multipart/related",
                                status = DownloadStatus.SAFE,
                                riskReason = "Offline Web Archive (.mht)"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to record archive in downloads db", e)
                        }
                    }

                    try {
                        MediaScannerConnection.scanFile(
                            activity,
                            arrayOf(file.absolutePath),
                            arrayOf("multipart/related"),
                            null
                        )
                    } catch (_: Exception) {}

                    activity.runOnUiThread { onComplete(file) }
                } else {
                    activity.runOnUiThread { onComplete(null) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web archive save", e)
            onComplete(null)
        }
    }

    /**
     * Cleanses a proposed filename of illegal filesystem characters and path traversal patterns.
     */
    fun sanitizeFileName(title: String, extension: String): String {
        var clean = title.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ', '_')

        if (clean.isBlank()) {
            clean = "Nova_Saved_Page"
        }

        val ext = extension.removePrefix(".").lowercase()
        val suffix = ".$ext"

        // Limit base length to 64 chars to prevent OS path length limits
        if (clean.length > 64) {
            clean = clean.substring(0, 64).trimEnd()
        }

        return if (clean.endsWith(suffix, ignoreCase = true)) clean else "$clean$suffix"
    }

    /**
     * Checks if a filename or URL represents an offline web archive format (.mht / .mhtml).
     */
    fun isWebArchive(fileOrUrl: String?): Boolean {
        if (fileOrUrl.isNullOrBlank()) return false
        val clean = fileOrUrl.substringBefore("?").substringBefore("#").lowercase()
        return clean.endsWith(".mht") || clean.endsWith(".mhtml")
    }
}
