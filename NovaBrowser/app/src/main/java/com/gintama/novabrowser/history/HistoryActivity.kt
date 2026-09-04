package com.gintama.novabrowser.history

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.core.controller.BrowserController
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var controller: BrowserController
    private lateinit var adapter: HistoryAdapter
    private lateinit var etSearch: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var rvList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        controller = BrowserController(this)

        val btnBack = findViewById<ImageButton>(R.id.btnHistoryBack)
        btnBack.setOnClickListener { finish() }

        etSearch = findViewById(R.id.etSearchHistory)
        tvEmpty = findViewById(R.id.tvEmptyHistory)
        rvList = findViewById(R.id.rvHistoryList)
        val btnClear = findViewById<ImageButton>(R.id.btnClearHistory)
        val btnClearSearch = findViewById<ImageButton>(R.id.btnClearSearch)

        adapter = HistoryAdapter(emptyList()) { item ->
            val resultIntent = Intent().apply {
                putExtra("selected_url", item.url)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter

        etSearch.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            searchHistory(query)
            btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        }

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all browsing history?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        controller.clearHistory()
                        loadRecentHistory()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<TextView>(R.id.chipFilterAll)?.setOnClickListener { searchHistory("") }
        findViewById<TextView>(R.id.chipFilterToday)?.setOnClickListener { searchHistory("today") }
        findViewById<TextView>(R.id.chipFilterYesterday)?.setOnClickListener { searchHistory("yesterday") }
        findViewById<TextView>(R.id.chipFilterSecurity)?.setOnClickListener { searchHistory("security") }
        findViewById<TextView>(R.id.chipFilterBookmarks)?.setOnClickListener { searchHistory("bookmark") }

        loadRecentHistory()
    }

    private fun loadRecentHistory() {
        lifecycleScope.launch {
            val items = controller.getRecentHistory()
            adapter.updateList(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun searchHistory(query: String) {
        lifecycleScope.launch {
            val items = if (query.isBlank()) {
                controller.getRecentHistory()
            } else {
                controller.searchHistory(query)
            }
            adapter.updateList(items)
            tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
