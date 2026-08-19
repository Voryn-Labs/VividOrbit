package com.vorynlabs.vividorbit.ui

import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.vorynlabs.vividorbit.R
import com.vorynlabs.vividorbit.data.KeyMappingRepository

class WalkthroughController(
    private val overlay: View,
    private val onFinish: () -> Unit
) {
    private val titleView: TextView = overlay.findViewById(R.id.walkthrough_title)
    private val bodyView: TextView = overlay.findViewById(R.id.walkthrough_body)
    private val skipBtn: TextView = overlay.findViewById(R.id.walkthrough_skip_btn)
    private val backBtn: TextView = overlay.findViewById(R.id.walkthrough_back_btn)
    private val nextBtn: TextView = overlay.findViewById(R.id.walkthrough_next_btn)
    private val dotsContainer: LinearLayout = overlay.findViewById(R.id.walkthrough_dots)
    private val chips: View = overlay.findViewById(R.id.walkthrough_chips)
    private val playground: View = overlay.findViewById(R.id.walkthrough_playground)
    private val phonePanel: View = overlay.findViewById(R.id.walkthrough_phone)
    private val statusView: TextView = overlay.findViewById(R.id.walkthrough_status)
    private val chipZap: TextView = overlay.findViewById(R.id.walkthrough_chip_zap)
    private val chipInfo: TextView = overlay.findViewById(R.id.walkthrough_chip_info)
    private val chipGuide: TextView = overlay.findViewById(R.id.walkthrough_chip_guide)
    private val chipLast: TextView = overlay.findViewById(R.id.walkthrough_chip_last)
    private val demoRows: List<View> = listOf(
        overlay.findViewById(R.id.walkthrough_demo_row_0),
        overlay.findViewById(R.id.walkthrough_demo_row_1),
        overlay.findViewById(R.id.walkthrough_demo_row_2)
    )

    private var currentPage = 0
    private var channels = seedDemoChannels()
    private var demoAssignNumberIndex = 0
    private val demoNumbersToAssign = listOf("1", "5", "10", "100")
    private val litChips = mutableSetOf<Int>()
    private val titles: Array<String> = overlay.resources.getStringArray(R.array.walkthrough_titles)
    private val bodies: Array<String> = overlay.resources.getStringArray(R.array.walkthrough_bodies)
    private val keyMappingRepository = KeyMappingRepository(overlay.context)

    init {
        skipBtn.setOnClickListener { onFinish() }
        backBtn.setOnClickListener { goBack() }
        nextBtn.setOnClickListener { goNext() }
        demoRows.forEachIndexed { index, row ->
            row.setOnClickListener { onDemoActivated(index) }
        }
    }

    fun show() {
        currentPage = 0
        channels = seedDemoChannels()
        demoAssignNumberIndex = 0
        litChips.clear()
        overlay.visibility = View.VISIBLE
        bindPage()
    }

    fun hide() {
        overlay.visibility = View.GONE
    }

    fun isVisible(): Boolean = overlay.visibility == View.VISIBLE

    fun handleKey(keyCode: Int): Boolean {
        if (!isVisible()) return false
        if (isRemotePage(currentPage)) {
            lightChip(keyCode)
            statusView.visibility = View.VISIBLE
            statusView.text = "Key Detected: ${keyMappingRepository.getKeyDisplayName(keyCode)}"
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                goBack()
                true
            }
            else -> false
        }
    }

    private fun goBack() {
        if (currentPage == 0) {
            onFinish()
        } else {
            currentPage = prevWalkthroughPage(currentPage)
            bindPage()
        }
    }

    private fun goNext() {
        if (isLastWalkthroughPage(currentPage)) {
            onFinish()
        } else {
            currentPage = nextWalkthroughPage(currentPage)
            bindPage()
        }
    }

    private fun bindPage() {
        titleView.text = titles[currentPage]
        bodyView.text = bodies[currentPage]
        skipBtn.visibility = if (isLastWalkthroughPage(currentPage)) View.GONE else View.VISIBLE
        backBtn.visibility = if (currentPage == 0) View.INVISIBLE else View.VISIBLE
        nextBtn.setText(
            if (isLastWalkthroughPage(currentPage)) R.string.walkthrough_get_started
            else R.string.walkthrough_next
        )
        chips.visibility = if (isRemotePage(currentPage)) View.VISIBLE else View.GONE
        playground.visibility = if (isPlaygroundPage(currentPage)) View.VISIBLE else View.GONE
        phonePanel.visibility = if (isPhonePage(currentPage)) View.VISIBLE else View.GONE

        if (isPlaygroundPage(currentPage)) {
            bindDemoRows()
        }
        if (isRemotePage(currentPage)) {
            bindChips()
        }
        if (!isPlaygroundPage(currentPage) && !isRemotePage(currentPage)) {
            hideStatus()
        }
        bindDots()

        if (isPlaygroundPage(currentPage)) {
            demoRows.first().requestFocus()
        } else {
            nextBtn.requestFocus()
        }
    }

    private fun bindDemoRows() {
        demoRows.forEachIndexed { index, row ->
            val channel = channels[index]
            row.findViewById<TextView>(R.id.demo_number).text = channel.customNumber
            row.findViewById<TextView>(R.id.demo_name).text = channel.name
            val caption = row.findViewById<TextView>(R.id.demo_caption)
            caption.text = when {
                channel.hidden -> overlay.context.getString(R.string.walkthrough_hidden)
                else -> overlay.context.getString(R.string.dth_format, channel.dthNumber)
            }
            row.findViewById<TextView>(R.id.demo_star).visibility =
                if (channel.favorite) View.VISIBLE else View.GONE
            row.alpha = if (channel.hidden) 0.45f else 1f
        }
    }

    private fun bindChips() {
        styleChip(chipZap, 0)
        styleChip(chipInfo, 1)
        styleChip(chipGuide, 2)
        styleChip(chipLast, 3)
    }

    private fun styleChip(chip: TextView, id: Int) {
        val on = litChips.contains(id)
        chip.setTextColor(
            overlay.context.getColor(if (on) R.color.accent else R.color.text_tertiary)
        )
    }

    private fun lightChip(keyCode: Int) {
        val id = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> 0
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> 1
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU -> 2
            KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 3
            else -> return
        }
        litChips.add(id)
        bindChips()
    }

    private fun onDemoActivated(index: Int) {
        val channel = channels[index]
        if (currentPage == 2) {
            val numToAssign = demoNumbersToAssign[demoAssignNumberIndex % demoNumbersToAssign.size]
            demoAssignNumberIndex++
            val result = assignDemoNumber(channels, channel.id, numToAssign) ?: return
            statusView.visibility = View.VISIBLE
            statusView.text = if (result.swappedWith != null) {
                overlay.context.getString(
                    R.string.walkthrough_status_swap,
                    channel.name,
                    result.assignedNumber,
                    result.swappedWith,
                    result.swappedNumber ?: ""
                )
            } else {
                overlay.context.getString(
                    R.string.walkthrough_status_assigned,
                    channel.name,
                    result.assignedNumber
                )
            }
            bindDemoRows()
        } else if (currentPage == 3) {
            val starred = toggleDemoFavorite(channels, channel.id)
            statusView.visibility = View.VISIBLE
            statusView.text = overlay.context.getString(
                if (starred) R.string.walkthrough_status_starred else R.string.walkthrough_status_unstarred,
                channel.name
            )
            bindDemoRows()
        }
    }

    private fun hideStatus() {
        statusView.visibility = View.GONE
        statusView.text = ""
    }

    private fun bindDots() {
        dotsContainer.removeAllViews()
        for (i in 0 until walkthroughPageCount()) {
            val dot = TextView(overlay.context)
            dot.text = "\u25CF"
            dot.textSize = 10f
            dot.setTextColor(
                overlay.context.getColor(
                    if (i == currentPage) R.color.accent else R.color.text_tertiary
                )
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(6, 0, 6, 0)
            dot.layoutParams = lp
            dotsContainer.addView(dot)
        }
    }
}
