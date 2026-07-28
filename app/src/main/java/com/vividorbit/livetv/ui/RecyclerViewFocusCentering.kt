package com.vividorbit.livetv.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Keeps this row centered in its parent RecyclerView whenever it gains
 * D-pad focus, so the list scrolls behind a fixed, centered highlight
 * rather than the highlight itself traveling up/down the panel.
 *
 * Works together with generous top/bottom padding on the RecyclerView
 * (see MainActivity.applyCenteringPadding) - without that padding, edge
 * rows (the first/last in the list) would have nowhere to scroll to in
 * order to reach the center.
 */
fun View.centerInParentOnFocus() {
    setOnFocusChangeListener { view, hasFocus ->
        if (!hasFocus) return@setOnFocusChangeListener
        val recyclerView = view.parent as? RecyclerView ?: return@setOnFocusChangeListener
        if (recyclerView.height <= 0) return@setOnFocusChangeListener

        val itemCenter = view.top + view.height / 2
        val recyclerCenter = recyclerView.height / 2
        val delta = itemCenter - recyclerCenter
        if (delta != 0) {
            recyclerView.smoothScrollBy(0, delta)
        }
    }
}
