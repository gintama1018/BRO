package com.gintama.novabrowser.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.gintama.novabrowser.R
import com.gintama.novabrowser.downloads.DownloadsActivity
import java.io.File

/**
 * NovaNotificationHelper: High-priority, Android 8.0 - 15+ compatible notification engine
 * for real-time download tracking, security quarantine warnings, and offline page export status.
 */
object NovaNotificationHelper {

    const val CHANNEL_DOWNLOADS = "nova_downloads"
    const val CHANNEL_SECURITY = "nova_security"
    const val CHANNEL_OFFLINE = "nova_offline"

    private var channelsInitialized = false

    fun initChannels(context: Context) {
        if (channelsInitialized || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Downloads Channel
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Nova Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progress and completion of file downloads"
            setShowBadge(true)
        }

        // 2. Security Alerts Channel
        val securityChannel = NotificationChannel(
            CHANNEL_SECURITY,
            "Nova Security & Quarantine",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Interception of malicious or quarantined downloads"
            setShowBadge(true)
            enableVibration(true)
        }

        // 3. Saved Pages & Offline Channel
        val offlineChannel = NotificationChannel(
            CHANNEL_OFFLINE,
            "Nova Saved Pages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Completion of Save as PDF and Web Archive captures"
            setShowBadge(true)
        }

        manager.createNotificationChannels(listOf(downloadChannel, securityChannel, offlineChannel))
        channelsInitialized = true
    }

    fun showDownloadProgress(
        context: Context,
        notificationId: Int,
        filename: String,
        progress: Int,
        indeterminate: Boolean = false
    ) {
        initChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $filename")
            .setContentText(if (indeterminate) "Downloading..." else "$progress%")
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        manager.notify(notificationId, builder.build())
    }

    fun showDownloadComplete(
        context: Context,
        notificationId: Int,
        filename: String,
        file: File,
        mimeType: String?
    ) {
        initChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val contentUri: Uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                Uri.fromFile(file)
            }
            setDataAndType(contentUri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(filename)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        manager.notify(notificationId, builder.build())
    }

    fun showQuarantineAlert(
        context: Context,
        notificationId: Int,
        filename: String,
        reason: String
    ) {
        initChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, DownloadsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SECURITY)
            .setSmallIcon(R.drawable.ic_security)
            .setContentTitle("File Quarantined by Nova Shield")
            .setContentText("$filename held in sandbox container")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$filename has been isolated into app-private quarantine.\nReason: $reason\nTap to inspect or release."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        manager.notify(notificationId, builder.build())
    }

    fun showPageSaved(
        context: Context,
        notificationId: Int,
        title: String,
        filePath: String,
        isPdf: Boolean
    ) {
        initChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val file = File(filePath)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val contentUri: Uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                Uri.fromFile(file)
            }
            val mime = if (isPdf) "application/pdf" else "multipart/related"
            setDataAndType(contentUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeName = if (isPdf) "PDF Document" else "Web Archive"
        val builder = NotificationCompat.Builder(context, CHANNEL_OFFLINE)
            .setSmallIcon(R.drawable.ic_bookmark_border)
            .setContentTitle("Page Saved ($typeName)")
            .setContentText(title.ifBlank { file.name })
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        manager.notify(notificationId, builder.build())
    }
}
