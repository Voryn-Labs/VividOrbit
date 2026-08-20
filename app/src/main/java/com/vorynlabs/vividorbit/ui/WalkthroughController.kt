package com.vorynlabs.vividorbit.ui

import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.vorynlabs.vividorbit.R

class WalkthroughController(
    private val overlay: View,
    private val onShowInfo: () -> Unit,
    private val onShowGuide: () -> Unit,
    private val onShowLineup: () -> Unit,
    private val onShowPhone: () -> Unit,
    private val onLeavePanels: () -> Unit,
    private val onFinish: () -> Unit
) {
    private val nextBtn: TextView = overlay.findViewById(R.id.walkthrough_next_btn)

    init {
        nextBtn.setOnClickListener { finish() }
    }

    fun show() {
        overlay.visibility = View.VISIBLE
        onLeavePanels()
        nextBtn.requestFocus()
    }

    fun hide() {
        overlay.visibility = View.GONE
    }

    fun isVisible(): Boolean = overlay.visibility == View.VISIBLE

    fun handleKey(keyCode: Int): Boolean {
        if (!isVisible()) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                finish()
                true
            }
            else -> false
        }
    }

    private fun finish() {
        onLeavePanels()
        onFinish()
    }
}
