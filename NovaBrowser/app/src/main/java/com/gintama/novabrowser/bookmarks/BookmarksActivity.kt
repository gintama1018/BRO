package com.gintama.novabrowser.bookmarks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.core.controller.BrowserController
import kotlinx.coroutines.launch

class BookmarksActivity : AppCompatActivity() {

    private lateinit var controller: BrowserController
    private lateinit var adapter: BookmarksAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var rvList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        controller = BrowserController(this)

        val btnBack = findViewById<ImageButton>(R.id.btnBookmarksBack)
        btnBack.setOnClickListener { finish() }

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
    }

    private fun loadBookmarks() {
        lifecycleScope.launch {
            val items = controller.getBookmarks()
            adapter.updateList(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
