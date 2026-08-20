package com.vorynlabs.vividorbit.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.vorynlabs.vividorbit.R
import com.vorynlabs.vividorbit.data.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelSettingsAdapter(
    private var channels: List<Channel> = emptyList(),
    private val scope: CoroutineScope,
    private val isHiddenChecker: ((Long) -> Boolean)? = null,
    private val onChannelClick: (Channel) -> Unit,
    private val onChannelLongClick: ((Channel) -> Boolean)? = null
) : RecyclerView.Adapter<ChannelSettingsAdapter.ViewHolder>() {

    private var updateJob: Job? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_settings, parent, false)
        return ViewHolder(view, scope)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.bind(channel, onChannelClick, onChannelLongClick)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    override fun getItemCount(): Int = channels.size

    fun updateChannels(newChannels: List<Channel>) {
        val oldChannels = channels
        updateJob?.cancel()
        updateJob = scope.launch(Dispatchers.Default) {
            val diffResult = DiffUtil.calculateDiff(channelDiff(oldChannels, newChannels))
            withContext(Dispatchers.Main) {
                channels = newChannels
                diffResult.dispatchUpdatesTo(this@ChannelSettingsAdapter)
            }
        }
    }

    fun notifyHiddenChanged(channelId: Long) {
        val index = channels.indexOfFirst { it.id == channelId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    inner class ViewHolder(itemView: View, private val scope: CoroutineScope) : RecyclerView.ViewHolder(itemView) {
        private val customNumberText: TextView = itemView.findViewById(R.id.settings_custom_number)
        private val logoImage: ImageView = itemView.findViewById(R.id.settings_channel_logo)
        private val nameText: TextView = itemView.findViewById(R.id.settings_channel_name)
        private val dthNumberText: TextView = itemView.findViewById(R.id.settings_dth_number)
        private var imageJob: Job? = null

        init {
            itemView.centerInParentOnFocus()
        }

        fun recycle() {
            imageJob?.cancel()
            imageJob = null
        }

        fun bind(
            channel: Channel,
            onClick: (Channel) -> Unit,
            onLongClick: ((Channel) -> Boolean)?
        ) {
            customNumberText.text = channel.displayNumber
            nameText.text = channel.displayName
            val hidden = isHiddenChecker?.invoke(channel.id) == true
            dthNumberText.text = if (hidden) {
                itemView.context.getString(R.string.settings_hidden_hint)
            } else {
                itemView.context.getString(R.string.dth_format, channel.originalDisplayNumber)
            }
            dthNumberText.setTypeface(null, if (hidden) Typeface.BOLD else Typeface.NORMAL)
            dthNumberText.setTextColor(
                itemView.context.getColor(if (hidden) R.color.accent_dim else R.color.text_secondary)
            )

            imageJob?.cancel()
            logoImage.visibility = View.GONE

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
