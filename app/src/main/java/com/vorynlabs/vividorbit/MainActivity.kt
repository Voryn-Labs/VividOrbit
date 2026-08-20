package com.vorynlabs.vividorbit

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vorynlabs.vividorbit.data.Channel
import com.vorynlabs.vividorbit.data.ChannelRepository
import com.vorynlabs.vividorbit.data.EpgRepository
import com.vorynlabs.vividorbit.data.KeyMappingRepository
import com.vorynlabs.vividorbit.data.RemoteAction
import com.vorynlabs.vividorbit.data.StartupMode
import com.vorynlabs.vividorbit.player.TvViewHelper
import com.vorynlabs.vividorbit.server.LocalConfigServer
import com.vorynlabs.vividorbit.server.NetworkUtils
import com.vorynlabs.vividorbit.server.QrCodeGenerator
import com.vorynlabs.vividorbit.ui.ChannelAdapter
import com.vorynlabs.vividorbit.ui.ChannelLogoLoader
import com.vorynlabs.vividorbit.ui.ChannelSettingsAdapter
import com.vorynlabs.vividorbit.ui.WalkthroughController
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
    private lateinit var genreList: LinearLayout
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
    private lateinit var settingsAboutRow: View
    private lateinit var settingsWalkthroughRow: View
    private lateinit var settingsBannerRow: View
    private lateinit var settingsBannerSubtitle: TextView
    private lateinit var settingsGuideHideRow: View
    private lateinit var settingsGuideHideSubtitle: TextView
    private lateinit var settingsHideRow: View
    private lateinit var settingsHideSubtitle: TextView
    private lateinit var settingsGuideEpgRow: View
    private lateinit var settingsGuideEpgBadge: TextView
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

    private lateinit var keyMappingRepository: KeyMappingRepository
    private lateinit var settingsKeymapRow: View
    private lateinit var keymapListContainer: LinearLayout
    private lateinit var keymapHintText: TextView
    private lateinit var keymapResetBtn: TextView
    private lateinit var keymapCloseBtn: TextView
    private var activeMappingAction: RemoteAction? = null
    private lateinit var settingsNavLineup: TextView
    private lateinit var settingsNavRemote: TextView
    private lateinit var settingsNavPhone: TextView
    private lateinit var settingsNavDisplay: TextView
    private lateinit var settingsNavAbout: TextView
    private lateinit var settingsSectionLineup: View
    private lateinit var settingsSectionRemote: View
    private lateinit var settingsSectionPhone: View
    private lateinit var settingsSectionDisplay: View
    private lateinit var settingsSectionAbout: View
    private lateinit var settingsPhoneQr: ImageView
    private lateinit var settingsPhoneUrl: TextView
    private lateinit var channelActionCard: View
    private lateinit var channelActionTitle: TextView
    private lateinit var channelActionFavorite: TextView
    private lateinit var channelActionNumber: TextView
    private lateinit var channelActionStartup: TextView
    private var actionSheetChannel: Channel? = null

    private var localConfigServer: LocalConfigServer? = null
    private var currentSessionToken: String = ""

    private lateinit var channelBannerCard: View
    private lateinit var bannerChannelNumber: TextView
    private lateinit var bannerChannelLogo: ImageView
    private lateinit var bannerChannelName: TextView
    private lateinit var bannerEpgLayout: View
    private lateinit var bannerProgramTitle: TextView
    private lateinit var bannerProgramTime: TextView
    private lateinit var bannerProgramProgress: ProgressBar
    private lateinit var bannerNextProgram: TextView

    private lateinit var channelProgramsCard: CardView
    private lateinit var programsTitle: TextView
    private lateinit var programsSubtitle: TextView
    private lateinit var programsContainer: LinearLayout

    private lateinit var walkthroughOverlay: View
    private lateinit var walkthroughController: WalkthroughController
    private lateinit var appToast: TextView
    private val toastHandler = Handler(Looper.getMainLooper())
    private val hideToastRunnable = Runnable {
        if (::appToast.isInitialized) appToast.visibility = View.GONE
    }

    private val bannerHandler = Handler(Looper.getMainLooper())
    private var pendingBannerChannel: Channel? = null
    private val hideBannerRunnable = Runnable {
        if (::channelBannerCard.isInitialized) {
            channelBannerCard.visibility = View.GONE
        }
    }
    private val fetchBannerEpgRunnable = Runnable {
        pendingBannerChannel?.let { fetchBannerEpg(it) }
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
    private val stuckTuningRunnable = Runnable {
        showChannelUnavailable()
    }

    private var pendingZapChannel: Channel? = null
    private val zapHandler = Handler(Looper.getMainLooper())
    private val zapTuneRunnable = Runnable {
        pendingZapChannel?.let { performTune(it) }
    }

    private lateinit var channelAdapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var selectedChannel: Channel? = null
    private var previewChannel: Channel? = null
    private var previousChannelId: Long? = null
    private var isFavoritesFilterActive = false
    private var activeGenre: String? = null
    private var activeProgramsChannel: Channel? = null
    private var guideLevel = 1
    private var settingsAtRoot = true
    private var backLongPressHandled = false
    private var lastBackPressTime = 0L
    private var hasRevertedToPrevious = false

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
        private const val ZAP_DEBOUNCE_MS = 80L
        private const val EPG_FETCH_DEBOUNCE_MS = 300L
        private const val ABOUT_US_URL = "https://voryn-labs.github.io/"
        private const val PRIVACY_POLICY_URL = "https://voryn-labs.github.io/vividorbit-privacy.html"
        private const val CONTACT_EMAIL = "appsvorynlabs@gmail.com"

        private const val SIDEBAR_AUTO_HIDE_MS = 20000L
        private const val BANNER_AUTO_HIDE_MS = 6000L
        private const val NUMERIC_ENTRY_TIMEOUT_MS = 3000L
        private const val NUMERIC_ENTRY_MAX_DIGITS = 4

        private const val TUNING_SHOW_DELAY_MS = 500L
        private const val TUNING_FALLBACK_TIMEOUT_MS = 8000L
        private const val SUSTAINED_BUFFERING_THRESHOLD_MS = 3000L
        private const val GENRE_ALL = "All"
        private const val GENRE_GENERAL = "General"
    }

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val name = intent?.getStringExtra("filename") ?: "screenshot_${System.currentTimeMillis()}.png"
            captureScreenshot(name)
        }
    }

    private fun captureScreenshot(fileName: String) {
        val decorView = window.decorView
        if (decorView.width <= 0 || decorView.height <= 0) return
        val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    val file = File(getExternalFilesDir(null), fileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Log.i("MainActivity", "Screenshot successfully saved to ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to save screenshot: ${e.message}")
                }
            } else {
                Log.e("MainActivity", "PixelCopy failed with error code $copyResult")
            }
        }, Handler(Looper.getMainLooper()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = SupervisorJob()
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, IntentFilter("com.vorynlabs.vividorbit.ACTION_SCREENSHOT"), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, IntentFilter("com.vorynlabs.vividorbit.ACTION_SCREENSHOT"))
        }

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
        genreList = findViewById(R.id.genre_list)
        channelRecyclerView = findViewById(R.id.channel_recycler_view)
        numericEntryCard = findViewById(R.id.numeric_entry_card)
        numericEntryText = findViewById(R.id.numeric_entry_text)
        noChannelsText = findViewById(R.id.no_channels_text)

        keyMappingRepository = KeyMappingRepository(this)

        settingsContainer = findViewById(R.id.settings_container)
        settingsCloseBtn = findViewById(R.id.settings_close_btn)
        settingsToggleRow = findViewById(R.id.settings_toggle_row)
        settingsToggleBadge = findViewById(R.id.settings_toggle_badge)
        settingsStartupRow = findViewById(R.id.settings_startup_row)
        settingsStartupSubtitle = findViewById(R.id.settings_startup_subtitle)
        settingsPhoneSetupRow = findViewById(R.id.settings_phone_setup_row)
        settingsAboutRow = findViewById(R.id.settings_about_row)
        settingsWalkthroughRow = findViewById(R.id.settings_walkthrough_row)
        settingsKeymapRow = findViewById(R.id.settings_keymap_row)
        keymapListContainer = findViewById(R.id.keymap_list_container)
        keymapHintText = findViewById(R.id.keymap_hint_text)
        keymapResetBtn = findViewById(R.id.keymap_reset_btn)
        keymapCloseBtn = findViewById(R.id.keymap_close_btn)
        settingsNavLineup = findViewById(R.id.settings_nav_lineup)
        settingsNavRemote = findViewById(R.id.settings_nav_remote)
        settingsNavPhone = findViewById(R.id.settings_nav_phone)
        settingsNavDisplay = findViewById(R.id.settings_nav_display)
        settingsNavAbout = findViewById(R.id.settings_nav_about)
        settingsSectionLineup = findViewById(R.id.settings_section_lineup)
        settingsSectionRemote = findViewById(R.id.settings_section_remote)
        settingsSectionPhone = findViewById(R.id.settings_section_phone)
        settingsSectionDisplay = findViewById(R.id.settings_section_display)
        settingsSectionAbout = findViewById(R.id.settings_section_about)
        settingsPhoneQr = findViewById(R.id.settings_phone_qr)
        settingsPhoneUrl = findViewById(R.id.settings_phone_url)
        channelActionCard = findViewById(R.id.channel_action_card)
        channelActionTitle = findViewById(R.id.channel_action_title)
        channelActionFavorite = findViewById(R.id.channel_action_favorite)
        channelActionNumber = findViewById(R.id.channel_action_number)
        channelActionStartup = findViewById(R.id.channel_action_startup)
        settingsBannerRow = findViewById(R.id.settings_banner_row)
        settingsBannerSubtitle = findViewById(R.id.settings_banner_subtitle)
        settingsGuideHideRow = findViewById(R.id.settings_guide_hide_row)
        settingsGuideHideSubtitle = findViewById(R.id.settings_guide_hide_subtitle)
        settingsHideRow = findViewById(R.id.settings_hide_row)
        settingsHideSubtitle = findViewById(R.id.settings_hide_subtitle)
        settingsGuideEpgRow = findViewById(R.id.settings_guide_epg_row)
        settingsGuideEpgBadge = findViewById(R.id.settings_guide_epg_badge)
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

        channelProgramsCard = findViewById(R.id.channel_programs_card)
        programsTitle = findViewById(R.id.programs_title)
        programsSubtitle = findViewById(R.id.programs_subtitle)
        programsContainer = findViewById(R.id.programs_container)

        appToast = findViewById(R.id.app_toast)
        walkthroughOverlay = findViewById(R.id.walkthrough_overlay)
        walkthroughController = WalkthroughController(
            overlay = walkthroughOverlay,
            onShowInfo = {
                selectedChannel?.let { showBottomBanner(it) }
            },
            onShowGuide = {
                showSidebar()
            },
            onShowLineup = {
                openSettings()
                showSettingsSection(0)
            },
            onShowPhone = {
                openSettings()
                showSettingsSection(2)
            },
            onLeavePanels = {
                if (::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE) {
                    closeSettings()
                }
                if (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) {
                    hideSidebar()
                }
            },
            onFinish = {
                repository.setWalkthroughSeen()
                walkthroughController.hide()
                if (selectedChannel == null) {
                    tuneToStartupChannel(preserveCurrentChannel = false)
                }
            }
        )

        repository = ChannelRepository(this)
        epgRepository = EpgRepository(this)
        previousChannelId = repository.getPreviousChannelId().takeIf { it != -1L }

        if (!repository.hasSeenWalkthrough()) {
            showWalkthrough()
        }

        sidebarSettingsBtn.setOnClickListener {
            openSettings()
        }

        settingsCloseBtn.setOnClickListener {
            closeSettings()
        }

        sidebarTabAll.isSelected = true
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
            bindPhoneSection()
        }

        settingsAboutRow.setOnClickListener {
            openAboutUs()
        }

        settingsWalkthroughRow.setOnClickListener {
            showWalkthrough()
        }

        settingsKeymapRow.setOnClickListener {
            showSettingsSection(1)
        }
        settingsNavLineup.setOnClickListener { showSettingsSection(0) }
        settingsNavRemote.setOnClickListener { showSettingsSection(1) }
        settingsNavPhone.setOnClickListener { showSettingsSection(2) }
        settingsNavDisplay.setOnClickListener { showSettingsSection(3) }
        settingsNavAbout.setOnClickListener { showSettingsSection(4) }
        channelActionFavorite.setOnClickListener { applyChannelActionFavorite() }
        channelActionNumber.setOnClickListener { applyChannelActionNumber() }
        channelActionStartup.setOnClickListener { applyChannelActionStartup() }

        keymapResetBtn.setOnClickListener {
            keyMappingRepository.resetDefaults()
            renderKeyMapRows()
            showAppToast("Reset remote keys to defaults")
        }

        keymapCloseBtn.setOnClickListener {
            closeKeyMapper()
        }

        settingsBannerRow.setOnClickListener {
            repository.cycleBannerHideMs()
            updateSettingsToggleUi()
        }

        settingsGuideHideRow.setOnClickListener {
            repository.cycleGuideAutoHideMs()
            updateSettingsToggleUi()
            resetSidebarTimer()
        }

        settingsHideRow.setOnClickListener {
            val channel = selectedChannel
            if (channel == null) {
                showAppToast(getString(R.string.settings_hide_none))
                return@setOnClickListener
            }
            repository.setHidden(channel.id, true)
            refreshDisplayedChannels()
            channelSettingsAdapter.notifyHiddenChanged(channel.id)
            updateSettingsToggleUi()
            val next = zapChannels().firstOrNull { it.id != channel.id }
            if (next != null) tuneToChannel(next)
            showAppToast("Hidden ${channel.displayName}")
        }

        settingsGuideEpgRow.setOnClickListener {
            repository.setGuideProgramTitlesEnabled(!repository.isGuideProgramTitlesEnabled())
            updateSettingsToggleUi()
            channelAdapter.notifyDataSetChanged()
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
                progressHandler.removeCallbacks(stuckTuningRunnable)
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
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ||
                        reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> {
                        progressHandler.postDelayed(stuckTuningRunnable, TUNING_FALLBACK_TIMEOUT_MS)
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
                if (selectedChannel?.id == channel.id && tvViewHelper.isTunedTo(channel.id)) {
                    hideSidebar()
                } else {
                    tuneToChannel(channel)
                }
            },
            showProgramTitles = { repository.isGuideProgramTitlesEnabled() },
            onChannelLongClick = { channel ->
                openChannelActionSheet(channel)
                true
            }
        )
        channelRecyclerView.layoutManager = LinearLayoutManager(this)
        channelRecyclerView.setHasFixedSize(true)
        channelRecyclerView.itemAnimator = null
        channelRecyclerView.setItemViewCacheSize(24)
        channelRecyclerView.isNestedScrollingEnabled = false
        channelRecyclerView.adapter = channelAdapter

        channelSettingsAdapter = ChannelSettingsAdapter(
            channels = emptyList(),
            scope = this,
            isHiddenChecker = { id -> repository.isHidden(id) },
            onChannelClick = { channel ->
                if (repository.isHidden(channel.id)) {
                    repository.setHidden(channel.id, false)
                    channelSettingsAdapter.notifyHiddenChanged(channel.id)
                    refreshDisplayedChannels()
                    updateSettingsToggleUi()
                    showAppToast("Restored ${channel.displayName}")
                } else {
                    openEditNumberDialog(channel)
                }
            },
            onChannelLongClick = { channel ->
                repository.setStartupMode(StartupMode.FIXED_DEFAULT)
                repository.setDefaultChannelId(channel.id)
                showAppToast(getString(R.string.default_channel_set_toast, channel.displayName))
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
        val genre = activeGenre
        val displayed = allChannels.filter { ch ->
            val matchesFavorites = !isFavoritesFilterActive || repository.isFavorite(ch.id)
            val effectiveGenre = ch.genre.ifBlank { GENRE_GENERAL }
            val matchesGenre = genre == null || effectiveGenre == genre
            matchesFavorites && matchesGenre && !repository.isHidden(ch.id)
        }
        channelAdapter.updateChannels(displayed)
        updateSidebarHeader(displayed.size)
        if (displayed.isEmpty()) {
            noChannelsText.visibility = View.VISIBLE
            noChannelsText.text = if (isFavoritesFilterActive) {
                getString(R.string.no_favorites_message)
            } else {
                getString(R.string.no_channels_message)
            }
        } else {
            noChannelsText.visibility = View.GONE
        }
    }

    private fun buildGenreList() {
        genreList.removeAllViews()
        val genreCounts = allChannels.map { it.genre.ifBlank { GENRE_GENERAL } }
        val realGenres = genreCounts.filter { it != GENRE_GENERAL }.distinct().sorted()
        if (realGenres.isEmpty()) {
            val emptyState = TextView(this).apply {
                text = "Categories unavailable for this lineup"
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                gravity = android.view.Gravity.CENTER
                setPadding(24, 28, 24, 28)
                isFocusable = true
                isClickable = true
                background = resources.getDrawable(
                    com.vorynlabs.vividorbit.R.drawable.item_background_selector,
                    null
                )
                setOnClickListener { openSettings() }
            }
            genreList.addView(emptyState)
            return
        }
        val hasGeneral = genreCounts.any { it == GENRE_GENERAL }
        val density = resources.displayMetrics.density
        val labels = listOf(GENRE_ALL) + realGenres + if (hasGeneral) listOf(GENRE_GENERAL) else emptyList()
        labels.forEach { label ->
            val row = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(resources.getColor(R.color.text_primary, null))
                setPadding((16 * density).toInt(), (13 * density).toInt(), (16 * density).toInt(), (13 * density).toInt())
                isFocusable = true
                isClickable = true
                background = resources.getDrawable(
                    com.vorynlabs.vividorbit.R.drawable.item_background_selector,
                    null
                )
                isSelected = if (label == GENRE_ALL) activeGenre == null else activeGenre == label
                tag = label
                setOnClickListener { applyGenreFilter(label) }
            }
            genreList.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (4 * density).toInt() }
            )
        }
    }

    private fun applyGenreFilter(label: String) {
        activeGenre = if (label == GENRE_ALL) null else label
        buildGenreList()
        refreshDisplayedChannels()
        showGuideLevel()
    }

    private fun hasGenres(): Boolean = genreList.childCount > 0

    private fun showGuideLevel(forceFocus: Boolean = true) {
        guideLevel = 1
        genreList.visibility = View.GONE
        channelRecyclerView.visibility = View.VISIBLE
        noChannelsText.visibility = if (channelAdapter.itemCount == 0) View.VISIBLE else View.GONE
        if (forceFocus) channelRecyclerView.requestFocus()
        resetSidebarTimer()
    }

    private fun showGenresLevel() {
        if (!hasGenres()) {
            openSettings()
            return
        }
        guideLevel = 2
        channelRecyclerView.visibility = View.GONE
        noChannelsText.visibility = View.GONE
        genreList.visibility = View.VISIBLE
        genreList.getChildAt(0)?.requestFocus()
        resetSidebarTimer()
    }

    private fun loadChannelData(preserveCurrentChannel: Boolean = false) {
        launch {
            progressBar.visibility = View.VISIBLE
            if (allChannels.isEmpty()) {
                val cached = repository.getCachedChannels()
                if (cached.isNotEmpty()) {
                    allChannels = cached
                    buildGenreList()
                    refreshDisplayedChannels()
                    updateSettingsToggleUi()
                    tuneToStartupChannel(preserveCurrentChannel)
                }
            }

            val fresh = repository.getChannels()
            repository.saveChannelsCache(fresh)
            allChannels = fresh
            buildGenreList()
            refreshDisplayedChannels()
            channelSettingsAdapter.updateChannels(allChannels)
            startupPickerAdapter.updateChannels(allChannels)

            progressBar.visibility = View.GONE
            updateSettingsToggleUi()
            tuneToStartupChannel(preserveCurrentChannel)
        }
    }

    private fun isWalkthroughShowing(): Boolean {
        return ::walkthroughController.isInitialized && walkthroughController.isVisible()
    }

    private fun showAppToast(message: String) {
        if (!::appToast.isInitialized) return
        appToast.text = message
        appToast.visibility = View.VISIBLE
        toastHandler.removeCallbacks(hideToastRunnable)
        toastHandler.postDelayed(hideToastRunnable, 2200)
    }

    private fun tuneToStartupChannel(preserveCurrentChannel: Boolean) {
        val visible = zapChannels()
        if (visible.isNotEmpty()) {
            val startChannel = repository.resolveStartupChannel(visible, preserveCurrentChannel, selectedChannel)
            if (startChannel != null) {
                tuneToChannel(startChannel, allowDuringWalkthrough = true)
            }
        } else {
            showSidebar()
        }
    }

    private fun updateSidebarHeader(count: Int) {
        val title = when {
            isFavoritesFilterActive -> "Favorites"
            activeGenre != null -> activeGenre ?: "Guide"
            else -> "Guide"
        }
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

        val bannerSec = (repository.getBannerHideMs() / 1000L).toString()
        settingsBannerSubtitle.text = getString(R.string.settings_banner_seconds, bannerSec)
        val guideHideMs = repository.getGuideAutoHideMs()
        settingsGuideHideSubtitle.text = if (guideHideMs <= 0L) {
            getString(R.string.settings_guide_hide_off)
        } else {
            getString(R.string.settings_guide_hide_seconds, (guideHideMs / 1000L).toString())
        }
        val hiddenCount = repository.getHiddenIds().size
        val watching = selectedChannel
        settingsHideSubtitle.text = if (watching != null) {
            "${watching.displayName} · " + getString(R.string.settings_hidden_count, hiddenCount)
        } else {
            getString(R.string.settings_hidden_count, hiddenCount)
        }
        val guideEpg = repository.isGuideProgramTitlesEnabled()
        settingsGuideEpgBadge.text = if (guideEpg) getString(R.string.toggle_on) else getString(R.string.toggle_off)
        settingsGuideEpgBadge.isSelected = guideEpg
    }

    private fun openSettings() {
        sidebarContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        updateSettingsToggleUi()
        channelSettingsAdapter.updateChannels(allChannels)
        val navs = listOf(settingsNavLineup, settingsNavRemote, settingsNavPhone, settingsNavDisplay, settingsNavAbout)
        val sections = listOf(settingsSectionLineup, settingsSectionRemote, settingsSectionPhone, settingsSectionDisplay, settingsSectionAbout)
        navs.forEach { it.isSelected = false }
        sections.forEach { it.visibility = View.GONE }
        stopPhoneSetupServer()
        settingsNavLineup.requestFocus()
        settingsAtRoot = true
    }

    private fun showSettingsSection(index: Int) {
        val navs = listOf(settingsNavLineup, settingsNavRemote, settingsNavPhone, settingsNavDisplay, settingsNavAbout)
        val sections = listOf(settingsSectionLineup, settingsSectionRemote, settingsSectionPhone, settingsSectionDisplay, settingsSectionAbout)
        navs.forEachIndexed { i, nav -> nav.isSelected = i == index }
        sections.forEachIndexed { i, section -> section.visibility = if (i == index) View.VISIBLE else View.GONE }
        settingsAtRoot = false
        if (index != 2) stopPhoneSetupServer()
        when (index) {
            0 -> {
                channelSettingsAdapter.updateChannels(allChannels)
                settingsToggleRow.requestFocus()
            }
            1 -> {
                renderKeyMapRows()
                keymapListContainer.getChildAt(0)?.requestFocus() ?: keymapResetBtn.requestFocus()
            }
            2 -> {
                bindPhoneSection()
                settingsPhoneSetupRow.requestFocus()
            }
            else -> navs[index].requestFocus()
        }
    }

    private fun closeSettings() {
        stopPhoneSetupServer()
        if (settingsAtRoot) {
            settingsContainer.visibility = View.GONE
            sidebarContainer.visibility = View.GONE
        } else {
            settingsContainer.visibility = View.GONE
            sidebarContainer.visibility = View.VISIBLE
            if (hasGenres()) {
                showGenresLevel()
            } else {
                focusChannelListAtSelected()
                showGuideLevel(forceFocus = false)
            }
        }
    }

    private fun showSettingsRoot() {
        val navs = listOf(settingsNavLineup, settingsNavRemote, settingsNavPhone, settingsNavDisplay, settingsNavAbout)
        val sections = listOf(settingsSectionLineup, settingsSectionRemote, settingsSectionPhone, settingsSectionDisplay, settingsSectionAbout)
        navs.forEach { it.isSelected = false }
        sections.forEach { it.visibility = View.GONE }
        stopPhoneSetupServer()
        settingsNavLineup.requestFocus()
        settingsAtRoot = true
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

    private fun stopPhoneSetupServer() {
        localConfigServer?.stop()
        localConfigServer = null
        currentSessionToken = ""
    }

    private fun bindPhoneSection() {
        val ip = NetworkUtils.getLocalIpAddress(this)
        if (ip.isNullOrBlank()) {
            settingsPhoneUrl.text = getString(R.string.offline_warning)
            settingsPhoneQr.setImageDrawable(null)
            return
        }
        if (currentSessionToken.isEmpty()) {
            val random = SecureRandom()
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            currentSessionToken = bytes.joinToString("") { "%02x".format(it) }
        }
        val url = "http://$ip:10230/?t=$currentSessionToken"
        settingsPhoneUrl.text = url
        val qrBitmap = QrCodeGenerator.generateQrBitmap(url, 400, 400)
        if (qrBitmap != null) settingsPhoneQr.setImageBitmap(qrBitmap)
        if (localConfigServer == null) {
            localConfigServer = LocalConfigServer(
                context = this,
                repository = repository,
                port = 10230,
                sessionToken = currentSessionToken,
                onDataChanged = {
                    runOnUiThread { loadChannelData(preserveCurrentChannel = true) }
                },
                onTuneRequested = { channelId ->
                    runOnUiThread { allChannels.find { it.id == channelId }?.let { tuneToChannel(it) } }
                }
            )
            localConfigServer?.start()
        }
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

        val url = "http://$ip:10230/?t=$currentSessionToken"
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
                port = 10230,
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

    private fun openKeyMapper() {
        showSettingsSection(1)
    }

    private fun closeKeyMapper() {
        activeMappingAction = null
        keymapHintText.text = getString(R.string.keymap_press_hint)
        renderKeyMapRows()
        keymapListContainer.post {
            keymapListContainer.getChildAt(0)?.requestFocus() ?: keymapResetBtn.requestFocus()
        }
    }

    private fun renderKeyMapRows() {
        keymapListContainer.removeAllViews()
        val density = resources.displayMetrics.density
        RemoteAction.values().forEach { action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                isFocusable = true
                isClickable = true
                background = resources.getDrawable(R.drawable.item_background_selector, null)
                setOnClickListener {
                    activeMappingAction = action
                    keymapHintText.text = getString(R.string.keymap_listening, action.title)
                    renderKeyMapRows()
                }
            }

            val label = TextView(this).apply {
                text = action.title
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val isBeingMapped = activeMappingAction == action
            val value = TextView(this).apply {
                text = if (isBeingMapped) "▶ Press Button..." else keyMappingRepository.getActionDisplayName(action).substringAfter(": ")
                setTextColor(resources.getColor(if (isBeingMapped) R.color.accent else R.color.text_secondary, null))
                textSize = 13f
            }

            row.addView(label)
            row.addView(value)
            keymapListContainer.addView(row)
        }
    }

    private fun openChannelActionSheet(channel: Channel) {
        actionSheetChannel = channel
        channelActionTitle.text = channel.displayName
        val fav = repository.isFavorite(channel.id)
        channelActionFavorite.text = if (fav) "Remove favorite" else "Add to favorites"
        channelActionCard.visibility = View.VISIBLE
        channelActionFavorite.requestFocus()
    }

    private fun closeChannelActionSheet() {
        channelActionCard.visibility = View.GONE
        actionSheetChannel = null
        focusChannelListAtSelected()
    }

    private fun applyChannelActionFavorite() {
        val channel = actionSheetChannel ?: return
        val isNowFav = repository.toggleFavorite(channel.id)
        channelAdapter.notifyFavoriteChanged(channel.id)
        showAppToast(if (isNowFav) "${channel.displayName}: favorite" else "${channel.displayName}: removed")
        closeChannelActionSheet()
        if (isFavoritesFilterActive) refreshDisplayedChannels()
    }

    private fun applyChannelActionNumber() {
        val channel = actionSheetChannel ?: return
        closeChannelActionSheet()
        openEditNumberDialog(channel)
    }

    private fun applyChannelActionStartup() {
        val channel = actionSheetChannel ?: return
        repository.setStartupMode(StartupMode.FIXED_DEFAULT)
        repository.setDefaultChannelId(channel.id)
        showAppToast(getString(R.string.default_channel_set_toast, channel.displayName))
        updateSettingsToggleUi()
        closeChannelActionSheet()
    }

    private fun openAboutUs() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_US_URL))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showAppToast(getString(R.string.about_us_no_browser))
        }
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

        editChannelLogo.visibility = View.GONE

        updateConflictIndicator(editBuffer)
        editNumberCard.visibility = View.VISIBLE
        editNumberCard.post { editSaveBtn.requestFocus() }
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
                val swappedId = repository.assignChannelNumber(channel.id, targetNumber)
                repository.setCustomNumbersEnabled(true)
                closeEditNumberDialog()
                loadChannelData(preserveCurrentChannel = true)
                settingsRecyclerView.post { settingsRecyclerView.requestFocus() }
                val swapped = swappedId?.let { id -> allChannels.find { it.id == id } }
                if (swapped != null) {
                    showAppToast(getString(R.string.number_swapped_format, channel.displayName, targetNumber, swapped.displayName, channel.displayNumber))
                } else {
                    showAppToast(getString(R.string.number_saved_format, channel.displayName, targetNumber))
                }
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
        settingsRecyclerView.post { settingsRecyclerView.requestFocus() }
        resetSidebarTimer()
    }

    private fun showChannelUnavailable() {
        progressHandler.removeCallbacks(showProgressRunnable)
        progressHandler.removeCallbacks(hideProgressFallbackRunnable)
        progressHandler.removeCallbacks(stuckTuningRunnable)
        progressBar.visibility = View.GONE
        channelUnavailableText.visibility = View.VISIBLE
    }

    private fun tuneToChannel(channel: Channel, allowDuringWalkthrough: Boolean = false) {
        if (isWalkthroughShowing() && !allowDuringWalkthrough) return
        previewChannel = null
        val current = selectedChannel
        if (current != null && current.id != channel.id) {
            previousChannelId = current.id
            repository.setPreviousChannelId(current.id)
        }

        pendingZapChannel = channel
        selectedChannel = channel
        hasRevertedToPrevious = false
        repository.setLastChannelId(channel.id)
        showBottomBanner(channel)
        channelUnavailableText.visibility = View.GONE

        channelAdapter.setCurrentChannel(channel.id)

        zapHandler.removeCallbacks(zapTuneRunnable)
        zapHandler.postDelayed(zapTuneRunnable, ZAP_DEBOUNCE_MS)
    }

    private fun performTune(channel: Channel) {
        if (tvViewHelper.isTunedTo(channel.id)) {
            return
        }

        progressHandler.removeCallbacks(showProgressRunnable)
        progressHandler.removeCallbacks(hideProgressFallbackRunnable)
        progressHandler.removeCallbacks(stuckTuningRunnable)
        progressBar.visibility = View.GONE
        tvViewHelper.tune(channel.inputId, channel.id, TvContract.buildChannelUri(channel.id))
    }

    private fun recallPreviousChannel() {
        val prevId = previousChannelId ?: repository.getPreviousChannelId().takeIf { it != -1L }
        if (prevId != null && prevId != -1L) {
            val target = allChannels.find { it.id == prevId }
            if (target != null) {
                tuneToChannel(target)
                showAppToast("Last channel: ${target.displayName}")
            }
        }
    }

    private fun showBottomBanner(channel: Channel) {
        bannerChannelNumber.text = channel.displayNumber
        bannerChannelName.text = channel.displayName
        bannerEpgLayout.visibility = View.GONE

        bannerChannelLogo.visibility = View.GONE

        pendingBannerChannel = channel
        bannerHandler.removeCallbacks(fetchBannerEpgRunnable)
        bannerHandler.postDelayed(fetchBannerEpgRunnable, EPG_FETCH_DEBOUNCE_MS)

        channelBannerCard.visibility = View.VISIBLE

        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, repository.getBannerHideMs())
    }

    private fun fetchBannerEpg(channel: Channel) {
        launch(Dispatchers.IO) {
            val (nowProgram, nextProgram) = epgRepository.getNowAndNext(channel.id)
            withContext(Dispatchers.Main) {
                val shownId = pendingBannerChannel?.id ?: selectedChannel?.id
                if (shownId != channel.id) return@withContext
                if (nowProgram != null && nowProgram.title.isNotBlank()) {
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
                } else {
                    bannerEpgLayout.visibility = View.GONE
                }
            }
        }
    }

    private fun openChannelPrograms(channel: Channel) {
        activeProgramsChannel = channel
        programsTitle.text = channel.displayName
        programsSubtitle.text = "Channel ${channel.displayNumber}"
        programsContainer.removeAllViews()
        channelProgramsCard.visibility = View.VISIBLE
        resetSidebarTimer()

        launch(Dispatchers.IO) {
            val nowPlaying = epgRepository.getNowAndNext(channel.id)
            val upcoming = epgRepository.getUpcoming(channel.id, 7)
            withContext(Dispatchers.Main) {
                if (activeProgramsChannel?.id != channel.id) return@withContext
                programsContainer.removeAllViews()
                val density = resources.displayMetrics.density
                val nowEnd = nowPlaying.first?.endTimeUtcMillis ?: 0L
                val programs = buildList {
                    nowPlaying.first?.let { add(it) }
                    addAll(upcoming.filter { it.startTimeUtcMillis >= nowEnd })
                }

                if (programs.isEmpty()) {
                    val empty = TextView(this@MainActivity).apply {
                        text = "No program information available"
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        textSize = 14f
                        setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
                    }
                    programsContainer.addView(empty)
                }

                programs.forEach { program ->
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding((14 * density).toInt(), (11 * density).toInt(), (14 * density).toInt(), (11 * density).toInt())
                        isFocusable = true
                        isClickable = true
                        background = resources.getDrawable(R.drawable.item_background_selector, null)
                        setOnClickListener { tuneChannelFromPrograms(channel) }
                    }
                    val name = TextView(this@MainActivity).apply {
                        text = program.title
                        setTextColor(resources.getColor(R.color.text_primary, null))
                        textSize = 15f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    val time = TextView(this@MainActivity).apply {
                        text = program.getFormattedTimeWindow()
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        textSize = 12f
                    }
                    row.addView(
                        name,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    )
                    row.addView(
                        time,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = (12 * density).toInt() }
                    )
                    programsContainer.addView(row)
                }

                programsContainer.getChildAt(0)?.requestFocus()
            }
        }
    }

    private fun closeChannelPrograms() {
        channelProgramsCard.visibility = View.GONE
        activeProgramsChannel = null
        guideLevel = 1
        channelRecyclerView.requestFocus()
        resetSidebarTimer()
    }

    private fun focusedGuideChannel(): Channel? {
        val focused = channelRecyclerView.focusedChild
        if (focused != null) {
            val position = channelRecyclerView.getChildAdapterPosition(focused)
            channelAdapter.channelAt(position)?.let { return it }
        }
        return selectedChannel
    }

    private fun tuneChannelFromPrograms(channel: Channel) {
        closeChannelPrograms()
        tuneToChannel(channel)
        hideSidebar()
    }

    private fun zapChannels(): List<Channel> {
        return allChannels.filter { ch ->
            !repository.isHidden(ch.id) && (!isFavoritesFilterActive || repository.isFavorite(ch.id))
        }
    }

    private fun browseBanner(direction: Int) {
        val currentList = zapChannels()
        if (currentList.isEmpty()) return
        val current = previewChannel ?: selectedChannel
        var nextIndex = 0
        if (current != null) {
            val currentIndex = currentList.indexOfFirst { it.id == current.id }
            if (currentIndex != -1) {
                nextIndex = (currentIndex + direction) % currentList.size
                if (nextIndex < 0) nextIndex += currentList.size
            }
        }
        val target = currentList[nextIndex]
        previewChannel = target
        showBottomBanner(target)
    }

    private fun navigateChannel(direction: Int, isRepeat: Boolean) {
        previewChannel = null
        val currentList = zapChannels()
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
                (::channelActionCard.isInitialized && channelActionCard.visibility == View.VISIBLE) ||
                (::editNumberCard.isInitialized && editNumberCard.visibility == View.VISIBLE) ||
                (::startupPickerCard.isInitialized && startupPickerCard.visibility == View.VISIBLE) ||
                (::qrPanelCard.isInitialized && qrPanelCard.visibility == View.VISIBLE) ||
                (::confirmActionCard.isInitialized && confirmActionCard.visibility == View.VISIBLE) ||
                (::channelProgramsCard.isInitialized && channelProgramsCard.visibility == View.VISIBLE) ||
                (::numericEntryCard.isInitialized && numericEntryCard.visibility == View.VISIBLE) ||
                (::walkthroughOverlay.isInitialized && walkthroughOverlay.visibility == View.VISIBLE)
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

        showAppToast("No channel $trimmed")
    }

    private fun resetSidebarTimer() {
        sidebarHandler.removeCallbacks(hideSidebarRunnable)
        if (::qrPanelCard.isInitialized && qrPanelCard.visibility == View.VISIBLE) return
        if (::walkthroughOverlay.isInitialized && walkthroughOverlay.visibility == View.VISIBLE) return
        if (::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE) return
        if (::editNumberCard.isInitialized && editNumberCard.visibility == View.VISIBLE) return
        if (::channelActionCard.isInitialized && channelActionCard.visibility == View.VISIBLE) return
        val hideMs = repository.getGuideAutoHideMs()
        if (hideMs > 0L && isAnyMenuVisible()) {
            sidebarHandler.postDelayed(hideSidebarRunnable, hideMs)
        }
    }

    private fun focusChannelListAtSelected() {
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
    }

    private fun showSidebar() {
        settingsContainer.visibility = View.GONE
        editNumberCard.visibility = View.GONE
        startupPickerCard.visibility = View.GONE
        qrPanelCard.visibility = View.GONE
        confirmActionCard.visibility = View.GONE
        if (::numericEntryCard.isInitialized) numericEntryCard.visibility = View.GONE
        sidebarContainer.visibility = View.VISIBLE
        focusChannelListAtSelected()
        showGuideLevel(forceFocus = false)
    }

    private fun hideSidebar() {
        if (::sidebarContainer.isInitialized) sidebarContainer.visibility = View.GONE
        if (::editNumberCard.isInitialized) editNumberCard.visibility = View.GONE
        if (::startupPickerCard.isInitialized) startupPickerCard.visibility = View.GONE
        if (::confirmActionCard.isInitialized) confirmActionCard.visibility = View.GONE
        if (::numericEntryCard.isInitialized) numericEntryCard.visibility = View.GONE
        if (::channelProgramsCard.isInitialized) channelProgramsCard.visibility = View.GONE
        if (::qrPanelCard.isInitialized) {
            qrPanelCard.visibility = View.GONE
            localConfigServer?.stop()
            localConfigServer = null
            currentSessionToken = ""
        }
        sidebarHandler.removeCallbacks(hideSidebarRunnable)
    }

    private fun showWalkthrough() {
        walkthroughController.show()
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && !isAnyMenuVisible()) {
            backLongPressHandled = true
            recallPreviousChannel()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        resetSidebarTimer()

        if (activeMappingAction != null) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeKeyMapper()
                return true
            }
            val action = activeMappingAction!!
            val conflict = keyMappingRepository.findConflict(action, keyCode)
            if (conflict != null) {
                showAppToast("Key already used by ${conflict.title}")
                return true
            }
            keyMappingRepository.setCustomKey(action, keyCode)
            showAppToast("Mapped ${keyMappingRepository.getKeyDisplayName(keyCode)} to ${action.title}")
            closeKeyMapper()
            return true
        }

        if (::channelActionCard.isInitialized && channelActionCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeChannelActionSheet()
                return true
            }
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (isWalkthroughShowing()) {
            if (walkthroughController.handleKey(keyCode)) return true
            return super.onKeyDown(keyCode, event)
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking()
            backLongPressHandled = false
            return true
        }

        if (::confirmActionCard.isInitialized && confirmActionCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeConfirmation()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (::channelProgramsCard.isInitialized && channelProgramsCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val focusedIndex = programsContainer.indexOfChild(currentFocus)
                val lastIndex = programsContainer.childCount - 1
                if (focusedIndex < 0) {
                    programsContainer.getChildAt(0)?.requestFocus()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    programsContainer.getChildAt((focusedIndex - 1).coerceAtLeast(0))?.requestFocus()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    programsContainer.getChildAt((focusedIndex + 1).coerceAtMost(lastIndex))?.requestFocus()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    closeChannelPrograms()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                currentFocus?.performClick()
                return true
            }
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
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (settingsAtRoot) {
                    closeSettings()
                } else {
                    showSettingsRoot()
                }
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        if (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) {
            val focused = currentFocus
            val onAllTab = focused == sidebarTabAll
            val onFavTab = focused == sidebarTabFavs
            if (onAllTab || onFavTab) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    setFavoritesFilter(false)
                    sidebarTabAll.requestFocus()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    setFavoritesFilter(true)
                    sidebarTabFavs.requestFocus()
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusChannelListAtSelected()
                    return true
                }
            }
            if (focused == sidebarSettingsBtn && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (isFavoritesFilterActive) sidebarTabFavs.requestFocus() else sidebarTabAll.requestFocus()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && focused != null) {
                val holder = channelRecyclerView.findContainingViewHolder(focused)
                if (holder != null && holder.bindingAdapterPosition <= 0) {
                    if (isFavoritesFilterActive) sidebarTabFavs.requestFocus() else sidebarTabAll.requestFocus()
                    return true
                }
            }
            val inChannelList = focused != null && channelRecyclerView.findContainingViewHolder(focused) != null
            if (inChannelList || genreList.visibility == View.VISIBLE) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (genreList.visibility == View.VISIBLE) {
                        openSettings()
                    } else {
                        showGenresLevel()
                    }
                    return true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (genreList.visibility == View.VISIBLE) {
                        showGuideLevel()
                    } else {
                        focusedGuideChannel()?.let { openChannelPrograms(it) }
                    }
                    return true
                }
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
        if (keyMappingRepository.matches(RemoteAction.QUICK_RECALL, keyCode)) {
            recallPreviousChannel()
            return true
        }

        // Favorite Toggle Keys
        if (keyMappingRepository.matches(RemoteAction.TOGGLE_FAVORITE, keyCode)) {
            selectedChannel?.let { ch ->
                val isNowFav = repository.toggleFavorite(ch.id)
                channelAdapter.notifyFavoriteChanged(ch.id)
                val msg = if (isNowFav) "Added to Favorites ★" else "Removed from Favorites"
                showAppToast("${ch.displayName}: $msg")
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

        if (keyMappingRepository.matches(RemoteAction.ZAP_UP, keyCode)) {
            if (!isAnyMenuVisible()) {
                browseBanner(1)
                return true
            }
        }
        if (keyMappingRepository.matches(RemoteAction.ZAP_DOWN, keyCode)) {
            if (!isAnyMenuVisible()) {
                browseBanner(-1)
                return true
            }
        }

        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            val numericEntryActive = ::numericEntryCard.isInitialized && numericEntryCard.visibility == View.VISIBLE
            if (!isAnyMenuVisible() || numericEntryActive) {
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

        if (keyMappingRepository.matches(RemoteAction.INFO_BANNER, keyCode)) {
            if (!isAnyMenuVisible()) {
                val preview = previewChannel
                if (preview != null && preview.id != selectedChannel?.id) {
                    tuneToChannel(preview)
                } else {
                    selectedChannel?.let { showBottomBanner(it) }
                }
                return true
            }
        }

        if (keyMappingRepository.matches(RemoteAction.OPEN_GUIDE, keyCode)) {
            if (::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE) {
                return true
            }
            if (!isAnyMenuVisible()) {
                showSidebar()
                return true
            } else if (::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE) {
                showGenresLevel()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backLongPressHandled) {
                backLongPressHandled = false
                return true
            }
            when {
                ::walkthroughController.isInitialized && walkthroughController.isVisible() -> {
                    // Handled in onKeyDown
                }
                ::channelProgramsCard.isInitialized && channelProgramsCard.visibility == View.VISIBLE -> closeChannelPrograms()
                ::confirmActionCard.isInitialized && confirmActionCard.visibility == View.VISIBLE -> closeConfirmation()
                ::channelActionCard.isInitialized && channelActionCard.visibility == View.VISIBLE -> closeChannelActionSheet()
                ::qrPanelCard.isInitialized && qrPanelCard.visibility == View.VISIBLE -> closePhoneSetup()
                ::startupPickerCard.isInitialized && startupPickerCard.visibility == View.VISIBLE -> closeStartupPicker()
                ::editNumberCard.isInitialized && editNumberCard.visibility == View.VISIBLE -> closeEditNumberDialog()
                ::settingsContainer.isInitialized && settingsContainer.visibility == View.VISIBLE -> { }
                ::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE && genreList.visibility == View.VISIBLE -> showGuideLevel()
                ::sidebarContainer.isInitialized && sidebarContainer.visibility == View.VISIBLE -> hideSidebar()
                ::numericEntryCard.isInitialized && numericEntryCard.visibility == View.VISIBLE -> {
                    numericHandler.removeCallbacks(tuneRunnable)
                    numericBuffer = ""
                    numericEntryCard.visibility = View.GONE
                }
                else -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000L) {
                        finish()
                    } else {
                        val prevId = previousChannelId ?: repository.getPreviousChannelId().takeIf { it != -1L }
                        if (prevId != null && prevId != selectedChannel?.id && !hasRevertedToPrevious) {
                            recallPreviousChannel()
                            hasRevertedToPrevious = true
                            lastBackPressTime = now
                            showAppToast("Press BACK again to exit")
                        } else {
                            lastBackPressTime = now
                            showAppToast("Press BACK again to exit")
                        }
                    }
                }
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
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
        try {
            unregisterReceiver(screenshotReceiver)
        } catch (e: Exception) { }
        super.onDestroy()
    }
}
