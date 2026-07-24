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
        holder.bind(category, category == selectedCategory, onCategoryClick = { clickedCat ->
            val oldPos = categories.indexOf(selectedCategory)
            selectedCategory = clickedCat
            if (oldPos != -1) notifyItemChanged(oldPos)
            notifyItemChanged(position)
            onCategoryClick(clickedCat)
        })
    }

    override fun getItemCount(): Int = categories.size

    fun setSelectedCategory(category: String) {
        val oldPos = categories.indexOf(selectedCategory)
        selectedCategory = category
        val newPos = categories.indexOf(selectedCategory)
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.category_name)
        private val indicatorView: View = itemView.findViewById(R.id.category_indicator)

        fun bind(category: String, isSelected: Boolean, onCategoryClick: (String) -> Unit) {
            nameText.text = category
            itemView.isSelected = isSelected
            indicatorView.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            itemView.setOnClickListener {
                onCategoryClick(category)
            }
        }
    }
}
