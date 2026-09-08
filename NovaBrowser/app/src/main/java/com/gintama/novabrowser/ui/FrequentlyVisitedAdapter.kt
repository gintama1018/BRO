package com.gintama.novabrowser.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R

data class FrequentlyVisitedItem(
    val title: String,
    val domain: String,
    val url: String,
    val relativeTime: String,
    val monogram: String
)

class FrequentlyVisitedAdapter(
    private val items: List<FrequentlyVisitedItem>,
    private val onItemClick: (FrequentlyVisitedItem) -> Unit
) : RecyclerView.Adapter<FrequentlyVisitedAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFrequentMonogram: TextView = view.findViewById(R.id.tvFrequentMonogram)
        val tvFrequentTitle: TextView = view.findViewById(R.id.tvFrequentTitle)
        val tvFrequentDomain: TextView = view.findViewById(R.id.tvFrequentDomain)
        val tvFrequentTime: TextView = view.findViewById(R.id.tvFrequentTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_frequently_visited_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvFrequentMonogram.text = item.monogram
        holder.tvFrequentTitle.text = item.title
        holder.tvFrequentDomain.text = item.domain
        holder.tvFrequentTime.text = item.relativeTime
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
