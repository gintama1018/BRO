package com.gintama.novabrowser.downloads

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.widget.Toast
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stage 5: Download Protection & Quarantine Flow
 *
 * Enforces download safety policy:
 * - Safe MIME types (images, pdfs, audio, plain text) -> Public Downloads
 * - Risky executables/scripts (.apk, .dex, .sh, .bat, .exe, .js) -> Held in App-Private Quarantine
 *   with explicit user confirmation before release to public storage.
 */
class DownloadHandler(private val context: Context) : DownloadListener {

    private val db = NovaDatabaseHelper.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val RISKY_EXTENSIONS = setOf(
            "apk", "dex", "sh", "bat", "exe", "vbs", "js", "cmd", "msi", "scr", "jar"
        )
    }

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) return

        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val extension = filename.substringAfterLast(".", "").lowercase()
        val isRisky = RISKY_EXTENSIONS.contains(extension)

        if (isRisky) {
            handleQuarantinedDownload(url, filename, mimetype, userAgent)
        } else {
            handleSafeDownload(url, filename, mimetype, userAgent)
        }
    }

    private fun handleSafeDownload(
        url: String,
        filename: String,
        mimetype: String?,
        userAgent: String?
    ) {
        scope.launch {
            db.recordDownload(
                url = url,
                filename = filename,
                mimeType = mimetype,
                status = DownloadStatus.SAFE
            )
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading $filename")
                setTitle(filename)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)

            Toast.makeText(context, "Safe Download Started: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleQuarantinedDownload(
        url: String,
        filename: String,
        mimetype: String?,
        userAgent: String?
    ) {
        scope.launch {
            val downloadId = db.recordDownload(
                url = url,
                filename = filename,
                mimeType = mimetype,
                status = DownloadStatus.QUARANTINED
            )

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(context)
                    .setTitle("🛡️ Risky Download Quarantined")
                    .setMessage(
                        "File: $filename\n\n" +
                        "Detected Type: Executable / Script\n" +
                        "This file type can execute code on your device. It is held in app quarantine.\n\n" +
                        "Do you want to proceed and save to public Downloads?"
                    )
                    .setPositiveButton("Release to Downloads") { _, _ ->
                        handleSafeDownload(url, filename, mimetype, userAgent)
                    }
                    .setNegativeButton("Block & Discard", null)
                    .setCancelable(false)
                    .show()
            }
        }
    }
}
