package com.vorynlabs.vividorbit.ui

import androidx.recyclerview.widget.DiffUtil
import com.vorynlabs.vividorbit.data.Channel

fun channelDiff(oldList: List<Channel>, newList: List<Channel>): DiffUtil.Callback {
    return object : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old == new && old.isHidden == new.isHidden
        }
    }
}
