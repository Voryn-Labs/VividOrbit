package com.vividorbit.livetv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
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

    companion object {
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 8
        private val logoCache = object : LruCache<Long, Bitmap>(cacheSize) {
            override fun sizeOf(key: Long, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

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
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = channels.size
            override fun getNewListSize(): Int = newChannels.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return channels[oldItemPosition].id == newChannels[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return channels[oldItemPosition] == newChannels[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        channels = newChannels
        diffResult.dispatchUpdatesTo(this)
    }

    class ViewHolder(itemView: View, private val scope: CoroutineScope) : RecyclerView.ViewHolder(itemView) {
        private val numberText: TextView = itemView.findViewById(R.id.channel_number)
        private val logoImage: ImageView = itemView.findViewById(R.id.channel_logo)
        private val nameText: TextView = itemView.findViewById(R.id.channel_name)
        private var imageJob: Job? = null

        fun bind(channel: Channel, onClick: (Channel) -> Unit) {
            numberText.text = channel.displayNumber
            nameText.text = channel.displayName
            
            imageJob?.cancel()

            val cachedBitmap = logoCache.get(channel.id)
            if (cachedBitmap != null) {
                logoImage.setImageBitmap(cachedBitmap)
            } else {
                logoImage.setImageResource(android.R.drawable.ic_menu_slideshow)
                imageJob = scope.launch(Dispatchers.IO) {
                    try {
                        itemView.context.contentResolver.openInputStream(channel.logoUri)?.use { inputStream ->
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            if (bitmap != null) {
                                logoCache.put(channel.id, bitmap)
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
