package com.vividorbit.livetv

import android.app.Activity
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vividorbit.livetv.data.Channel
import com.vividorbit.livetv.data.ChannelRepository
import com.vividorbit.livetv.player.TvViewHelper
import com.vividorbit.livetv.ui.ChannelAdapter
import com.vividorbit.livetv.ui.ChannelLogoLoader
import com.vividorbit.livetv.ui.ChannelSettingsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class MainActivity : Activity(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var tvView: TvView
    private lateinit var tvViewHelper: TvViewHelper
    private lateinit var repository: ChannelRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var channelUnavailableText: TextView
    private lateinit var sidebarContainer: View
    private lateinit var sidebarHeader: TextView
    private lateinit var sidebarSettingsBtn: TextView
    private lateinit var channelRecyclerView: RecyclerView
    private lateinit var numericEntryCard: CardView
    private lateinit var numericEntryText: TextView
    private lateinit var noChannelsText: TextView

    private lateinit var settingsContainer: View
    private lateinit var settingsCloseBtn: TextView
    private lateinit var settingsToggleRow: View
    private lateinit var settingsToggleBadge: TextView
    private lateinit var settingsAutoRenumberBtn: TextView
    private lateinit var settingsResetDthBtn: TextView
    private lateinit var settingsRecyclerView: RecyclerView
    private lateinit var channelSettingsAdapter: ChannelSettingsAdapter

    private lateinit var editNumberCard: CardView
    private lateinit var editChannelLogo: ImageView
    private lateinit var editChannelName: TextView
    private lateinit var editNumberDisplay: TextView
    private lateinit var editConflictText: TextView
    private lateinit var editCancelBtn: TextView
    private lateinit var editSaveBtn: TextView

    private var editingChannel: Channel? = null
    private var editBuffer: String = ""
    private var isFirstDigitAfterOpen = true

    private lateinit var channelBannerCard: CardView
    private lateinit var bannerChannelNumber: TextView
    private lateinit var bannerChannelLogo: ImageView
    private lateinit var bannerChannelName: TextView

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable {
        channelBannerCard.visibility = View.GONE
    }

    private lateinit var mediaSession: MediaSession
    private val sidebarHandler = Handler(Looper.getMainLooper())
    private val hideSidebarRunnable = Runnable {
        hideSidebar()
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val showProgressRunnable = Runnable {
        progressBar.visibility = View.VISIBLE
    }
    private val hideProgressFallbackRunnable = Runnable {
        progressBar.visibility = View.GONE
    }

    private var pendingZapChannel: Channel? = null
    private val zapHandler = Handler(Looper.getMainLooper())
    private val zapTuneRunnable = Runnable {
        pendingZapChannel?.let { tuneToChannel(it) }
    }

    private lateinit var channelAdapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var selectedChannel: Channel? = null

    private var numericBuffer = ""
    private val numericHandler = Handler(Looper.getMainLooper())
    private val tuneRunnable = Runnable {
        val numberToTune = numericBuffer
        numericBuffer = ""
        numericEntryCard.visibility = View.GONE
        tuneToChannelNumber(numberToTune)
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            "com.android.providers.tv.permission.READ_EPG_DATA",
            "android.permission.READ_TV_LISTINGS"
        )
        private const val PERMISSION_REQUEST_CODE = 1010
        private const val ZAP_DEBOUNCE_MS = 120L

        private const val SIDEBAR_AUTO_HIDE_MS = 20000L
        private const val BANNER_AUTO_HIDE_MS = 5000L
        private const val NUMERIC_ENTRY_TIMEOUT_MS = 3000L
        private const val NUMERIC_ENTRY_MAX_DIGITS = 4

        private const val TUNING_SHOW_DELAY_MS = 500L
        private const val TUNING_FALLBACK_TIMEOUT_MS = 8000L
        private const val SUSTAINED_BUFFERING_THRESHOLD_MS = 3000L

        private const val PREFS_NAME = "vividorbit_prefs"
        private const val PREF_LAST_CHANNEL_ID = "last_channel_id"
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = Job()
        setContentView(R.layout.activity_main)

        mediaSession = MediaSession(this, "VividOrbitLiveTv")
        mediaSession.isActive = true

        tvView = findViewById(R.id.tv_view)
        progressBar = findViewById(R.id.progress_bar)
        channelUnavailableText = findViewById(R.id.channel_unavailable_text)
        sidebarContainer = findViewById(R.id.sidebar_container)
        sidebarHeader = findViewById(R.id.sidebar_header)
        sidebarSettingsBtn = findViewById(R.id.sidebar_settings_btn)
        channelRecyclerView = findViewById(R.id.channel_recycler_view)
        numericEntryCard = findViewById(R.id.numeric_entry_card)
        numericEntryText = findViewById(R.id.numeric_entry_text)
        noChannelsText = findViewById(R.id.no_channels_text)

        settingsContainer = findViewById(R.id.settings_container)
        settingsCloseBtn = findViewById(R.id.settings_close_btn)
        settingsToggleRow = findViewById(R.id.settings_toggle_row)
        settingsToggleBadge = findViewById(R.id.settings_toggle_badge)
        settingsAutoRenumberBtn = findViewById(R.id.settings_auto_renumber_btn)
        settingsResetDthBtn = findViewById(R.id.settings_reset_dth_btn)
        settingsRecyclerView = findViewById(R.id.settings_recycler_view)

        editNumberCard = findViewById(R.id.edit_number_card)
        editChannelLogo = findViewById(R.id.edit_channel_logo)
        editChannelName = findViewById(R.id.edit_channel_name)
        editNumberDisplay = findViewById(R.id.edit_number_display)
        editConflictText = findViewById(R.id.edit_conflict_text)
        editCancelBtn = findViewById(R.id.edit_cancel_btn)
        editSaveBtn = findViewById(R.id.edit_save_btn)

        channelBannerCard = findViewById(R.id.channel_banner_card)
        bannerChannelNumber = findViewById(R.id.banner_channel_number)
        bannerChannelLogo = findViewById(R.id.banner_channel_logo)
        bannerChannelName = findViewById(R.id.banner_channel_name)

        repository = ChannelRepository(this)

        sidebarSettingsBtn.setOnClickListener {
            openSettings()
        }

        settingsCloseBtn.setOnClickListener {
            closeSettings()
        }

        settingsToggleRow.setOnClickListener {
            toggleCustomNumbers()
        }

        settingsAutoRenumberBtn.setOnClickListener {
            autoRenumberLinear()
        }

        settingsResetDthBtn.setOnClickListener {
            resetToDth()
        }

        editCancelBtn.setOnClickListener {
            closeEditNumberDialog()
        }

        editSaveBtn.setOnClickListener {
            saveEditedNumber()
        }

        tvViewHelper = TvViewHelper(
            tvView = tvView,
            onVideoAvailable = {
                progressHandler.removeCallbacks(showProgressRunnable)
                progressHandler.removeCallbacks(hideProgressFallbackRunnable)
                progressBar.visibility = View.GONE
                channelUnavailableText.visibility = View.GONE

                val stateBuilder = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                mediaSession.setPlaybackState(stateBuilder.build())
            },
            onVideoUnavailable = { reason ->
                progressHandler.removeCallbacks(showProgressRunnable)
                progressHandler.removeCallbacks(hideProgressFallbackRunnable)

                val stateBuilder = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
                mediaSession.setPlaybackState(stateBuilder.build())

                when {
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> {
                        progressHandler.postDelayed(showProgressRunnable, TUNING_SHOW_DELAY_MS)
                        progressHandler.postDelayed(hideProgressFallbackRunnable, TUNING_FALLBACK_TIMEOUT_MS)
                    }
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING && !tvViewHelper.hasStartedPlayback() -> {
                        progressHandler.postDelayed(showProgressRunnable, TUNING_SHOW_DELAY_MS)
                        progressHandler.postDelayed(hideProgressFallbackRunnable, TUNING_FALLBACK_TIMEOUT_MS)
                    }
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> {
                        progressHandler.postDelayed(showProgressRunnable, SUSTAINED_BUFFERING_THRESHOLD_MS)
                        progressHandler.postDelayed(
                            hideProgressFallbackRunnable,
                            SUSTAINED_BUFFERING_THRESHOLD_MS + TUNING_FALLBACK_TIMEOUT_MS
                        )
                    }
                    else -> {
                        showChannelUnavailable()
                    }
                }
            },
            onInputError = {
                val stateBuilder = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
                mediaSession.setPlaybackState(stateBuilder.build())
                showChannelUnavailable()
            }
        )

        val channelLayoutManager = LinearLayoutManager(this)
        channelRecyclerView.layoutManager = channelLayoutManager
        channelRecyclerView.setHasFixedSize(true)
        channelRecyclerView.itemAnimator = null

        val settingsLayoutManager = LinearLayoutManager(this)
        settingsRecyclerView.layoutManager = settingsLayoutManager
        settingsRecyclerView.setHasFixedSize(true)
        settingsRecyclerView.itemAnimator = null

        val missing = REQUIRED_PERMISSIONS.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            loadChannelData()
        }
    }

    private fun loadChannelData(preserveCurrentChannel: Boolean = false) {
        launch {
            progressBar.visibility = View.VISIBLE
            allChannels = repository.getChannels()

            channelAdapter = ChannelAdapter(
                channels = allChannels,
                scope = this@MainActivity,
                onChannelClick = { channel ->
                    tuneToChannel(channel)
                    hideSidebar()
                }
            )
            channelRecyclerView.adapter = channelAdapter

            channelSettingsAdapter = ChannelSettingsAdapter(
                channels = allChannels,
                scope = this@MainActivity,
                onChannelClick = { channel ->
                    openEditNumberDialog(channel)
                }
            )
            settingsRecyclerView.adapter = channelSettingsAdapter

            progressBar.visibility = View.GONE
            updateSidebarHeader(allChannels.size)
            updateSettingsToggleUi()

            if (allChannels.isNotEmpty()) {
                val activeChannel = selectedChannel
                val startChannel = if (preserveCurrentChannel && activeChannel != null) {
                    allChannels.find { it.id == activeChannel.id } ?: allChannels[0]
                } else {
                    val lastChannelId = prefs.getLong(PREF_LAST_CHANNEL_ID, -1L)
                    allChannels.find { it.id == lastChannelId } ?: allChannels[0]
                }
                tuneToChannel(startChannel)
            } else {
                showSidebar()
            }
        }
    }

    private fun updateSidebarHeader(count: Int) {
        sidebarHeader.text = getString(R.string.sidebar_header_format, count)
        noChannelsText.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    private fun updateSettingsToggleUi() {
        val enabled = repository.isCustomNumbersEnabled()
        settingsToggleBadge.text = if (enabled) getString(R.string.toggle_on) else getString(R.string.toggle_off)
        settingsToggleBadge.isSelected = enabled
    }

    private fun openSettings() {
        sidebarContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        updateSettingsToggleUi()
        if (::channelSettingsAdapter.isInitialized) {
            channelSettingsAdapter.updateChannels(allChannels)
        }
        settingsToggleRow.requestFocus()
        resetSidebarTimer()
    }

    private fun closeSettings() {
        settingsContainer.visibility = View.GONE
        sidebarContainer.visibility = View.VISIBLE

        val activeChannel = selectedChannel
        val index = if (activeChannel != null) {
            allChannels.indexOfFirst { it.id == activeChannel.id }
        } else {
            -1
        }

        val lm = channelRecyclerView.layoutManager as? LinearLayoutManager
        if (index != -1 && lm != null) {
            lm.scrollToPositionWithOffset(index, 140)
            channelRecyclerView.post {
                val holder = channelRecyclerView.findViewHolderForAdapterPosition(index)
                holder?.itemView?.requestFocus() ?: channelRecyclerView.requestFocus()
            }
        } else {
            channelRecyclerView.requestFocus()
        }
        resetSidebarTimer()
    }

    private fun toggleCustomNumbers() {
        val newState = !repository.isCustomNumbersEnabled()
        repository.setCustomNumbersEnabled(newState)
        updateSettingsToggleUi()
        loadChannelData(preserveCurrentChannel = true)
    }

    private fun autoRenumberLinear() {
        launch {
            progressBar.visibility = View.VISIBLE
            repository.autoAssignLinearOrder(allChannels)
            repository.setCustomNumbersEnabled(true)
            loadChannelData(preserveCurrentChannel = true)
        }
    }

    private fun resetToDth() {
        launch {
            progressBar.visibility = View.VISIBLE
            repository.resetCustomNumbers()
            loadChannelData(preserveCurrentChannel = true)
        }
    }

    private fun openEditNumberDialog(channel: Channel) {
        editingChannel = channel
        editBuffer = channel.displayNumber
        isFirstDigitAfterOpen = true
        editChannelName.text = channel.displayName
        editNumberDisplay.text = editBuffer

        val cachedLogo = ChannelLogoLoader.getCached(channel.id)
        if (cachedLogo != null) {
            editChannelLogo.setImageBitmap(cachedLogo)
        } else {
            editChannelLogo.setImageResource(android.R.drawable.ic_menu_slideshow)
            launch(Dispatchers.IO) {
                val bitmap = ChannelLogoLoader.loadAndCache(this@MainActivity, channel.id, channel.logoUri)
                withContext(Dispatchers.Main) {
                    if (editingChannel?.id == channel.id && bitmap != null) {
                        editChannelLogo.setImageBitmap(bitmap)
                    }
                }
            }
        }

        updateConflictIndicator(editBuffer)
        editNumberCard.visibility = View.VISIBLE
        editSaveBtn.requestFocus()
        resetSidebarTimer()
    }

    private fun updateConflictIndicator(number: String) {
        val targetId = editingChannel?.id ?: return
        val conflictChannel = allChannels.find { it.displayNumber == number && it.id != targetId }
        if (conflictChannel != null) {
            editConflictText.text = getString(R.string.conflict_swap_format, number, conflictChannel.displayName)
            editConflictText.visibility = View.VISIBLE
        } else {
            editConflictText.visibility = View.GONE
        }
    }

    private fun saveEditedNumber() {
        val channel = editingChannel ?: return
        val targetNumber = editBuffer.trim()
        if (targetNumber.isNotEmpty()) {
            launch {
                progressBar.visibility = View.VISIBLE
                repository.assignChannelNumber(channel.id, targetNumber)
                repository.setCustomNumbersEnabled(true)
                closeEditNumberDialog()
                loadChannelData(preserveCurrentChannel = true)
            }
        } else {
            closeEditNumberDialog()
        }
    }

    private fun closeEditNumberDialog() {
        editNumberCard.visibility = View.GONE
        editingChannel = null
        editBuffer = ""
        isFirstDigitAfterOpen = true
        settingsRecyclerView.requestFocus()
        resetSidebarTimer()
    }

    private fun showChannelUnavailable() {
        progressHandler.removeCallbacks(showProgressRunnable)
        progressHandler.removeCallbacks(hideProgressFallbackRunnable)
        progressBar.visibility = View.GONE
        channelUnavailableText.visibility = View.VISIBLE
    }

    private fun tuneToChannel(channel: Channel) {
        pendingZapChannel = null
        selectedChannel = channel
        prefs.edit()
            .putLong(PREF_LAST_CHANNEL_ID, channel.id)
            .apply()
        showBottomBanner(channel)
        channelUnavailableText.visibility = View.GONE

        if (::channelAdapter.isInitialized) {
            channelAdapter.setCurrentChannel(channel.id)
        }

        if (tvViewHelper.isTunedTo(channel.id)) {
            return
        }

        progressHandler.removeCallbacks(showProgressRunnable)
        progressHandler.removeCallbacks(hideProgressFallbackRunnable)
        progressHandler.postDelayed(showProgressRunnable, TUNING_SHOW_DELAY_MS)
        tvViewHelper.tune(channel.inputId, channel.id, TvContract.buildChannelUri(channel.id))
    }

    private fun showBottomBanner(channel: Channel) {
        bannerChannelNumber.text = channel.displayNumber
        bannerChannelName.text = channel.displayName

        val cachedLogo = ChannelLogoLoader.getCached(channel.id)
        if (cachedLogo != null) {
            bannerChannelLogo.setImageBitmap(cachedLogo)
        } else {
            bannerChannelLogo.setImageResource(android.R.drawable.ic_menu_slideshow)
            launch(Dispatchers.IO) {
                val bitmap = ChannelLogoLoader.loadAndCache(this@MainActivity, channel.id, channel.logoUri)
                withContext(Dispatchers.Main) {
                    if (selectedChannel?.id == channel.id) {
                        if (bitmap != null) {
                            bannerChannelLogo.setImageBitmap(bitmap)
                        } else {
                            bannerChannelLogo.setImageResource(android.R.drawable.ic_menu_slideshow)
                        }
                    }
                }
            }
        }

        channelBannerCard.visibility = View.VISIBLE

        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, BANNER_AUTO_HIDE_MS)
    }

    private fun navigateChannel(direction: Int, isRepeat: Boolean) {
        if (allChannels.isEmpty()) return

        val current = pendingZapChannel ?: selectedChannel
        var nextIndex = 0
        if (current != null) {
            val currentIndex = allChannels.indexOfFirst { it.id == current.id }
            if (currentIndex != -1) {
                nextIndex = (currentIndex + direction) % allChannels.size
                if (nextIndex < 0) {
                    nextIndex += allChannels.size
                }
            }
        }
        val targetChannel = allChannels[nextIndex]

        pendingZapChannel = targetChannel
        selectedChannel = targetChannel
        showBottomBanner(targetChannel)

        if (isRepeat) {
            zapHandler.removeCallbacks(zapTuneRunnable)
            zapHandler.postDelayed(zapTuneRunnable, ZAP_DEBOUNCE_MS)
        } else {
            zapHandler.removeCallbacks(zapTuneRunnable)
            tuneToChannel(targetChannel)
        }
    }

    private fun isAnyMenuVisible(): Boolean {
        return sidebarContainer.visibility == View.VISIBLE ||
                settingsContainer.visibility == View.VISIBLE ||
                editNumberCard.visibility == View.VISIBLE
    }

    private fun tuneToChannelNumber(number: String) {
        val parsedTarget = number.toIntOrNull() ?: return
        val channel = allChannels.find { it.displayNumber.toIntOrNull() == parsedTarget }
        if (channel != null) {
            tuneToChannel(channel)
        }
    }

    private fun resetSidebarTimer() {
        sidebarHandler.removeCallbacks(hideSidebarRunnable)
        if (isAnyMenuVisible()) {
            sidebarHandler.postDelayed(hideSidebarRunnable, SIDEBAR_AUTO_HIDE_MS)
        }
    }

    private fun showSidebar() {
        settingsContainer.visibility = View.GONE
        editNumberCard.visibility = View.GONE
        sidebarContainer.visibility = View.VISIBLE

        val activeChannel = selectedChannel
        val index = if (activeChannel != null) {
            allChannels.indexOfFirst { it.id == activeChannel.id }
        } else {
            -1
        }

        val lm = channelRecyclerView.layoutManager as? LinearLayoutManager
        if (index != -1 && lm != null) {
            lm.scrollToPositionWithOffset(index, 140)
            channelRecyclerView.post {
                val holder = channelRecyclerView.findViewHolderForAdapterPosition(index)
                holder?.itemView?.requestFocus() ?: channelRecyclerView.requestFocus()
            }
        } else {
            channelRecyclerView.requestFocus()
        }

        resetSidebarTimer()
    }

    private fun hideSidebar() {
        sidebarContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        editNumberCard.visibility = View.GONE
        sidebarHandler.removeCallbacks(hideSidebarRunnable)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        resetSidebarTimer()

        if (editNumberCard.visibility == View.VISIBLE) {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                if (isFirstDigitAfterOpen) {
                    editBuffer = digit
                    isFirstDigitAfterOpen = false
                } else {
                    if (editBuffer.length < NUMERIC_ENTRY_MAX_DIGITS) {
                        editBuffer += digit
                    }
                }
                editNumberDisplay.text = editBuffer
                updateConflictIndicator(editBuffer)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                val cur = editBuffer.toIntOrNull() ?: 0
                editBuffer = (cur + 1).coerceAtMost(9999).toString()
                isFirstDigitAfterOpen = false
                editNumberDisplay.text = editBuffer
                updateConflictIndicator(editBuffer)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val cur = editBuffer.toIntOrNull() ?: 1
                editBuffer = (cur - 1).coerceAtLeast(1).toString()
                isFirstDigitAfterOpen = false
                editNumberDisplay.text = editBuffer
                updateConflictIndicator(editBuffer)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (editBuffer.length > 1) {
                    editBuffer = editBuffer.dropLast(1)
                } else {
                    editBuffer = "1"
                }
                isFirstDigitAfterOpen = false
                editNumberDisplay.text = editBuffer
                updateConflictIndicator(editBuffer)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeEditNumberDialog()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (editCancelBtn.isFocused) {
                    closeEditNumberDialog()
                } else {
                    saveEditedNumber()
                }
                return true
            }
        }

        if (settingsContainer.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeSettings()
                return true
            }
        }

        if (sidebarContainer.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                openSettings()
                return true
            }
        }

        if (numericEntryCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                numericHandler.removeCallbacks(tuneRunnable)
                tuneRunnable.run()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                numericHandler.removeCallbacks(tuneRunnable)
                numericBuffer = ""
                numericEntryCard.visibility = View.GONE
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
            navigateChannel(1, event.repeatCount > 0)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            navigateChannel(-1, event.repeatCount > 0)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (!isAnyMenuVisible()) {
                navigateChannel(-1, event.repeatCount > 0)
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!isAnyMenuVisible()) {
                navigateChannel(1, event.repeatCount > 0)
                return true
            }
        }

        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            if (!isAnyMenuVisible()) {
                if (numericBuffer.length < NUMERIC_ENTRY_MAX_DIGITS) {
                    val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                    numericHandler.removeCallbacks(tuneRunnable)
                    numericBuffer += digit
                    numericEntryText.text = numericBuffer
                    numericEntryCard.visibility = View.VISIBLE
                    numericHandler.postDelayed(tuneRunnable, NUMERIC_ENTRY_TIMEOUT_MS)
                }
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (sidebarContainer.visibility == View.VISIBLE) {
                hideSidebar()
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!isAnyMenuVisible()) {
                selectedChannel?.let { showBottomBanner(it) }
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_GUIDE) {
            if (!isAnyMenuVisible()) {
                showSidebar()
                return true
            } else if (sidebarContainer.visibility == View.VISIBLE) {
                openSettings()
                return true
            } else if (settingsContainer.visibility == View.VISIBLE) {
                hideSidebar()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                loadChannelData()
            } else {
                progressBar.visibility = View.GONE
                channelUnavailableText.text = getString(R.string.permission_required)
                channelUnavailableText.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        sidebarHandler.removeCallbacksAndMessages(null)
        bannerHandler.removeCallbacksAndMessages(null)
        progressHandler.removeCallbacksAndMessages(null)
        numericHandler.removeCallbacksAndMessages(null)
        zapHandler.removeCallbacksAndMessages(null)
        job.cancel()
        tvViewHelper.cleanup()
        tvViewHelper.reset()
    }
}
