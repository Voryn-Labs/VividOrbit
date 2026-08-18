package com.vividorbit.livetv

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vividorbit.livetv.data.Channel
import com.vividorbit.livetv.data.ChannelRepository
import com.vividorbit.livetv.data.EpgRepository
import com.vividorbit.livetv.data.StartupMode
import com.vividorbit.livetv.player.TvViewHelper
import com.vividorbit.livetv.server.LocalConfigServer
import com.vividorbit.livetv.server.NetworkUtils
import com.vividorbit.livetv.server.QrCodeGenerator
import com.vividorbit.livetv.ui.ChannelAdapter
import com.vividorbit.livetv.ui.ChannelLogoLoader
import com.vividorbit.livetv.ui.ChannelSettingsAdapter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

class MainActivity : Activity(), CoroutineScope {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainActivity", "Uncaught coroutine exception: ${throwable.message}", throwable)
    }

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job + exceptionHandler

    private lateinit var tvView: TvView
    private lateinit var tvViewHelper: TvViewHelper
    private lateinit var repository: ChannelRepository
    private lateinit var epgRepository: EpgRepository

    private lateinit var progressBar: ProgressBar
    private lateinit var channelUnavailableText: TextView
    private lateinit var sidebarContainer: View
    private lateinit var sidebarHeader: TextView
    private lateinit var sidebarSettingsBtn: TextView
    private lateinit var sidebarTabAll: TextView
    private lateinit var sidebarTabFavs: TextView
    private lateinit var channelRecyclerView: RecyclerView
    private lateinit var numericEntryCard: CardView
    private lateinit var numericEntryText: TextView
    private lateinit var noChannelsText: TextView

    private lateinit var settingsContainer: View
    private lateinit var settingsCloseBtn: TextView
    private lateinit var settingsToggleRow: View
    private lateinit var settingsToggleBadge: TextView
    private lateinit var settingsStartupRow: View
    private lateinit var settingsStartupSubtitle: TextView
    private lateinit var settingsPhoneSetupRow: View
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

    private lateinit var startupPickerCard: CardView
    private lateinit var startupOptLast: TextView
    private lateinit var startupOptFirst: TextView
    private lateinit var startupChannelRecycler: RecyclerView
    private lateinit var startupCancelBtn: TextView
    private lateinit var startupPickerAdapter: ChannelAdapter

    private lateinit var qrPanelCard: CardView
    private lateinit var qrConnectedLayout: View
    private lateinit var qrOfflineLayout: View
    private lateinit var qrImageView: ImageView
    private lateinit var qrUrlText: TextView
    private lateinit var qrRetryBtn: TextView
    private lateinit var qrCloseBtn: TextView

    private lateinit var confirmActionCard: CardView
    private lateinit var confirmTitle: TextView
    private lateinit var confirmDesc: TextView
    private lateinit var confirmCancelBtn: TextView
    private lateinit var confirmOkBtn: TextView
    private var pendingConfirmAction: (() -> Unit)? = null

    private var localConfigServer: LocalConfigServer? = null
    private var currentSessionToken: String = ""

    private lateinit var channelBannerCard: CardView
    private lateinit var bannerChannelNumber: TextView
    private lateinit var bannerChannelLogo: ImageView
    private lateinit var bannerChannelName: TextView
    private lateinit var bannerEpgLayout: View
    private lateinit var bannerProgramTitle: TextView
    private lateinit var bannerProgramTime: TextView
    private lateinit var bannerProgramProgress: ProgressBar
    private lateinit var bannerNextProgram: TextView

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable {
        if (::channelBannerCard.isInitialized) {
            channelBannerCard.visibility = View.GONE
        }
    }

    private lateinit var mediaSession: MediaSession
    private val sidebarHandler = Handler(Looper.getMainLooper())
    private val hideSidebarRunnable = Runnable {
        hideSidebar()
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val showProgressRunnable = Runnable {
        if (::progressBar.isInitialized) {
            progressBar.visibility = View.VISIBLE
        }
    }
    private val hideProgressFallbackRunnable = Runnable {
        if (::progressBar.isInitialized) {
            progressBar.visibility = View.GONE
        }
    }

    private var pendingZapChannel: Channel? = null
    private val zapHandler = Handler(Looper.getMainLooper())
    private val zapTuneRunnable = Runnable {
        pendingZapChannel?.let { tuneToChannel(it) }
    }

    private lateinit var channelAdapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var selectedChannel: Channel? = null
    private var previousChannelId: Long? = null
    private var isFavoritesFilterActive = false

    private var numericBuffer = ""
    private val numericHandler = Handler(Looper.getMainLooper())
    private val tuneRunnable = Runnable {
        val numberToTune = numericBuffer
        numericBuffer = ""
        if (::numericEntryCard.isInitialized) {
            numericEntryCard.visibility = View.GONE
        }
        tuneToChannelNumber(numberToTune)
    }

    companion object {
        private const val PRIMARY_PERMISSION = "android.permission.READ_TV_LISTINGS"
        private const val OPTIONAL_EPG_PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA"
        private const val PERMISSION_REQUEST_CODE = 1010
        private const val ZAP_DEBOUNCE_MS = 120L

        private const val SIDEBAR_AUTO_HIDE_MS = 20000L
        private const val BANNER_AUTO_HIDE_MS = 6000L
        private const val NUMERIC_ENTRY_TIMEOUT_MS = 3000L
        private const val NUMERIC_ENTRY_MAX_DIGITS = 4

        private const val TUNING_SHOW_DELAY_MS = 500L
        private const val TUNING_FALLBACK_TIMEOUT_MS = 8000L
        private const val SUSTAINED_BUFFERING_THRESHOLD_MS = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = SupervisorJob()
        setContentView(R.layout.activity_main)

        mediaSession = MediaSession(this, "VividOrbitLiveTv")
        mediaSession.isActive = true

        tvView = findViewById(R.id.tv_view)
        progressBar = findViewById(R.id.progress_bar)
        channelUnavailableText = findViewById(R.id.channel_unavailable_text)
        sidebarContainer = findViewById(R.id.sidebar_container)
        sidebarHeader = findViewById(R.id.sidebar_header)
        sidebarSettingsBtn = findViewById(R.id.sidebar_settings_btn)
        sidebarTabAll = findViewById(R.id.sidebar_tab_all)
        sidebarTabFavs = findViewById(R.id.sidebar_tab_favs)
        channelRecyclerView = findViewById(R.id.channel_recycler_view)
        numericEntryCard = findViewById(R.id.numeric_entry_card)
        numericEntryText = findViewById(R.id.numeric_entry_text)
        noChannelsText = findViewById(R.id.no_channels_text)

        settingsContainer = findViewById(R.id.settings_container)
        settingsCloseBtn = findViewById(R.id.settings_close_btn)
        settingsToggleRow = findViewById(R.id.settings_toggle_row)
        settingsToggleBadge = findViewById(R.id.settings_toggle_badge)
        settingsStartupRow = findViewById(R.id.settings_startup_row)
        settingsStartupSubtitle = findViewById(R.id.settings_startup_subtitle)
        settingsPhoneSetupRow = findViewById(R.id.settings_phone_setup_row)
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

        startupPickerCard = findViewById(R.id.startup_picker_card)
        startupOptLast = findViewById(R.id.startup_opt_last)
        startupOptFirst = findViewById(R.id.startup_opt_first)
        startupChannelRecycler = findViewById(R.id.startup_channel_recycler)
        startupCancelBtn = findViewById(R.id.startup_cancel_btn)

        qrPanelCard = findViewById(R.id.qr_panel_card)
        qrConnectedLayout = findViewById(R.id.qr_connected_layout)
        qrOfflineLayout = findViewById(R.id.qr_offline_layout)
        qrImageView = findViewById(R.id.qr_image_view)
        qrUrlText = findViewById(R.id.qr_url_text)
        qrRetryBtn = findViewById(R.id.qr_retry_btn)
        qrCloseBtn = findViewById(R.id.qr_close_btn)

        confirmActionCard = findViewById(R.id.confirm_action_card)
        confirmTitle = findViewById(R.id.confirm_title)
        confirmDesc = findViewById(R.id.confirm_desc)
        confirmCancelBtn = findViewById(R.id.confirm_cancel_btn)
        confirmOkBtn = findViewById(R.id.confirm_ok_btn)

        channelBannerCard = findViewById(R.id.channel_banner_card)
        bannerChannelNumber = findViewById(R.id.banner_channel_number)
        bannerChannelLogo = findViewById(R.id.banner_channel_logo)
        bannerChannelName = findViewById(R.id.banner_channel_name)
        bannerEpgLayout = findViewById(R.id.banner_epg_layout)
        bannerProgramTitle = findViewById(R.id.banner_program_title)
        bannerProgramTime = findViewById(R.id.banner_program_time)
        bannerProgramProgress = findViewById(R.id.banner_program_progress)
        bannerNextProgram = findViewById(R.id.banner_next_program)

        repository = ChannelRepository(this)
        epgRepository = EpgRepository(this)
        previousChannelId = repository.getPreviousChannelId().takeIf { it != -1L }

        sidebarSettingsBtn.setOnClickListener {
            openSettings()
        }

        settingsCloseBtn.setOnClickListener {
            closeSettings()
        }

        sidebarTabAll.setOnClickListener {
            setFavoritesFilter(false)
        }

        sidebarTabFavs.setOnClickListener {
            setFavoritesFilter(true)
        }

        settingsToggleRow.setOnClickListener {
            toggleCustomNumbers()
        }

        settingsStartupRow.setOnClickListener {
            openStartupPicker()
        }

        settingsPhoneSetupRow.setOnClickListener {
            openPhoneSetup()
        }

        settingsAutoRenumberBtn.setOnClickListener {
            showConfirmation(
                title = getString(R.string.confirm_auto_renumber_title),
                desc = getString(R.string.confirm_auto_renumber_desc),
                onConfirm = { autoRenumberLinear() }
            )
        }

        settingsResetDthBtn.setOnClickListener {
            showConfirmation(
                title = getString(R.string.confirm_reset_dth_title),
                desc = getString(R.string.confirm_reset_dth_desc),
                onConfirm = { resetToDth() }
            )
        }

        confirmCancelBtn.setOnClickListener {
            closeConfirmation()
        }

        confirmOkBtn.setOnClickListener {
            val action = pendingConfirmAction
            closeConfirmation()
            action?.invoke()
        }

        editCancelBtn.setOnClickListener {
            closeEditNumberDialog()
        }

        editSaveBtn.setOnClickListener {
            saveEditedNumber()
        }

        startupOptLast.setOnClickListener {
            repository.setStartupMode(StartupMode.LAST_WATCHED)
            closeStartupPicker()
            updateSettingsToggleUi()
        }

        startupOptFirst.setOnClickListener {
            repository.setStartupMode(StartupMode.FIRST_CHANNEL)
            closeStartupPicker()
            updateSettingsToggleUi()
        }

        startupCancelBtn.setOnClickListener {
            closeStartupPicker()
        }

        qrRetryBtn.setOnClickListener {
            openPhoneSetup()
        }

        qrCloseBtn.setOnClickListener {
            closePhoneSetup()
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

        channelAdapter = ChannelAdapter(
            channels = emptyList(),
            scope = this,
            epgRepository = epgRepository,
            isFavoriteChecker = { id -> repository.isFavorite(id) },
            onChannelClick = { channel ->
                tuneToChannel(channel)
                hideSidebar()
            },
            onChannelLongClick = { channel ->
                val isNowFav = repository.toggleFavorite(channel.id)
                channelAdapter.notifyFavoriteChanged(channel.id)
                val msg = if (isNowFav) "Added to Favorites ★" else "Removed from Favorites"
                Toast.makeText(this, "${channel.displayName}: $msg", Toast.LENGTH_SHORT).show()
                if (isFavoritesFilterActive) {
                    refreshDisplayedChannels()
                }
                true
            }
        )
        channelRecyclerView.layoutManager = LinearLayoutManager(this)
        channelRecyclerView.setHasFixedSize(true)
        channelRecyclerView.itemAnimator = null
        channelRecyclerView.adapter = channelAdapter

        channelSettingsAdapter = ChannelSettingsAdapter(
            channels = emptyList(),
            scope = this,
            onChannelClick = { channel ->
                openEditNumberDialog(channel)
            },
            onChannelLongClick = { channel ->
                repository.setStartupMode(StartupMode.FIXED_DEFAULT)
                repository.setDefaultChannelId(channel.id)
                Toast.makeText(this, getString(R.string.default_channel_set_toast, channel.displayName), Toast.LENGTH_SHORT).show()
                updateSettingsToggleUi()
                true
            }
        )
        settingsRecyclerView.layoutManager = LinearLayoutManager(this)
        settingsRecyclerView.setHasFixedSize(true)
        settingsRecyclerView.itemAnimator = null
        settingsRecyclerView.adapter = channelSettingsAdapter

        startupPickerAdapter = ChannelAdapter(
            channels = emptyList(),
            scope = this,
            onChannelClick = { channel ->
                repository.setStartupMode(StartupMode.FIXED_DEFAULT)
                repository.setDefaultChannelId(channel.id)
                closeStartupPicker()
                updateSettingsToggleUi()
            }
        )
        startupChannelRecycler.layoutManager = LinearLayoutManager(this)
        startupChannelRecycler.setHasFixedSize(true)
        startupChannelRecycler.adapter = startupPickerAdapter

        if (checkSelfPermission(PRIMARY_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(PRIMARY_PERMISSION, OPTIONAL_EPG_PERMISSION), PERMISSION_REQUEST_CODE)
        } else {
            loadChannelData()
        }
    }

    private fun setFavoritesFilter(enabled: Boolean) {
        isFavoritesFilterActive = enabled
        sidebarTabAll.isSelected = !enabled
        sidebarTabFavs.isSelected = enabled
        refreshDisplayedChannels()
        resetSidebarTimer()
    }

    private fun refreshDisplayedChannels() {
        val displayed = if (isFavoritesFilterActive) {
            allChannels.filter { repository.isFavorite(it.id) }
        } else {
            allChannels
        }
        channelAdapter.updateChannels(displayed)
        updateSidebarHeader(displayed.size)
    }

    private fun loadChannelData(preserveCurrentChannel: Boolean = false) {
        launch {
            progressBar.visibility = View.VISIBLE
            allChannels = repository.getChannels()

            refreshDisplayedChannels()
            channelSettingsAdapter.updateChannels(allChannels)
            startupPickerAdapter.updateChannels(allChannels)

            progressBar.visibility = View.GONE
            updateSettingsToggleUi()

            if (allChannels.isNotEmpty()) {
                val startChannel = repository.resolveStartupChannel(allChannels, preserveCurrentChannel, selectedChannel)
                if (startChannel != null) {
                    tuneToChannel(startChannel)
                }
            } else {
                showSidebar()
            }
        }
    }

    private fun updateSidebarHeader(count: Int) {
        val title = if (isFavoritesFilterActive) "Favorites · $count" else "Channels · $count"
        sidebarHeader.text = title
        noChannelsText.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    private fun updateSettingsToggleUi() {
        val enabled = repository.isCustomNumbersEnabled()
        settingsToggleBadge.text = if (enabled) getString(R.string.toggle_on) else getString(R.string.toggle_off)
        settingsToggleBadge.isSelected = enabled

        val mode = repository.getStartupMode()
        when (mode) {
            StartupMode.LAST_WATCHED -> {
                settingsStartupSubtitle.text = getString(R.string.startup_mode_last)
            }
            StartupMode.FIRST_CHANNEL -> {
                settingsStartupSubtitle.text = getString(R.string.startup_mode_first)
            }
            StartupMode.FIXED_DEFAULT -> {
                val defaultId = repository.getDefaultChannelId()
                val defChannel = allChannels.find { it.id == defaultId }
                val title = defChannel?.displayName ?: "Channel"
                settingsStartupSubtitle.text = getString(R.string.startup_mode_fixed_format, title)
            }
        }
    }

    private fun openSettings() {
        sidebarContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        updateSettingsToggleUi()
        channelSettingsAdapter.updateChannels(allChannels)
        settingsToggleRow.requestFocus()
        resetSidebarTimer()
    }

    private fun closeSettings() {
        settingsContainer.visibility = View.GONE
        sidebarContainer.visibility = View.VISIBLE

        val activeChannel = selectedChannel
        val currentList = if (isFavoritesFilterActive) allChannels.filter { repository.isFavorite(it.id) } else allChannels
        val index = if (activeChannel != null) {
            currentList.indexOfFirst { it.id == activeChannel.id }
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

    private fun openStartupPicker() {
        startupPickerCard.visibility = View.VISIBLE
        startupPickerAdapter.updateChannels(allChannels)
        startupOptLast.requestFocus()
        resetSidebarTimer()
    }

    private fun closeStartupPicker() {
        startupPickerCard.visibility = View.GONE
        settingsStartupRow.requestFocus()
        resetSidebarTimer()
    }

    private fun showConfirmation(title: String, desc: String, onConfirm: () -> Unit) {
        confirmTitle.text = title
        confirmDesc.text = desc
        pendingConfirmAction = onConfirm
        confirmActionCard.visibility = View.VISIBLE
        confirmCancelBtn.requestFocus()
        resetSidebarTimer()
    }

    private fun closeConfirmation() {
        confirmActionCard.visibility = View.GONE
        pendingConfirmAction = null
        settingsAutoRenumberBtn.requestFocus()
        resetSidebarTimer()
    }

    private fun openPhoneSetup() {
        val ip = NetworkUtils.getLocalIpAddress(this)
        if (ip.isNullOrBlank()) {
            qrConnectedLayout.visibility = View.GONE
            qrOfflineLayout.visibility = View.VISIBLE
            qrPanelCard.visibility = View.VISIBLE
            qrRetryBtn.requestFocus()
            return
        }

        if (currentSessionToken.isEmpty()) {
            val random = SecureRandom()
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            currentSessionToken = bytes.joinToString("") { "%02x".format(it) }
        }

        val url = "http://$ip:8080/?t=$currentSessionToken"
        qrUrlText.text = url

        val qrBitmap = QrCodeGenerator.generateQrBitmap(url, 400, 400)
        if (qrBitmap != null) {
            qrImageView.setImageBitmap(qrBitmap)
        }

        qrOfflineLayout.visibility = View.GONE
        qrConnectedLayout.visibility = View.VISIBLE
        qrPanelCard.visibility = View.VISIBLE

        if (localConfigServer == null) {
            localConfigServer = LocalConfigServer(
                context = this,
                repository = repository,
                port = 8080,
                sessionToken = currentSessionToken,
                onDataChanged = {
                    runOnUiThread {
                        loadChannelData(preserveCurrentChannel = true)
                    }
                },
                onTuneRequested = { channelId ->
                    runOnUiThread {
                        allChannels.find { it.id == channelId }?.let { tuneToChannel(it) }
                    }
                }
            )
            localConfigServer?.start()
        }

        qrCloseBtn.requestFocus()
        resetSidebarTimer()
    }

    private fun closePhoneSetup() {
        localConfigServer?.stop()
        localConfigServer = null
        currentSessionToken = ""
        qrPanelCard.visibility = View.GONE
        settingsPhoneSetupRow.requestFocus()
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
        val current = selectedChannel
        if (current != null && current.id != channel.id) {
            previousChannelId = current.id
            repository.setPreviousChannelId(current.id)
        }

        pendingZapChannel = null
        selectedChannel = channel
        repository.setLastChannelId(channel.id)
        showBottomBanner(channel)
        channelUnavailableText.visibility = View.GONE

        channelAdapter.setCurrentChannel(channel.id)

        if (tvViewHelper.isTunedTo(channel.id)) {
            return
        }

        progressHandler.removeCallbacks(showProgressRunnable)
        progressHandler.removeCallbacks(hideProgressFallbackRunnable)
        progressHandler.postDelayed(showProgressRunnable, TUNING_SHOW_DELAY_MS)
        tvViewHelper.tune(channel.inputId, channel.id, TvContract.buildChannelUri(channel.id))
    }

    private fun recallPreviousChannel() {
        val prevId = previousChannelId ?: repository.getPreviousChannelId().takeIf { it != -1L }
        if (prevId != null && prevId != -1L) {
            val target = allChannels.find { it.id == prevId }
            if (target != null) {
                tuneToChannel(target)
                Toast.makeText(this, "Quick Recall: ${target.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBottomBanner(channel: Channel) {
        bannerChannelNumber.text = channel.displayNumber
        bannerChannelName.text = channel.displayName
        bannerEpgLayout.visibility = View.GONE

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

        launch(Dispatchers.IO) {
            val (nowProgram, nextProgram) = epgRepository.getNowAndNext(channel.id)
            withContext(Dispatchers.Main) {
                if (selectedChannel?.id == channel.id && nowProgram != null && nowProgram.title.isNotBlank()) {
                    bannerProgramTitle.text = nowProgram.title
                    bannerProgramTime.text = nowProgram.getFormattedTimeWindow()
                    bannerProgramProgress.progress = nowProgram.getProgressPercent()

                    if (nextProgram != null && nextProgram.title.isNotBlank()) {
                        val nextTime = nextProgram.getFormattedTimeWindow().substringBefore(" – ")
                        val suffix = if (nextTime.isNotBlank()) " ($nextTime)" else ""
                        bannerNextProgram.text = getString(R.string.next_program_prefix, "${nextProgram.title}$suffix")
                        bannerNextProgram.visibility = View.VISIBLE
                    } else {
                        bannerNextProgram.visibility = View.GONE
                    }

                    bannerEpgLayout.visibility = View.VISIBLE
                } else if (selectedChannel?.id == channel.id) {
                    bannerEpgLayout.visibility = View.GONE
                }
            }
        }

        channelBannerCard.visibility = View.VISIBLE

        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, BANNER_AUTO_HIDE_MS)
    }

    private fun navigateChannel(direction: Int, isRepeat: Boolean) {
        val currentList = if (isFavoritesFilterActive) allChannels.filter { repository.isFavorite(it.id) } else allChannels
        if (currentList.isEmpty()) return

        val current = pendingZapChannel ?: selectedChannel
        var nextIndex = 0
        if (current != null) {
            val currentIndex = currentList.indexOfFirst { it.id == current.id }
            if (currentIndex != -1) {
                nextIndex = (currentIndex + direction) % currentList.size
                if (nextIndex < 0) {
                    nextIndex += currentList.size
                }
            }
        }
        val targetChannel = currentList[nextIndex]

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
        return (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) ||
                (::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE) ||
                (::editNumberCard.isInitialized && editNumberCard.visibility == View.VISIBLE) ||
                (::startupPickerCard.isInitialized && startupPickerCard.visibility == View.VISIBLE) ||
                (::qrPanelCard.isInitialized && qrPanelCard.visibility == View.VISIBLE) ||
                (::confirmActionCard.isInitialized && confirmActionCard.visibility == View.VISIBLE) ||
                (::numericEntryCard.isInitialized && numericEntryCard.visibility == View.VISIBLE)
    }

    private fun tuneToChannelNumber(number: String) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return

        val exactMatch = allChannels.find { it.displayNumber.equals(trimmed, ignoreCase = true) }
        if (exactMatch != null) {
            tuneToChannel(exactMatch)
            return
        }

        val parsedTarget = trimmed.toIntOrNull()
        if (parsedTarget != null) {
            val numericMatch = allChannels.find { it.displayNumber.toIntOrNull() == parsedTarget }
            if (numericMatch != null) {
                tuneToChannel(numericMatch)
                return
            }
        }

        Toast.makeText(this, "Channel $trimmed not found", Toast.LENGTH_SHORT).show()
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
        startupPickerCard.visibility = View.GONE
        qrPanelCard.visibility = View.GONE
        confirmActionCard.visibility = View.GONE
        if (::numericEntryCard.isInitialized) numericEntryCard.visibility = View.GONE
        sidebarContainer.visibility = View.VISIBLE

        val activeChannel = selectedChannel
        val currentList = if (isFavoritesFilterActive) allChannels.filter { repository.isFavorite(it.id) } else allChannels
        val index = if (activeChannel != null) {
            currentList.indexOfFirst { it.id == activeChannel.id }
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
        if (::sidebarContainer.isInitialized) sidebarContainer.visibility = View.GONE
        if (::settingsContainer.isInitialized) settingsContainer.visibility = View.GONE
        if (::editNumberCard.isInitialized) editNumberCard.visibility = View.GONE
        if (::startupPickerCard.isInitialized) startupPickerCard.visibility = View.GONE
        if (::confirmActionCard.isInitialized) confirmActionCard.visibility = View.GONE
        if (::numericEntryCard.isInitialized) numericEntryCard.visibility = View.GONE
        if (::qrPanelCard.isInitialized) {
            qrPanelCard.visibility = View.GONE
            localConfigServer?.stop()
            localConfigServer = null
            currentSessionToken = ""
        }
        sidebarHandler.removeCallbacks(hideSidebarRunnable)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && !isAnyMenuVisible()) {
            recallPreviousChannel()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        resetSidebarTimer()

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking()
        }

        if (::confirmActionCard.isInitialized && confirmActionCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeConfirmation()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (::qrPanelCard.isInitialized && qrPanelCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closePhoneSetup()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (qrRetryBtn.isFocused) {
                    openPhoneSetup()
                } else {
                    closePhoneSetup()
                }
                return true
            }
        }

        if (::startupPickerCard.isInitialized && startupPickerCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeStartupPicker()
                return true
            }
        }

        if (::editNumberCard.isInitialized && editNumberCard.visibility == View.VISIBLE) {
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

        if (::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeSettings()
                return true
            }
        }

        if (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                openSettings()
                return true
            }
        }

        if (::numericEntryCard.isInitialized && numericEntryCard.visibility == View.VISIBLE) {
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

        // Quick Recall Keys
        if (keyCode == KeyEvent.KEYCODE_LAST_CHANNEL || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            recallPreviousChannel()
            return true
        }

        // Favorite Toggle Keys (Yellow or Blue TV remote buttons)
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
            selectedChannel?.let { ch ->
                val isNowFav = repository.toggleFavorite(ch.id)
                channelAdapter.notifyFavoriteChanged(ch.id)
                val msg = if (isNowFav) "Added to Favorites ★" else "Removed from Favorites"
                Toast.makeText(this, "${ch.displayName}: $msg", Toast.LENGTH_SHORT).show()
                if (isFavoritesFilterActive) refreshDisplayedChannels()
            }
            return true
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
            if (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) {
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
            } else if (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) {
                openSettings()
                return true
            } else if (::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE) {
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ChannelLogoLoader.evictAll()
            epgRepository.clearCache()
        }
    }

    override fun onStop() {
        super.onStop()
        localConfigServer?.stop()
        localConfigServer = null
        currentSessionToken = ""
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
            val listingsIndex = permissions.indexOf(PRIMARY_PERMISSION)
            val isListingsGranted = listingsIndex != -1 &&
                    grantResults.size > listingsIndex &&
                    grantResults[listingsIndex] == PackageManager.PERMISSION_GRANTED

            if (isListingsGranted) {
                loadChannelData()
            } else {
                if (::progressBar.isInitialized) progressBar.visibility = View.GONE
                if (::channelUnavailableText.isInitialized) {
                    channelUnavailableText.text = getString(R.string.permission_required)
                    channelUnavailableText.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroy() {
        localConfigServer?.stop()
        localConfigServer = null
        currentSessionToken = ""
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
        sidebarHandler.removeCallbacksAndMessages(null)
        bannerHandler.removeCallbacksAndMessages(null)
        progressHandler.removeCallbacksAndMessages(null)
        numericHandler.removeCallbacksAndMessages(null)
        zapHandler.removeCallbacksAndMessages(null)
        job.cancel()
        if (::tvViewHelper.isInitialized) {
            tvViewHelper.cleanup()
            tvViewHelper.reset()
        }
        super.onDestroy()
    }
}
