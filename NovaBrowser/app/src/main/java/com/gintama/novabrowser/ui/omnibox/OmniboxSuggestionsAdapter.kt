package com.gintama.novabrowser.ui.omnibox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gintama.novabrowser.R

enum class OmniboxState {
    IDLE,
    FOCUS,
    EDITING,
    SUBMITTING,
    LOADING,
    LOADED
}

enum class SuggestionType {
    SEARCH,
    HISTORY,
    BOOKMARK
}

data class OmniboxSuggestion(
    val type: SuggestionType,
    val title: String,
    val subtitle: String,
    val targetUrl: String,
    val queryToInsert: String
)

class OmniboxSuggestionsAdapter(
    private val onItemClick: (OmniboxSuggestion) -> Unit,
    private val onInsertClick: (OmniboxSuggestion) -> Unit
) : RecyclerView.Adapter<OmniboxSuggestionsAdapter.ViewHolder>() {

    private val items = mutableListOf<OmniboxSuggestion>()

    fun submitList(newItems: List<OmniboxSuggestion>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_omnibox_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onInsertClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivSuggestionIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSuggestionTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSuggestionSubtitle)
        private val ivInsert: ImageView = itemView.findViewById(R.id.ivSuggestionInsert)

        fun bind(
            item: OmniboxSuggestion,
            onItemClick: (OmniboxSuggestion) -> Unit,
            onInsertClick: (OmniboxSuggestion) -> Unit
        ) {
            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle
            when (item.type) {
                SuggestionType.SEARCH -> {
                    ivIcon.setImageResource(R.drawable.ic_search)
                    ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
                SuggestionType.HISTORY -> {
                    ivIcon.setImageResource(R.drawable.ic_history)
                    ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }
                SuggestionType.BOOKMARK -> {
                    ivIcon.setImageResource(R.drawable.ic_bookmark)
                    ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.accent_emerald))
                }
            }

            itemView.setOnClickListener { onItemClick(item) }
            ivInsert.setOnClickListener { onInsertClick(item) }
        }
    }
}
