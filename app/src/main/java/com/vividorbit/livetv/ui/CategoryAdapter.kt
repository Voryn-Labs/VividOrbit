package com.vividorbit.livetv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vividorbit.livetv.R

class CategoryAdapter(
    private val categories: List<String>,
    private var selectedCategory: String = "All Channels",
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category, category == selectedCategory, onCategoryClick)
    }

    override fun getItemCount(): Int = categories.size

    /**
     * Single source of truth for the selected category. Callers should call
     * this once their own filtering/selection logic completes, rather than
     * the adapter tracking selection itself.
     */
    fun setSelectedCategory(category: String) {
        val oldPos = categories.indexOf(selectedCategory)
        selectedCategory = category
        val newPos = categories.indexOf(selectedCategory)
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.category_name)

        fun bind(category: String, isSelected: Boolean, onCategoryClick: (String) -> Unit) {
            nameText.text = category
            // Selection is shown purely by the row's own background/outline
            // (see item_background_selector.xml) - no separate indicator dot.
            itemView.isSelected = isSelected

            itemView.setOnClickListener {
                onCategoryClick(category)
            }
        }
    }
}
