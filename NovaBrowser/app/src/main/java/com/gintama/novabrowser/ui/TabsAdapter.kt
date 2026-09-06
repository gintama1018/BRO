package com.gintama.novabrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R
import com.gintama.novabrowser.browser.BrowserTab
import java.net.URI

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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_grid, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab, tab.id == activeTabId)
    }

    override fun getItemCount(): Int = tabs.size

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardTab: View = itemView.findViewById(R.id.cardTab)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivTabIcon)
        private val btnClose: ImageButton = itemView.findViewById(R.id.btnCloseTab)
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivTabThumbnail)
        private val layoutFallback: View = itemView.findViewById(R.id.layoutThumbnailFallback)
        private val tvMonogram: TextView = itemView.findViewById(R.id.tvDomainMonogram)
        private val tvDomainBadge: TextView = itemView.findViewById(R.id.tvDomainBadge)
        private val tvSecurityBadge: TextView = itemView.findViewById(R.id.tvSecurityBadge)
        private val layoutActiveIndicator: View = itemView.findViewById(R.id.layoutActiveIndicator)
        private val viewActiveDot: View = itemView.findViewById(R.id.viewActiveDot)
        private val tvActiveText: TextView = itemView.findViewById(R.id.tvActiveText)

        fun bind(tab: BrowserTab, isActive: Boolean) {
            val titleText = if (tab.title.isNotBlank()) tab.title else "New Tab"
            tvTitle.text = titleText

            val domain = extractHost(tab.url)
            tvDomainBadge.text = domain
            tvMonogram.text = deriveMonogram(domain)

            // Live thumbnail or high-fidelity fallback card
            if (tab.thumbnail != null && !tab.thumbnail!!.isRecycled) {
                ivThumbnail.setImageBitmap(tab.thumbnail)
                ivThumbnail.visibility = View.VISIBLE
                layoutFallback.visibility = View.GONE
            } else {
                ivThumbnail.setImageBitmap(null)
                ivThumbnail.visibility = View.GONE
                layoutFallback.visibility = View.VISIBLE
            }

            // Private vs Standard Theme Styling
            if (tab.isPrivate) {
                ivIcon.setImageResource(R.drawable.ic_incognito)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.incognito_accent))
                tvMonogram.setTextColor(ContextCompat.getColor(itemView.context, R.color.incognito_accent))
                tvSecurityBadge.text = "INCOGNITO • NO TRACE"
                tvSecurityBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.incognito_accent))
                viewActiveDot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.incognito_accent)
                tvActiveText.text = "ACTIVE PRIVATE TAB"
                tvActiveText.setTextColor(ContextCompat.getColor(itemView.context, R.color.incognito_accent))

                if (isActive) {
                    cardTab.setBackgroundResource(R.drawable.bg_glass_card_incognito_active)
                    cardTab.elevation = 6f
                } else {
                    cardTab.setBackgroundResource(R.drawable.bg_glass_card_incognito)
                    cardTab.elevation = 1f
                }
            } else {
                ivIcon.setImageResource(R.drawable.ic_tabs)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.accent_emerald))
                tvMonogram.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_emerald))
                tvSecurityBadge.text = "SECURE • SANDBOXED"
                tvSecurityBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.risk_safe))
                viewActiveDot.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.accent_emerald)
                tvActiveText.text = "ACTIVE TAB"
                tvActiveText.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_emerald))

                if (isActive) {
                    cardTab.setBackgroundResource(R.drawable.bg_glass_card_active)
                    cardTab.elevation = 6f
                } else {
                    cardTab.setBackgroundResource(R.drawable.bg_glass_card)
                    cardTab.elevation = 1f
                }
            }

            layoutActiveIndicator.visibility = if (isActive) View.VISIBLE else View.INVISIBLE

            itemView.setOnClickListener { onTabClick(tab) }
            btnClose.setOnClickListener { onTabClose(tab) }
        }

        private fun extractHost(url: String): String {
            if (url.isBlank() || url == "about:blank") return "Start Canvas"
            return try {
                val uri = URI(url)
                val host = uri.host
                if (!host.isNullOrBlank()) {
                    if (host.startsWith("www.", ignoreCase = true)) host.substring(4) else host
                } else {
                    url
                }
            } catch (e: Exception) {
                url
            }
        }

        private fun deriveMonogram(domain: String): String {
            if (domain == "Start Canvas" || domain.isBlank()) return "✦"
            val parts = domain.split(".")
            return if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                val first = parts[0]
                if (first.length >= 2) first.substring(0, 2).uppercase() else first.uppercase()
            } else {
                "✦"
            }
        }
    }
}
