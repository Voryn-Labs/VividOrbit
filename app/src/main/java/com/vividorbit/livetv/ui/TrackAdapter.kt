package com.vividorbit.livetv.ui

import android.media.tv.TvTrackInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vividorbit.livetv.R
import java.util.Locale

class TrackAdapter(
    private val tracks: List<TvTrackInfo>,
    private val selectedTrackId: String?,
    private val onTrackClick: (TvTrackInfo) -> Unit
) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        val isSelected = track.id == selectedTrackId
        holder.bind(track, isSelected, onTrackClick)
    }

    override fun getItemCount(): Int = tracks.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.category_name)

        fun bind(track: TvTrackInfo, isSelected: Boolean, onClick: (TvTrackInfo) -> Unit) {
            val locale = Locale(track.language ?: "")
            var displayName = locale.displayLanguage.ifEmpty { track.language ?: "Unknown" }
            if (isSelected) {
                displayName += " (Current)"
            }
            nameText.text = displayName

            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(160)
                        .start()
                } else {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(160)
                        .start()
                }
            }

            itemView.setOnClickListener {
                onClick(track)
            }
        }
    }
}
