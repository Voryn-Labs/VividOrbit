package com.vorynlabs.vividorbit.ui

import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.vorynlabs.vividorbit.R

class WalkthroughController(
    private val overlay: View,
    private val onFinish: () -> Unit
) {
    private val kickerView: TextView = overlay.findViewById(R.id.walkthrough_kicker)
    private val titleView: TextView = overlay.findViewById(R.id.walkthrough_title)
    private val bodyView: TextView = overlay.findViewById(R.id.walkthrough_body)
    private val skipBtn: TextView = overlay.findViewById(R.id.walkthrough_skip_btn)
    private val backBtn: TextView = overlay.findViewById(R.id.walkthrough_back_btn)
    private val nextBtn: TextView = overlay.findViewById(R.id.walkthrough_next_btn)
    private val dotsContainer: LinearLayout = overlay.findViewById(R.id.walkthrough_dots)
    private val phonePanel: View = overlay.findViewById(R.id.walkthrough_phone)

    private var currentPage = 0
    private val kickers = overlay.resources.getStringArray(R.array.walkthrough_kickers)
    private val titles = overlay.resources.getStringArray(R.array.walkthrough_titles)
    private val bodies = overlay.resources.getStringArray(R.array.walkthrough_bodies)

    init {
        skipBtn.setOnClickListener { onFinish() }
        backBtn.setOnClickListener { goBack() }
        nextBtn.setOnClickListener { goNext() }
    }

    fun show() {
        currentPage = 0
        overlay.visibility = View.VISIBLE
        bindPage()
    }

    fun hide() {
        overlay.visibility = View.GONE
    }

    fun isVisible(): Boolean = overlay.visibility == View.VISIBLE

    fun handleKey(keyCode: Int): Boolean {
        if (!isVisible()) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                goBack()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                goPrevPage()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                goNext()
                true
            }
            else -> false
        }
    }

    private fun goBack() {
        if (currentPage == 0) onFinish() else {
            currentPage = prevWalkthroughPage(currentPage)
            bindPage()
        }
    }

    private fun goPrevPage() {
        if (currentPage > 0) {
            currentPage = prevWalkthroughPage(currentPage)
            bindPage()
        }
    }

    private fun goNext() {
        if (isLastWalkthroughPage(currentPage)) onFinish() else {
            currentPage = nextWalkthroughPage(currentPage)
            bindPage()
        }
    }

    private fun bindPage() {
        kickerView.text = kickers[currentPage]
        titleView.text = titles[currentPage]
        bodyView.text = bodies[currentPage]
        phonePanel.visibility = if (isPhoneWalkthroughPage(currentPage)) View.VISIBLE else View.GONE
        skipBtn.visibility = if (isLastWalkthroughPage(currentPage)) View.INVISIBLE else View.VISIBLE
        backBtn.visibility = if (currentPage == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.setText(
            if (isLastWalkthroughPage(currentPage)) R.string.walkthrough_get_started
            else R.string.walkthrough_next
        )
        bindDots()
        nextBtn.requestFocus()
    }

    private fun bindDots() {
        dotsContainer.removeAllViews()
        for (i in 0 until walkthroughPageCount()) {
            val dot = TextView(overlay.context)
            dot.text = "\u25CF"
            dot.textSize = 9f
            dot.setTextColor(
                overlay.context.getColor(
                    if (i == currentPage) R.color.accent else R.color.text_tertiary
                )
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(5, 0, 5, 0)
            dot.layoutParams = lp
            dotsContainer.addView(dot)
        }
    }
}
