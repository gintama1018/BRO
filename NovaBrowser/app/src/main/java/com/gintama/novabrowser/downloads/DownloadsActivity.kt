package com.gintama.novabrowser.downloads

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.core.db.NovaDatabaseHelper
import com.gintama.novabrowser.core.model.DownloadItem
import com.gintama.novabrowser.core.model.DownloadStatus
import com.gintama.novabrowser.ui.motion.NovaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadsActivity : AppCompatActivity() {

    private lateinit var db: NovaDatabaseHelper
    private lateinit var adapter: DownloadsAdapter
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var rvList: RecyclerView
    private lateinit var chipAll: TextView
    private lateinit var chipSafe: TextView
    private lateinit var chipQuarantine: TextView

    private var allDownloads = listOf<DownloadItem>()
    private var currentFilterMode = FilterMode.ALL
    private var currentQuery = ""

    enum class FilterMode { ALL, SAFE, QUARANTINE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        db = NovaDatabaseHelper.getInstance(this)

        val btnBack = findViewById<ImageButton>(R.id.btnDownloadsBack)
        btnBack.setOnClickListener { finish() }

        val btnClearAll = findViewById<ImageButton>(R.id.btnClearDownloads)
        btnClearAll.setOnClickListener { showClearAllDialog() }

        etSearch = findViewById(R.id.etSearchDownloads)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        layoutEmpty = findViewById(R.id.layoutEmptyDownloads)
        rvList = findViewById(R.id.rvDownloadsList)

        chipAll = findViewById(R.id.chipFilterAll)
        chipSafe = findViewById(R.id.chipFilterSafe)
        chipQuarantine = findViewById(R.id.chipFilterQuarantine)

        adapter = DownloadsAdapter(
            items = emptyList(),
            onItemClick = { item, file -> handleItemClick(item, file) },
            onShareClick = { item, file -> handleShareClick(item, file) },
            onDeleteClick = { item, file -> handleDeleteClick(item, file) }
        )

        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        setupFilters()
        setupSearch()
        loadDownloads()

        NovaMotion.attachSpringTouchFeedback(btnBack, btnClearAll, chipAll, chipSafe, chipQuarantine)
    }

    private fun setupFilters() {
        chipAll.setOnClickListener {
            currentFilterMode = FilterMode.ALL
            updateFilterChips()
            applyFilterAndSearch()
        }

        chipSafe.setOnClickListener {
            currentFilterMode = FilterMode.SAFE
            updateFilterChips()
            applyFilterAndSearch()
        }

        chipQuarantine.setOnClickListener {
            currentFilterMode = FilterMode.QUARANTINE
            updateFilterChips()
            applyFilterAndSearch()
        }
    }

    private fun updateFilterChips() {
        val activeBg = R.drawable.bg_glass_pill_dark
        val inactiveBg = R.drawable.bg_glass_pill
        val activeTextColor = ContextCompat.getColor(this, R.color.risk_safe)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.text_secondary)

        chipAll.setBackgroundResource(if (currentFilterMode == FilterMode.ALL) activeBg else inactiveBg)
        chipAll.setTextColor(if (currentFilterMode == FilterMode.ALL) activeTextColor else inactiveTextColor)

        chipSafe.setBackgroundResource(if (currentFilterMode == FilterMode.SAFE) activeBg else inactiveBg)
        chipSafe.setTextColor(if (currentFilterMode == FilterMode.SAFE) activeTextColor else inactiveTextColor)

        chipQuarantine.setBackgroundResource(if (currentFilterMode == FilterMode.QUARANTINE) activeBg else inactiveBg)
        chipQuarantine.setTextColor(if (currentFilterMode == FilterMode.QUARANTINE) activeTextColor else inactiveTextColor)
    }

    private fun setupSearch() {
        etSearch.doAfterTextChanged { text ->
            currentQuery = text?.toString()?.trim().orEmpty()
            btnClearSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
            applyFilterAndSearch()
        }

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }
    }

    private fun loadDownloads() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                db.getDownloads()
            }
            allDownloads = list
            applyFilterAndSearch()
        }
    }

    private fun applyFilterAndSearch() {
        val filtered = allDownloads.filter { item ->
            val matchesFilter = when (currentFilterMode) {
                FilterMode.ALL -> true
                FilterMode.SAFE -> item.status == DownloadStatus.SAFE || item.status == DownloadStatus.COMPLETED
                FilterMode.QUARANTINE -> item.status == DownloadStatus.QUARANTINED || item.status == DownloadStatus.BLOCKED
            }
            val matchesSearch = if (currentQuery.isBlank()) {
                true
            } else {
                (item.filename?.contains(currentQuery, ignoreCase = true) == true) ||
                item.url.contains(currentQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }

        adapter.updateItems(filtered)
        if (filtered.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvList.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvList.visibility = View.VISIBLE
        }
    }

    private fun handleItemClick(item: DownloadItem, file: File?) {
        if (item.status == DownloadStatus.QUARANTINED) {
            showQuarantineActionDialog(item, file)
            return
        }

        openDownloadedFile(item, file)
    }

    private fun openDownloadedFile(item: DownloadItem, file: File?) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File not found on storage: ${item.filename}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: item.mimeType
                ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No application found to open this file type", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showQuarantineActionDialog(item: DownloadItem, file: File?) {
        val filename = item.filename ?: "unknown_file"
        AlertDialog.Builder(this)
            .setTitle("Quarantined File")
            .setMessage("File: $filename\n\nReason: ${item.riskReason ?: "Executable / dangerous extension detected"}.\n\nThis file is held safely in isolation.")
            .setPositiveButton("Release to Public Downloads") { _, _ ->
                releaseFileFromQuarantine(item, file)
            }
            .setNeutralButton("Open Anyway") { _, _ ->
                openDownloadedFile(item, file)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun releaseFileFromQuarantine(item: DownloadItem, file: File?) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "File no longer exists on disk", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(publicDir, file.name)
                    file.copyTo(targetFile, overwrite = true)
                    file.delete()
                    db.updateDownloadStatus(item.id, DownloadStatus.COMPLETED, "Released by user to Downloads")
                } catch (e: Exception) {
                    // Ignore or log
                }
            }
            Toast.makeText(this@DownloadsActivity, "Released to public Downloads folder: ${file.name}", Toast.LENGTH_LONG).show()
            loadDownloads()
        }
    }

    private fun handleShareClick(item: DownloadItem, file: File?) {
        if (file != null && file.exists()) {
            try {
                val contentUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val extension = file.extension.lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: item.mimeType
                    ?: "*/*"

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
            } catch (e: Exception) {
                shareUrlOnly(item)
            }
        } else {
            shareUrlOnly(item)
        }
    }

    private fun shareUrlOnly(item: DownloadItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, item.url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share download link"))
    }

    private fun handleDeleteClick(item: DownloadItem, file: File?) {
        AlertDialog.Builder(this)
            .setTitle("Delete Download")
            .setMessage("Delete \"${item.filename ?: "this file"}\" from download records?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.deleteDownload(item.id)
                        if (file != null && file.exists()) {
                            file.delete()
                        }
                    }
                    loadDownloads()
                    Toast.makeText(this@DownloadsActivity, "Download deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Downloads")
            .setMessage("Clear all download records from history?")
            .setPositiveButton("Clear All") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.clearDownloads()
                    }
                    loadDownloads()
                    Toast.makeText(this@DownloadsActivity, "Downloads cleared", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
