package com.vorynlabs.vividorbit.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.vorynlabs.vividorbit.R
import com.vorynlabs.vividorbit.data.Channel
import com.vorynlabs.vividorbit.data.EpgRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelAdapter(
    private var channels: List<Channel> = emptyList(),
    private val scope: CoroutineScope,
    private var currentChannelId: Long? = null,
    private val epgRepository: EpgRepository? = null,
    private var isFavoriteChecker: ((Long) -> Boolean)? = null,
    private val onChannelClick: (Channel) -> Unit,
    private val onChannelLongClick: ((Channel) -> Boolean)? = null,
    private val showProgramTitles: () -> Boolean = { false }
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    fun channelAt(position: Int): Channel? = channels.getOrNull(position)

    companion object {
        const val PAYLOAD_SELECTION = "payload_selection"
        const val PAYLOAD_FAVORITE = "payload_favorite"
    }

    private var updateJob: Job? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view, scope, epgRepository)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val channel = channels[position]
            holder.setSelection(channel.id == currentChannelId)
            return
        }
        if (payloads.contains(PAYLOAD_FAVORITE)) {
            val channel = channels[position]
            val isFav = isFavoriteChecker?.invoke(channel.id) == true
            holder.setFavorite(isFav)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        val isFav = isFavoriteChecker?.invoke(channel.id) == true
        holder.bind(channel, channel.id == currentChannelId, isFav, onChannelClick, onChannelLongClick)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
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

    fun notifyFavoriteChanged(channelId: Long) {
        val index = channels.indexOfFirst { it.id == channelId }
        if (index != -1) {
            notifyItemChanged(index, PAYLOAD_FAVORITE)
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

    inner class ViewHolder(
        itemView: View,
        private val scope: CoroutineScope,
        private val epgRepository: EpgRepository?
    ) : RecyclerView.ViewHolder(itemView) {
        private val numberText: TextView = itemView.findViewById(R.id.channel_number)
        private val logoImage: ImageView = itemView.findViewById(R.id.channel_logo)
        private val nameText: TextView = itemView.findViewById(R.id.channel_name)
        private val programText: TextView = itemView.findViewById(R.id.channel_program)
        private val favoriteStar: TextView = itemView.findViewById(R.id.item_favorite_star)
        private var imageJob: Job? = null
        private var epgJob: Job? = null

        init {
            itemView.centerInParentOnFocus()
        }

        fun recycle() {
            imageJob?.cancel()
            imageJob = null
            epgJob?.cancel()
            epgJob = null
        }

        fun setSelection(isSelected: Boolean) {
            itemView.isSelected = isSelected
        }

        fun setFavorite(isFavorite: Boolean) {
            favoriteStar.visibility = if (isFavorite) View.VISIBLE else View.GONE
        }

        fun bind(
            channel: Channel,
            isCurrentlyPlaying: Boolean,
            isFavorite: Boolean,
            onClick: (Channel) -> Unit,
            onLongClick: ((Channel) -> Boolean)?
        ) {
            numberText.text = channel.displayNumber
            nameText.text = channel.displayName
            itemView.isSelected = isCurrentlyPlaying
            favoriteStar.visibility = if (isFavorite) View.VISIBLE else View.GONE

            itemView.contentDescription = itemView.context.getString(
                R.string.accessibility_channel_format,
                channel.displayNumber,
                channel.displayName
            )

            imageJob?.cancel()
            imageJob = ChannelLogoLoader.bind(logoImage, scope, channel.id, channel.logoUri)

            epgJob?.cancel()
            programText.visibility = View.GONE
            if (epgRepository != null && this@ChannelAdapter.showProgramTitles()) {
                epgJob = scope.launch(Dispatchers.IO) {
                    val (nowProgram, _) = epgRepository.getNowAndNext(channel.id)
                    withContext(Dispatchers.Main) {
                        if (nowProgram != null && nowProgram.title.isNotBlank()) {
                            programText.text = nowProgram.title
                            programText.visibility = View.VISIBLE
                            itemView.contentDescription = itemView.context.getString(
                                R.string.accessibility_channel_with_epg_format,
                                channel.displayNumber,
                                channel.displayName,
                                nowProgram.title
                            )
                        } else {
                            programText.visibility = View.GONE
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
