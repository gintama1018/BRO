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
        val RISKY_EXTENSIONS = setOf(
            "apk", "dex", "sh", "bat", "exe", "vbs", "js", "cmd", "msi", "scr", "jar"
        )

        fun isRiskyExtension(extension: String): Boolean {
            return RISKY_EXTENSIONS.contains(extension.lowercase().trim().removePrefix("."))
        }
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
        Toast.makeText(context, "Isolating file in app-private quarantine...", Toast.LENGTH_SHORT).show()

        scope.launch {
            val quarantineDir = File(context.cacheDir, "quarantine").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val safeName = filename.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val quarantineFile = File(quarantineDir, "${timestamp}_${safeName}.quarantine")

            var downloadedBytes = 0L
            var sha256Hex = ""

            try {
                // Physically stream bytes into app-private sandbox container
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    userAgent?.let { setRequestProperty("User-Agent", it) }
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                connection.connect()

                val digest = java.security.MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    quarantineFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                        }
                    }
                }

                sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }

                val sizeFormatted = when {
                    downloadedBytes >= 1024 * 1024 -> String.format("%.2f MB", downloadedBytes / (1024.0 * 1024.0))
                    downloadedBytes >= 1024 -> String.format("%.1f KB", downloadedBytes / 1024.0)
                    else -> "$downloadedBytes Bytes"
                }

                val downloadId = db.recordDownload(
                    url = url,
                    filename = filename,
                    mimeType = mimetype,
                    status = DownloadStatus.QUARANTINED,
                    riskReason = "SHA-256: $sha256Hex | Size: $sizeFormatted | Sandbox: ${quarantineFile.name}"
                )

                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(context)
                        .setTitle("🛡️ Executable Isolated in Quarantine")
                        .setMessage(
                            "File: $filename\n" +
                            "Size: $sizeFormatted\n" +
                            "SHA-256: ${sha256Hex.take(16)}...${sha256Hex.takeLast(8)}\n\n" +
                            "Risk Classification: Executable / Script\n" +
                            "Storage Location: App-Private Quarantine Sandbox\n\n" +
                            "This file is isolated in private application storage and cannot execute on your system.\n\n" +
                            "Do you want to release it to your public Downloads folder, or permanently purge it?"
                        )
                        .setPositiveButton("Release to Downloads") { _, _ ->
                            scope.launch {
                                try {
                                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    publicDir.mkdirs()
                                    val targetFile = File(publicDir, filename)
                                    quarantineFile.copyTo(targetFile, overwrite = true)
                                    quarantineFile.delete()

                                    db.updateDownloadStatus(downloadId, DownloadStatus.COMPLETED, "Released by user to Downloads")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "File released to public Downloads: $filename", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to copy file: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Purge & Discard (Safe)") { _, _ ->
                            scope.launch {
                                quarantineFile.delete()
                                db.updateDownloadStatus(downloadId, DownloadStatus.BLOCKED, "Purged from quarantine by user")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Threat purged from quarantine.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setCancelable(false)
                        .show()
                }

            } catch (e: Exception) {
                quarantineFile.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Quarantine download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
