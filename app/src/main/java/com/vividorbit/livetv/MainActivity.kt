package com.vividorbit.livetv

import android.app.Activity
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
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
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.vividorbit.livetv.ui.ChannelAdapter
import com.vividorbit.livetv.ui.ChannelLogoLoader
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
    private lateinit var channelRecyclerView: RecyclerView
    private lateinit var numericEntryCard: CardView
    private lateinit var numericEntryText: TextView
    private lateinit var noChannelsText: TextView

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

    // Debounces rapid channel-up/down key repeats so holding the button
    // doesn't flood the tuner with real tune() calls - only the channel the
    // user finally settles on gets tuned. Banner/selection still update
    // instantly on every press so navigation still feels responsive.
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
        private const val ZAP_DEBOUNCE_MS = 300L

        // These are intentionally generous - the guide is designed for
        // viewers who read and react more slowly, so nothing should vanish
        // or time out before they've had a real chance to see it.
        private const val SIDEBAR_AUTO_HIDE_MS = 20000L
        private const val BANNER_AUTO_HIDE_MS = 6000L
        private const val NUMERIC_ENTRY_TIMEOUT_MS = 3000L
        private const val NUMERIC_ENTRY_MAX_DIGITS = 4

        // How long to wait after a tune (or after a channel that's already
        // playing starts re-buffering) before showing the loading indicator,
        // and how long after *that* to give up and show "Channel
        // Unavailable" if it never resolves. The exact right values here
        // depend on how long this specific tuner hardware actually takes to
        // lock a signal in practice - these are reasonable starting points,
        // not verified against real hardware.
        private const val TUNING_SHOW_DELAY_MS = 400L
        private const val TUNING_FALLBACK_TIMEOUT_MS = 8000L
        private const val SUSTAINED_BUFFERING_THRESHOLD_MS = 3000L

        // Persists only the last-watched channel. Since the app now fully
        // exits whenever it's left (see onStop()), this is what makes the
        // next launch resume where you left off instead of always
        // restarting on the first channel.
        private const val PREFS_NAME = "vividorbit_prefs"
        private const val PREF_LAST_CHANNEL_ID = "last_channel_id"
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = Job()
        setContentView(R.layout.activity_main)

        // Initialize MediaSession to prevent sleep
        mediaSession = MediaSession(this, "VividOrbitLiveTv")
        mediaSession.isActive = true

        // Initialize UI Elements
        tvView = findViewById(R.id.tv_view)
        progressBar = findViewById(R.id.progress_bar)
        channelUnavailableText = findViewById(R.id.channel_unavailable_text)
        sidebarContainer = findViewById(R.id.sidebar_container)
        sidebarHeader = findViewById(R.id.sidebar_header)
        channelRecyclerView = findViewById(R.id.channel_recycler_view)
        numericEntryCard = findViewById(R.id.numeric_entry_card)
        numericEntryText = findViewById(R.id.numeric_entry_text)
        noChannelsText = findViewById(R.id.no_channels_text)
        channelBannerCard = findViewById(R.id.channel_banner_card)
        bannerChannelNumber = findViewById(R.id.banner_channel_number)
        bannerChannelLogo = findViewById(R.id.banner_channel_logo)
        bannerChannelName = findViewById(R.id.banner_channel_name)

        // Initialize helpers
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
                // Cancelled unconditionally up front (previously only
                // showProgressRunnable was cleared here) - otherwise a
                // second VIDEO_UNAVAILABLE_REASON_TUNING callback in the same
                // tune sequence could stack a second hideProgressFallback
                // call on top of the first, hiding the spinner prematurely.
                progressHandler.removeCallbacks(showProgressRunnable)
                progressHandler.removeCallbacks(hideProgressFallbackRunnable)

                val stateBuilder = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
                mediaSession.setPlaybackState(stateBuilder.build())

                when {
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> {
                        // Genuine channel change in progress - show the loading indicator.
                        progressHandler.postDelayed(showProgressRunnable, TUNING_SHOW_DELAY_MS)
                        progressHandler.postDelayed(hideProgressFallbackRunnable, TUNING_FALLBACK_TIMEOUT_MS)
                    }
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING && !tvViewHelper.hasStartedPlayback() -> {
                        // Still buffering the very first frame after a tune - show the indicator.
                        progressHandler.postDelayed(showProgressRunnable, TUNING_SHOW_DELAY_MS)
                        progressHandler.postDelayed(hideProgressFallbackRunnable, TUNING_FALLBACK_TIMEOUT_MS)
                    }
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> {
                        // Re-buffering on a channel that's already played
                        // before. A brief blip shouldn't throw a loading
                        // overlay over an already-playing picture - but a
                        // genuine, sustained signal loss still needs to be
                        // visible, not silently invisible forever (this
                        // previously never showed anything at all once a
                        // channel had played once, however long the loss).
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
                // Hard failures previously had no handling at all: a lost
                // connection to the tuner input service, a tuner-level
                // error, or a content-rating block would just leave the app
                // sitting on a frozen frame with zero feedback.
                val stateBuilder = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
                mediaSession.setPlaybackState(stateBuilder.build())
                showChannelUnavailable()
            }
        )
        repository = ChannelRepository(this)

        // Set up recyclerview layout
        channelRecyclerView.layoutManager = LinearLayoutManager(this)
        channelRecyclerView.setHasFixedSize(true)
        // Default add/change animations were the main cause of the guide
        // "jumping then sliding" when it opens - a changed-item cross-fade
        // animation was firing at the same moment the list scrolled to the
        // current channel, with no stable prior layout to animate from.
        channelRecyclerView.itemAnimator = null
        applyCenteringPadding(channelRecyclerView)

        // Check for TV EPG Provider permissions at runtime
        val missing = REQUIRED_PERMISSIONS.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            loadChannelData()
        }
    }

    private fun loadChannelData() {
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

            progressBar.visibility = View.GONE
            updateSidebarHeader(allChannels.size)

            // Resume on whichever channel was last playing, falling back to
            // the first available channel if it no longer exists (e.g.
            // removed by a fresh channel scan).
            if (allChannels.isNotEmpty()) {
                val lastChannelId = prefs.getLong(PREF_LAST_CHANNEL_ID, -1L)
                val startChannel = allChannels.find { it.id == lastChannelId } ?: allChannels[0]
                tuneToChannel(startChannel)
                // Deliberately NOT opening the guide here - the app should
                // launch straight into live TV with just the channel banner
                // (which hides itself after BANNER_AUTO_HIDE_MS). The guide
                // only opens when the user actually asks for it.
            } else {
                // Nothing to play - show the guide so the "no channels found"
                // message is visible instead of a blank screen with no
                // explanation.
                showSidebar()
            }
        }
    }

    private fun updateSidebarHeader(count: Int) {
        sidebarHeader.text = getString(R.string.sidebar_header_format, count)
        noChannelsText.visibility = if (count == 0) View.VISIBLE else View.GONE
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
            // Already tuned to (or already tuning to) this channel - skip the
            // redundant real tune, which would otherwise reset audio track
            // selection and can cause an audible/visible blip for no reason.
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
            // Decode off the main thread - ImageView.setImageURI() decodes the
            // full-resolution image synchronously on whatever thread calls it,
            // which was stalling the UI on every single channel change.
            launch(Dispatchers.IO) {
                val bitmap = ChannelLogoLoader.loadAndCache(this@MainActivity, channel.id, channel.logoUri)
                withContext(Dispatchers.Main) {
                    // Only apply if the user hasn't already zapped past this channel.
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

    private fun navigateChannel(direction: Int) {
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

        // Instant feedback on every press: update the selection and banner
        // right away so navigation still feels responsive.
        pendingZapChannel = targetChannel
        selectedChannel = targetChannel
        showBottomBanner(targetChannel)

        // Debounce the actual hardware tune so holding/rapidly pressing
        // channel up/down doesn't queue up a real tune() per key-repeat
        // event - only the channel the user settles on gets tuned.
        zapHandler.removeCallbacks(zapTuneRunnable)
        zapHandler.postDelayed(zapTuneRunnable, ZAP_DEBOUNCE_MS)
    }

    private fun isAnyMenuVisible(): Boolean {
        return sidebarContainer.visibility == View.VISIBLE
    }

    /**
     * Gives a RecyclerView generous top/bottom padding (half its own height)
     * plus clipToPadding = false, so that even the first or last row has
     * somewhere to scroll to in order to reach the vertical center - without
     * this, an edge row physically cannot be centered since there's no real
     * content above/below it to scroll through. Combined with
     * View.centerInParentOnFocus() on each row, this is what makes the whole
     * list scroll behind a fixed, centered highlight.
     *
     * Deferred until the RecyclerView actually has a real height, since
     * these panels start as View.GONE and have no layout at all until first
     * shown.
     */
    private fun applyCenteringPadding(recyclerView: RecyclerView) {
        recyclerView.clipToPadding = false
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val height = recyclerView.height
                if (height > 0) {
                    recyclerView.setPadding(recyclerView.paddingLeft, height / 2, recyclerView.paddingRight, height / 2)
                    recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
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
        sidebarContainer.visibility = View.VISIBLE

        // Scroll to the currently playing channel *before* requesting focus -
        // requesting focus first would grab whatever row happened to already
        // be laid out (usually wherever the list was last left scrolled to),
        // and then scrolling away from it left the focus highlight on the
        // wrong (or an off-screen) row.
        val activeChannel = selectedChannel
        val index = if (activeChannel != null) {
            allChannels.indexOfFirst { it.id == activeChannel.id }
        } else {
            -1
        }

        if (index != -1) {
            channelRecyclerView.scrollToPosition(index)
            channelRecyclerView.post {
                val holder = channelRecyclerView.findViewHolderForAdapterPosition(index)
                if (holder != null) {
                    holder.itemView.requestFocus()
                } else {
                    channelRecyclerView.requestFocus()
                }
            }
        } else {
            channelRecyclerView.requestFocus()
        }

        resetSidebarTimer()
    }

    private fun hideSidebar() {
        sidebarContainer.visibility = View.GONE
        sidebarHandler.removeCallbacks(hideSidebarRunnable)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        resetSidebarTimer()

        if (numericEntryCard.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                numericHandler.removeCallbacks(tuneRunnable)
                tuneRunnable.run()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Previously fell through to the system default, which -
                // since nothing else claimed it - exited the whole app
                // instead of just dismissing the number entry.
                numericHandler.removeCallbacks(tuneRunnable)
                numericBuffer = ""
                numericEntryCard.visibility = View.GONE
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
            navigateChannel(1)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            navigateChannel(-1)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (!isAnyMenuVisible()) {
                navigateChannel(-1)
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!isAnyMenuVisible()) {
                navigateChannel(1)
                return true
            }
        }

        // Intercept Keypad Numbers for Natural entry tuning - only when no
        // other overlay is open, matching how arrow-key zapping behaves.
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
        // The user has left the app entirely - backgrounded it, switched to
        // another app, or the TV went to sleep. Rather than lingering in the
        // background holding the tuner, MediaSession, and other resources,
        // fully exit here. onDestroy() (triggered by finish()) does the
        // actual teardown; the next launch resumes on the last channel via
        // the preferences saved in tuneToChannel().
        //
        // Guarded against configuration changes (e.g. a locale/density
        // change) since those already trigger their own destroy+recreate
        // cycle - we don't want to also treat that as "the user left".
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
                // Previously failed silently here, leaving a permanently
                // blank screen with no indication of what went wrong.
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
        // Order matters here: detach the callback first, then reset. If we
        // reset() first while still connected, a resulting callback (e.g.
        // onVideoUnavailable) could fire and try to touch mediaSession
        // (already released above) or views that are mid-teardown.
        tvViewHelper.cleanup()
        tvViewHelper.reset()
    }
}
