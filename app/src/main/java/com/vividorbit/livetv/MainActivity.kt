package com.vividorbit.livetv

import android.app.Activity
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vividorbit.livetv.data.Channel
import com.vividorbit.livetv.data.ChannelRepository
import com.vividorbit.livetv.player.TvViewHelper
import com.vividorbit.livetv.ui.CategoryAdapter
import com.vividorbit.livetv.ui.ChannelAdapter
import com.vividorbit.livetv.ui.TrackAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    private lateinit var categoryContainer: View
    private lateinit var categoryRecyclerView: RecyclerView
    private lateinit var trackSelectorCard: CardView
    private lateinit var trackRecyclerView: RecyclerView
    private lateinit var numericEntryCard: CardView
    private lateinit var numericEntryText: TextView

    private lateinit var channelBannerCard: CardView
    private lateinit var bannerChannelNumber: TextView
    private lateinit var bannerChannelLogo: ImageView
    private lateinit var bannerChannelName: TextView

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable {
        channelBannerCard.visibility = View.GONE
    }

    private val progressHandler = Handler(Looper.getMainLooper())
    private val showProgressRunnable = Runnable {
        progressBar.visibility = View.VISIBLE
    }

    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private var allChannels: List<Channel> = emptyList()
    private var filteredChannels: List<Channel> = emptyList()
    private var categories: List<String> = emptyList()
    private var currentCategory: String = "All Channels"
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        tvView = findViewById(R.id.tv_view)
        progressBar = findViewById(R.id.progress_bar)
        channelUnavailableText = findViewById(R.id.channel_unavailable_text)
        sidebarContainer = findViewById(R.id.sidebar_container)
        sidebarHeader = findViewById(R.id.sidebar_header)
        channelRecyclerView = findViewById(R.id.channel_recycler_view)
        categoryContainer = findViewById(R.id.category_container)
        categoryRecyclerView = findViewById(R.id.category_recycler_view)
        trackSelectorCard = findViewById(R.id.track_selector_card)
        trackRecyclerView = findViewById(R.id.track_recycler_view)
        numericEntryCard = findViewById(R.id.numeric_entry_card)
        numericEntryText = findViewById(R.id.numeric_entry_text)
        channelBannerCard = findViewById(R.id.channel_banner_card)
        bannerChannelNumber = findViewById(R.id.banner_channel_number)
        bannerChannelLogo = findViewById(R.id.banner_channel_logo)
        bannerChannelName = findViewById(R.id.banner_channel_name)

        // Initialize helpers
        tvViewHelper = TvViewHelper(
            tvView = tvView,
            onVideoAvailable = {
                progressHandler.removeCallbacks(showProgressRunnable)
                progressBar.visibility = View.GONE
                channelUnavailableText.visibility = View.GONE
            },
            onVideoUnavailable = { reason ->
                progressHandler.removeCallbacks(showProgressRunnable)
                if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING) {
                    progressHandler.postDelayed(showProgressRunnable, 400)
                } else {
                    progressBar.visibility = View.GONE
                    channelUnavailableText.visibility = View.VISIBLE
                }
            }
        )
        repository = ChannelRepository(this)

        // Set up recyclerview layouts
        channelRecyclerView.layoutManager = LinearLayoutManager(this)
        categoryRecyclerView.layoutManager = LinearLayoutManager(this)
        trackRecyclerView.layoutManager = LinearLayoutManager(this)

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
            filteredChannels = allChannels
            categories = repository.getCategories(allChannels)

            // Setup Adapters
            channelAdapter = ChannelAdapter(filteredChannels, this@MainActivity) { channel ->
                tuneToChannel(channel)
                hideSidebar()
            }
            channelRecyclerView.adapter = channelAdapter

            categoryAdapter = CategoryAdapter(
                categories = categories,
                onCategoryFocused = { category ->
                    filterChannels(category)
                },
                onCategoryClick = { category ->
                    filterChannels(category)
                    categoryContainer.visibility = View.GONE
                    channelRecyclerView.requestFocus()
                }
            )
            categoryRecyclerView.adapter = categoryAdapter

            progressBar.visibility = View.GONE

            // Auto-tune first channel if available
            if (allChannels.isNotEmpty()) {
                tuneToChannel(allChannels[0])
            }

            // Show sidebar on launch so the interface is visible immediately
            showSidebar()
        }
    }

    private fun filterChannels(category: String) {
        currentCategory = category
        sidebarHeader.text = category
        filteredChannels = if (category == "All Channels") {
            allChannels
        } else {
            allChannels.filter { repository.cleanInputName(it.inputId) == category }
        }
        channelAdapter.updateChannels(filteredChannels)
    }

    private fun tuneToChannel(channel: Channel) {
        selectedChannel = channel
        showBottomBanner(channel)
        channelUnavailableText.visibility = View.GONE

        progressHandler.removeCallbacks(showProgressRunnable)
        progressHandler.postDelayed(showProgressRunnable, 400)
        tvViewHelper.tune(channel.inputId, TvContract.buildChannelUri(channel.id))
    }

    private fun showBottomBanner(channel: Channel) {
        bannerChannelNumber.text = channel.displayNumber
        bannerChannelName.text = channel.displayName

        bannerChannelLogo.setImageURI(null)
        try {
            bannerChannelLogo.setImageURI(channel.logoUri)
        } catch (e: Exception) {
            bannerChannelLogo.setImageResource(android.R.drawable.ic_menu_slideshow)
        }

        channelBannerCard.visibility = View.VISIBLE

        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, 3000)
    }

    private fun navigateChannel(direction: Int) {
        val listToNavigate = if (filteredChannels.isNotEmpty()) filteredChannels else allChannels
        if (listToNavigate.isEmpty()) return

        val current = selectedChannel
        var nextIndex = 0
        if (current != null) {
            val currentIndex = listToNavigate.indexOfFirst { it.id == current.id }
            if (currentIndex != -1) {
                nextIndex = (currentIndex + direction) % listToNavigate.size
                if (nextIndex < 0) {
                    nextIndex += listToNavigate.size
                }
            }
        }
        val targetChannel = listToNavigate[nextIndex]
        tuneToChannel(targetChannel)
    }

    private fun isAnyMenuVisible(): Boolean {
        return sidebarContainer.visibility == View.VISIBLE ||
                categoryContainer.visibility == View.VISIBLE ||
                trackSelectorCard.visibility == View.VISIBLE
    }

    private fun tuneToChannelNumber(number: String) {
        val channel = allChannels.find { it.displayNumber == number }
        if (channel != null) {
            tuneToChannel(channel)
        }
    }

    private fun showSidebar() {
        sidebarContainer.visibility = View.VISIBLE
        channelRecyclerView.requestFocus()
        // If possible, focus the currently playing channel
        val activeChannel = selectedChannel
        if (activeChannel != null) {
            val index = filteredChannels.indexOfFirst { it.id == activeChannel.id }
            if (index != -1) {
                channelRecyclerView.scrollToPosition(index)
            }
        }
    }

    private fun hideSidebar() {
        sidebarContainer.visibility = View.GONE
        categoryContainer.visibility = View.GONE
    }

    private fun showAudioTrackSelector() {
        val tracks = tvViewHelper.getAudioTracks()
        if (tracks.isEmpty()) return

        val currentTrackId = tvViewHelper.getSelectedAudioTrack()
        val adapter = TrackAdapter(tracks, currentTrackId) { track ->
            tvViewHelper.selectAudioTrack(track.id)
            trackSelectorCard.visibility = View.GONE
        }
        trackRecyclerView.adapter = adapter
        trackSelectorCard.visibility = View.VISIBLE
        trackRecyclerView.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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

        // Intercept Keypad Numbers for Natural entry tuning
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
            numericHandler.removeCallbacks(tuneRunnable)
            numericBuffer += digit
            numericEntryText.text = numericBuffer
            numericEntryCard.visibility = View.VISIBLE
            numericHandler.postDelayed(tuneRunnable, 1500)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (trackSelectorCard.visibility == View.VISIBLE) {
                trackSelectorCard.visibility = View.GONE
                return true
            }
            if (categoryContainer.visibility == View.VISIBLE) {
                categoryContainer.visibility = View.GONE
                channelRecyclerView.requestFocus()
                return true
            }
            if (sidebarContainer.visibility == View.VISIBLE) {
                hideSidebar()
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (sidebarContainer.visibility == View.VISIBLE && categoryContainer.visibility != View.VISIBLE) {
                categoryContainer.visibility = View.VISIBLE
                categoryRecyclerView.requestFocus()
                // Find and focus current category
                val catIndex = categories.indexOf(currentCategory)
                if (catIndex != -1) {
                    categoryRecyclerView.scrollToPosition(catIndex)
                }
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (categoryContainer.visibility == View.VISIBLE) {
                categoryContainer.visibility = View.GONE
                channelRecyclerView.requestFocus()
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

        // Custom or standard remote audio track key triggers
        if (keyCode == KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK || keyCode == KeyEvent.KEYCODE_PROG_RED) {
            showAudioTrackSelector()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                loadChannelData()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerHandler.removeCallbacksAndMessages(null)
        progressHandler.removeCallbacksAndMessages(null)
        numericHandler.removeCallbacksAndMessages(null)
        job.cancel()
        tvViewHelper.cleanup()
        tvViewHelper.reset()
    }
}
