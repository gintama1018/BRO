package com.gintama.novabrowser.downloads

import android.content.Context
import android.os.Environment
import android.util.Log
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.DownloadStatus
import com.gintama.novabrowser.notifications.NovaNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * NovaDownloadEngine: Multi-threaded, HTTP Range-aware resumable download manager
 * with real-time speed telemetry, ETA calculation, pause/resume state control,
 * and quarantine isolation support.
 */
object NovaDownloadEngine {

    private const val TAG = "NovaDownloadEngine"
    private const val BUFFER_SIZE = 16384 // 16 KB buffer for high-throughput I/O

    data class DownloadSnapshot(
        val id: Long,
        val url: String,
        val filename: String,
        val mimeType: String?,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedText: String,
        val etaText: String,
        val status: DownloadStatus,
        val isQuarantine: Boolean,
        val filePath: String
    ) {
        val progressPercent: Int
            get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeSnapshots = ConcurrentHashMap<Long, DownloadSnapshot>()

    private val _downloadsFlow = MutableStateFlow<Map<Long, DownloadSnapshot>>(emptyMap())
    val downloadsFlow: StateFlow<Map<Long, DownloadSnapshot>> = _downloadsFlow.asStateFlow()

    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0)
            bytesPerSecond > 0 -> "$bytesPerSecond B/s"
            else -> "0 KB/s"
        }
    }

    fun formatEta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0 || remainingBytes <= 0) return "--"
        val seconds = remainingBytes / bytesPerSecond
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    fun startDownload(
        context: Context,
        url: String,
        filename: String,
        mimeType: String?,
        userAgent: String?,
        isQuarantine: Boolean
    ): Long {
        val db = NovaDatabaseHelper.getInstance(context)
        val initialStatus = if (isQuarantine) DownloadStatus.QUARANTINED else DownloadStatus.DOWNLOADING
        val downloadId = db.recordDownload(
            url = url,
            filename = filename,
            mimeType = mimeType,
            status = initialStatus,
            riskReason = if (isQuarantine) "Isolated in quarantine" else null
        )

        val targetDir = if (isQuarantine) {
            File(context.cacheDir, "quarantine").apply { mkdirs() }
        } else {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDir.exists() || publicDir.mkdirs()) publicDir else context.filesDir
        }

        val targetFile = File(targetDir, if (isQuarantine) "${System.currentTimeMillis()}_$filename.quarantine" else filename)

        val snapshot = DownloadSnapshot(
            id = downloadId,
            url = url,
            filename = filename,
            mimeType = mimeType,
            downloadedBytes = 0L,
            totalBytes = -1L,
            speedText = "Connecting...",
            etaText = "--",
            status = initialStatus,
            isQuarantine = isQuarantine,
            filePath = targetFile.absolutePath
        )
        activeSnapshots[downloadId] = snapshot
        _downloadsFlow.value = activeSnapshots.toMap()

        val job = scope.launch {
            executeDownload(context, downloadId, url, userAgent, targetFile, mimeType, isQuarantine)
        }
        activeJobs[downloadId] = job
        return downloadId
    }

    fun pauseDownload(downloadId: Long, context: Context) {
        val job = activeJobs.remove(downloadId)
        job?.cancel()

        val current = activeSnapshots[downloadId] ?: return
        val paused = current.copy(
            status = DownloadStatus.PAUSED,
            speedText = "Paused"
        )
        activeSnapshots[downloadId] = paused
        _downloadsFlow.value = activeSnapshots.toMap()

        NovaDatabaseHelper.getInstance(context).updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
        NovaNotificationHelper.showDownloadProgress(
            context,
            downloadId.toInt(),
            current.filename,
            paused.progressPercent,
            indeterminate = false
        )
    }

    fun resumeDownload(downloadId: Long, context: Context, userAgent: String?) {
        val current = activeSnapshots[downloadId] ?: return
        if (activeJobs[downloadId]?.isActive == true) return // Already running

        val targetFile = File(current.filePath)
        val job = scope.launch {
            executeDownload(context, downloadId, current.url, userAgent, targetFile, current.mimeType, current.isQuarantine)
        }
        activeJobs[downloadId] = job
    }

    fun cancelDownload(downloadId: Long, context: Context) {
        val job = activeJobs.remove(downloadId)
        job?.cancel()

        val current = activeSnapshots.remove(downloadId)
        _downloadsFlow.value = activeSnapshots.toMap()

        if (current != null) {
            try { File(current.filePath).delete() } catch (_: Exception) {}
        }
        NovaDatabaseHelper.getInstance(context).updateDownloadStatus(downloadId, DownloadStatus.FAILED, "Cancelled by user")
    }

    private fun executeDownload(
        context: Context,
        downloadId: Long,
        urlStr: String,
        userAgent: String?,
        targetFile: File,
        mimeType: String?,
        isQuarantine: Boolean
    ) {
        val db = NovaDatabaseHelper.getInstance(context)
        var connection: HttpURLConnection? = null

        try {
            var existingBytes = if (targetFile.exists()) targetFile.length() else 0L

            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                userAgent?.let { setRequestProperty("User-Agent", it) }
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }
            connection.connect()

            val responseCode = connection.responseCode
            val isRangeAccepted = responseCode == HttpURLConnection.HTTP_PARTIAL // 206
            if (!isRangeAccepted && existingBytes > 0) {
                // Server doesn't support range requests; reset file to beginning
                existingBytes = 0L
                targetFile.delete()
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (contentLength > 0) {
                if (isRangeAccepted) existingBytes + contentLength else contentLength
            } else {
                -1L
            }

            db.updateDownloadStatus(downloadId, if (isQuarantine) DownloadStatus.QUARANTINED else DownloadStatus.DOWNLOADING)

            var downloadedBytes = existingBytes
            val raf = RandomAccessFile(targetFile, "rw")
            if (isRangeAccepted) {
                raf.seek(existingBytes)
            } else {
                raf.setLength(0)
            }

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var lastSpeedCalcTime = System.currentTimeMillis()
            var bytesSinceLastSpeedCalc = 0L
            var currentSpeed = 0L

            val inputStream = connection.inputStream
            inputStream.use { input ->
                raf.use { fileOut ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        fileOut.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        bytesSinceLastSpeedCalc += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSpeedCalcTime
                        if (elapsed >= 700L) {
                            currentSpeed = (bytesSinceLastSpeedCalc * 1000L) / elapsed
                            val speedText = formatSpeed(currentSpeed)
                            val remaining = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
                            val etaText = formatEta(remaining, currentSpeed)

                            val snapshot = DownloadSnapshot(
                                id = downloadId,
                                url = urlStr,
                                filename = targetFile.name,
                                mimeType = mimeType,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speedText = speedText,
                                etaText = etaText,
                                status = if (isQuarantine) DownloadStatus.QUARANTINED else DownloadStatus.DOWNLOADING,
                                isQuarantine = isQuarantine,
                                filePath = targetFile.absolutePath
                            )
                            activeSnapshots[downloadId] = snapshot
                            _downloadsFlow.value = activeSnapshots.toMap()

                            NovaNotificationHelper.showDownloadProgress(
                                context,
                                downloadId.toInt(),
                                targetFile.name,
                                snapshot.progressPercent,
                                indeterminate = totalBytes <= 0
                            )

                            lastSpeedCalcTime = now
                            bytesSinceLastSpeedCalc = 0L
                        }
                    }
                }
            }

            // Completed!
            activeJobs.remove(downloadId)
            val finalStatus = if (isQuarantine) DownloadStatus.QUARANTINED else DownloadStatus.COMPLETED
            db.updateDownloadStatus(downloadId, finalStatus)

            val completedSnapshot = DownloadSnapshot(
                id = downloadId,
                url = urlStr,
                filename = targetFile.name,
                mimeType = mimeType,
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                speedText = "Completed",
                etaText = "--",
                status = finalStatus,
                isQuarantine = isQuarantine,
                filePath = targetFile.absolutePath
            )
            activeSnapshots[downloadId] = completedSnapshot
            _downloadsFlow.value = activeSnapshots.toMap()

            if (isQuarantine) {
                NovaNotificationHelper.showQuarantineAlert(
                    context,
                    downloadId.toInt(),
                    targetFile.name,
                    "Held in sandbox: ${formatSpeed(downloadedBytes)}"
                )
            } else {
                NovaNotificationHelper.showDownloadComplete(
                    context,
                    downloadId.toInt(),
                    targetFile.name,
                    targetFile,
                    mimeType
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed or interrupted: ${e.message}", e)
            activeJobs.remove(downloadId)
            db.updateDownloadStatus(downloadId, DownloadStatus.FAILED, e.message)

            val current = activeSnapshots[downloadId]
            if (current != null) {
                val failedSnapshot = current.copy(
                    status = DownloadStatus.FAILED,
                    speedText = "Failed: ${e.message?.take(30)}"
                )
                activeSnapshots[downloadId] = failedSnapshot
                _downloadsFlow.value = activeSnapshots.toMap()
            }
        } finally {
            connection?.disconnect()
        }
    }
}
