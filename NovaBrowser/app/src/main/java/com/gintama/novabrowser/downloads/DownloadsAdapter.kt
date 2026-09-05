package com.gintama.novabrowser.downloads

import android.content.Context
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.core.model.DownloadItem
import com.gintama.novabrowser.core.model.DownloadStatus
import com.gintama.novabrowser.ui.motion.NovaMotion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadsAdapter(
    private var items: List<DownloadItem>,
    private val onItemClick: (DownloadItem, File?) -> Unit,
    private val onShareClick: (DownloadItem, File?) -> Unit,
    private val onDeleteClick: (DownloadItem, File?) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    fun updateItems(newItems: List<DownloadItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
        return DownloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DownloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivDownloadIcon)
        private val tvFilename: TextView = itemView.findViewById(R.id.tvDownloadFilename)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvDownloadMeta)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvDownloadStatusBadge)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShareDownload)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteDownload)

        fun bind(item: DownloadItem) {
            val context = itemView.context
            val filename = item.filename ?: item.url.substringAfterLast("/").ifBlank { "download" }
            tvFilename.text = filename

            // Resolve file on disk
            val file = resolveFile(context, item, filename)
            val fileSizeStr = if (file != null && file.exists()) {
                formatFileSize(file.length())
            } else {
                item.mimeType ?: "File"
            }

            val dateStr = dateFormat.format(Date(item.createdAt))
            tvMeta.text = "$fileSizeStr • $dateStr"

            // Status Badge & Colors
            when (item.status) {
                DownloadStatus.SAFE, DownloadStatus.COMPLETED -> {
                    tvStatusBadge.text = "SAFE"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_emerald)
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.risk_safe_on_container))
                }
                DownloadStatus.QUARANTINED -> {
                    tvStatusBadge.text = "QUARANTINED"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.risk_suspicious_on_container))
                }
                DownloadStatus.BLOCKED -> {
                    tvStatusBadge.text = "BLOCKED"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.risk_blocked_on_container))
                }
                DownloadStatus.PENDING -> {
                    tvStatusBadge.text = "DOWNLOADING"
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_emerald)
                    tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.risk_safe_on_container))
                }
            }

            // File Icon Styling
            val extension = filename.substringAfterLast(".", "").lowercase()
            when {
                extension in listOf("apk", "dex") -> {
                    ivIcon.setImageResource(R.drawable.ic_security)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.risk_suspicious))
                }
                extension in listOf("jpg", "jpeg", "png", "webp", "gif") -> {
                    ivIcon.setImageResource(R.drawable.ic_file)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.risk_safe))
                }
                extension in listOf("mp4", "mkv", "webm", "avi") -> {
                    ivIcon.setImageResource(R.drawable.ic_file)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.incognito_accent))
                }
                extension in listOf("pdf", "doc", "docx", "txt") -> {
                    ivIcon.setImageResource(R.drawable.ic_clean_reader)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.risk_safe))
                }
                else -> {
                    ivIcon.setImageResource(R.drawable.ic_file)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }

            // Click Handlers
            itemView.setOnClickListener { onItemClick(item, file) }
            btnShare.setOnClickListener { onShareClick(item, file) }
            btnDelete.setOnClickListener { onDeleteClick(item, file) }

            NovaMotion.attachSpringTouchFeedback(itemView, btnShare, btnDelete)
        }
    }

    companion object {
        fun resolveFile(context: Context, item: DownloadItem, filename: String): File? {
            // First check public Downloads
            val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
            if (publicFile.exists()) return publicFile

            // Check quarantine dir
            val quarantineFile = File(File(context.filesDir, "quarantine"), filename)
            if (quarantineFile.exists()) return quarantineFile

            // Check app download files dir
            val internalFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
            if (internalFile.exists()) return internalFile

            return if (publicFile.exists()) publicFile else quarantineFile
        }

        fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}
