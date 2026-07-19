package com.vividorbit.livetv.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vividorbit.livetv.R
import com.vividorbit.livetv.data.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelAdapter(
    private var channels: List<Channel>,
    private val scope: CoroutineScope,
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view, scope)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, onChannelClick)
    }

    override fun getItemCount(): Int = channels.size

    fun updateChannels(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View, private val scope: CoroutineScope) : RecyclerView.ViewHolder(itemView) {
        private val numberText: TextView = itemView.findViewById(R.id.channel_number)
        private val logoImage: ImageView = itemView.findViewById(R.id.channel_logo)
        private val nameText: TextView = itemView.findViewById(R.id.channel_name)
        private var imageJob: Job? = null

        init {
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
        }

        fun bind(channel: Channel, onClick: (Channel) -> Unit) {
            numberText.text = channel.displayNumber
            nameText.text = channel.displayName
            
            imageJob?.cancel()
            logoImage.setImageResource(android.R.drawable.ic_menu_slideshow)
            
            if (channel.logoUri != null) {
                imageJob = scope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = itemView.context.contentResolver.openInputStream(channel.logoUri)
                        if (inputStream != null) {
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream.close()
                            if (bitmap != null) {
                                withContext(Dispatchers.Main) {
                                    logoImage.setImageBitmap(bitmap)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore, fallback is already set
                    }
                }
            }

            itemView.setOnClickListener {
                onClick(channel)
            }
        }
    }
}
