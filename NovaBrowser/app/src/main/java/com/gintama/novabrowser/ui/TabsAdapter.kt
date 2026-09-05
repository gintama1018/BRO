package com.gintama.novabrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.browser.BrowserTab

class TabsAdapter(
    private var tabs: List<BrowserTab>,
    private var activeTabId: String?,
    private val onTabClick: (BrowserTab) -> Unit,
    private val onTabClose: (BrowserTab) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {

    fun updateTabs(newTabs: List<BrowserTab>, activeId: String?) {
        this.tabs = newTabs
        this.activeTabId = activeId
        notifyDataSetChanged()
    }

    fun getTabAt(position: Int): BrowserTab = tabs[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab, tab.id == activeTabId)
    }

    override fun getItemCount(): Int = tabs.size

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardTab: CardView = itemView.findViewById(R.id.cardTab)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        private val tvUrl: TextView = itemView.findViewById(R.id.tvTabUrl)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivTabIcon)
        private val btnClose: ImageButton = itemView.findViewById(R.id.btnCloseTab)

        fun bind(tab: BrowserTab, isActive: Boolean) {
            tvTitle.text = tab.title.ifBlank { "New Tab" }
            tvUrl.text = tab.url.ifBlank { "about:blank" }

            if (tab.isPrivate) {
                ivIcon.setImageResource(R.drawable.ic_incognito)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.incognito_accent))
            } else {
                ivIcon.setImageResource(R.drawable.ic_tabs)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.accent))
            }

            if (isActive) {
                cardTab.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.background))
                cardTab.cardElevation = 10f
            } else {
                cardTab.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.surface))
                cardTab.cardElevation = 2f
            }

            itemView.setOnClickListener { onTabClick(tab) }
            btnClose.setOnClickListener { onTabClose(tab) }
        }
    }
}
