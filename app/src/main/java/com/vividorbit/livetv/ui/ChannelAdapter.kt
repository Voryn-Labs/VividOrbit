package com.vividorbit.livetv.ui

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
    private var channels: List<Channel> = emptyList(),
    private val scope: CoroutineScope,
    private var currentChannelId: Long? = null,
    private val onChannelClick: (Channel) -> Unit,
    private val onChannelLongClick: ((Channel) -> Boolean)? = null
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    companion object {
        const val PAYLOAD_SELECTION = "payload_selection"
    }

    private var updateJob: Job? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view, scope)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val channel = channels[position]
            holder.setSelection(channel.id == currentChannelId)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, channel.id == currentChannelId, onChannelClick, onChannelLongClick)
    }

    override fun getItemCount(): Int = channels.size

    fun setCurrentChannel(channelId: Long?) {
        if (currentChannelId == channelId) return
        val oldId = currentChannelId
        currentChannelId = channelId
        channels.forEachIndexed { index, channel ->
            if (channel.id == oldId || channel.id == channelId) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    fun updateChannels(newChannels: List<Channel>) {
        val oldChannels = channels
        updateJob?.cancel()
        updateJob = scope.launch(Dispatchers.Default) {
            val diffResult = DiffUtil.calculateDiff(channelDiff(oldChannels, newChannels))
            withContext(Dispatchers.Main) {
                channels = newChannels
                diffResult.dispatchUpdatesTo(this@ChannelAdapter)
            }
        }
    }

    class ViewHolder(itemView: View, private val scope: CoroutineScope) : RecyclerView.ViewHolder(itemView) {
        private val numberText: TextView = itemView.findViewById(R.id.channel_number)
        private val logoImage: ImageView = itemView.findViewById(R.id.channel_logo)
        private val nameText: TextView = itemView.findViewById(R.id.channel_name)
        private var imageJob: Job? = null

        init {
            itemView.centerInParentOnFocus()
        }

        fun setSelection(isSelected: Boolean) {
            itemView.isSelected = isSelected
        }

        fun bind(
            channel: Channel,
            isCurrentlyPlaying: Boolean,
            onClick: (Channel) -> Unit,
            onLongClick: ((Channel) -> Boolean)?
        ) {
            numberText.text = channel.displayNumber
            nameText.text = channel.displayName
            itemView.isSelected = isCurrentlyPlaying

            imageJob?.cancel()

            val cachedBitmap = ChannelLogoLoader.getCached(channel.id)
            if (cachedBitmap != null) {
                logoImage.setImageBitmap(cachedBitmap)
            } else {
                logoImage.setImageResource(android.R.drawable.ic_menu_slideshow)
                imageJob = scope.launch(Dispatchers.IO) {
                    val bitmap = ChannelLogoLoader.loadAndCache(itemView.context, channel.id, channel.logoUri)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            logoImage.setImageBitmap(bitmap)
                        }
                    }
                }
            }

            itemView.setOnClickListener {
                onClick(channel)
            }

            if (onLongClick != null) {
                itemView.setOnLongClickListener {
                    onLongClick(channel)
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }
}
