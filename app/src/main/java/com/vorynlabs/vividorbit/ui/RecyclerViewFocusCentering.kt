package com.vorynlabs.vividorbit.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView

fun View.centerInParentOnFocus() {
    setOnFocusChangeListener { view, hasFocus ->
        if (!hasFocus) return@setOnFocusChangeListener
        val recyclerView = view.parent as? RecyclerView ?: return@setOnFocusChangeListener
        if (recyclerView.height <= 0) return@setOnFocusChangeListener

        val itemCenter = view.top + view.height / 2
        val recyclerCenter = recyclerView.height / 2
        val delta = itemCenter - recyclerCenter
        if (Math.abs(delta) > view.height / 2) {
            recyclerView.scrollBy(0, delta)
        }
    }
}
