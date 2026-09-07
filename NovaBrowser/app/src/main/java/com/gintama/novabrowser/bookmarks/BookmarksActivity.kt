package com.gintama.novabrowser.bookmarks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.core.controller.BrowserController
import com.gintama.novabrowser.ui.motion.NovaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksActivity : AppCompatActivity() {

    private lateinit var controller: BrowserController
    private lateinit var adapter: BookmarksAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var rvList: RecyclerView

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val html = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val items = BookmarkHtmlManager.importBookmarksFromHtml(html)
                    var count = 0
                    for ((url, title) in items) {
                        val id = controller.addBookmark(url, title)
                        if (id > 0) count++
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BookmarksActivity, "Imported $count bookmarks", Toast.LENGTH_SHORT).show()
                        loadBookmarks()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BookmarksActivity, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        controller = BrowserController(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBookmarksBack)
        btnBack.setOnClickListener { finish() }

        val btnImport = findViewById<ImageButton>(R.id.btnImportBookmarks)
        btnImport.setOnClickListener {
            importLauncher.launch("*/*")
        }

        val btnExport = findViewById<ImageButton>(R.id.btnExportBookmarks)
        btnExport.setOnClickListener {
            lifecycleScope.launch {
                val items = controller.getBookmarks()
                if (items.isEmpty()) {
                    Toast.makeText(this@BookmarksActivity, "No bookmarks to export", Toast.LENGTH_SHORT).show()
                } else {
                    BookmarkHtmlManager.shareExportedBookmarks(this@BookmarksActivity, items)
                }
            }
        }

        tvEmpty = findViewById(R.id.tvEmptyBookmarks)
        rvList = findViewById(R.id.rvBookmarksList)

        adapter = BookmarksAdapter(
            items = emptyList(),
            onItemClick = { item ->
                val resultIntent = Intent().apply {
                    putExtra("selected_url", item.url)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            },
            onDeleteClick = { item ->
                lifecycleScope.launch {
                    controller.deleteBookmark(item.id)
                    loadBookmarks()
                }
            }
        )

        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        loadBookmarks()

        NovaMotion.attachSpringTouchFeedback(btnBack, btnImport, btnExport)
    }

    private fun loadBookmarks() {
        lifecycleScope.launch {
            val items = controller.getBookmarks()
            adapter.updateList(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
