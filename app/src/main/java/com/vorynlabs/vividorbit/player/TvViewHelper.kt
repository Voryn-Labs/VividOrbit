package com.vorynlabs.vividorbit.player

import android.media.tv.TvContentRating
import android.media.tv.TvTrackInfo
import android.media.tv.TvView
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class TvViewHelper(
    private val tvView: TvView,
    private val onVideoAvailable: () -> Unit,
    private val onVideoUnavailable: (reason: Int) -> Unit,
    private val onInputError: () -> Unit
) {
    companion object {
        private const val TAG = "TvViewHelper"
        private const val AUDIO_WATCHDOG_INTERVAL_MS = 3000L
        private const val MAX_WATCHDOG_ATTEMPTS = 3
    }

    private var currentInputId: String? = null
    private var currentChannelId: Long? = null
    private var lastSelectedAudioTrackId: String? = null
    private var hasReceivedFirstFrame = false
    private var watchdogAttempts = 0

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val audioWatchdogRunnable = object : Runnable {
        override fun run() {
            val isSelected = ensureAudioTrackSelected()
            if (!isSelected && watchdogAttempts < MAX_WATCHDOG_ATTEMPTS) {
                watchdogAttempts++
                watchdogHandler.postDelayed(this, AUDIO_WATCHDOG_INTERVAL_MS)
            }
        }
    }

    init {
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                super.onVideoAvailable(inputId)
                Log.d(TAG, "Video available on input: $inputId")
                currentInputId = inputId
                hasReceivedFirstFrame = true
                ensureAudioTrackSelected()
                onVideoAvailable()
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                super.onVideoUnavailable(inputId, reason)
                Log.w(TAG, "Video unavailable on input: $inputId, reason: $reason")
                onVideoUnavailable(reason)
            }

            override fun onTracksChanged(inputId: String, tracks: MutableList<TvTrackInfo>?) {
                super.onTracksChanged(inputId, tracks)
                Log.d(TAG, "Tracks changed on input $inputId, track count: ${tracks?.size ?: 0}")
                val isSelected = ensureAudioTrackSelected()
                if (!isSelected) {
                    watchdogAttempts = 0
                    watchdogHandler.removeCallbacks(audioWatchdogRunnable)
                    watchdogHandler.postDelayed(audioWatchdogRunnable, AUDIO_WATCHDOG_INTERVAL_MS)
                }
            }

            override fun onTrackSelected(inputId: String, type: Int, trackId: String?) {
                super.onTrackSelected(inputId, type, trackId)
                if (type == TvTrackInfo.TYPE_AUDIO && trackId != null) {
                    Log.d(TAG, "Audio track selected on input $inputId: $trackId")
                    lastSelectedAudioTrackId = trackId
                    watchdogHandler.removeCallbacks(audioWatchdogRunnable)
                }
            }

            override fun onConnectionFailed(inputId: String) {
                super.onConnectionFailed(inputId)
                Log.e(TAG, "Connection failed for input: $inputId")
                onInputError()
            }

            override fun onDisconnected(inputId: String) {
                super.onDisconnected(inputId)
                Log.w(TAG, "Disconnected from input: $inputId")
                onInputError()
            }

            override fun onContentBlocked(inputId: String, rating: TvContentRating) {
                super.onContentBlocked(inputId, rating)
                Log.w(TAG, "Content blocked on input $inputId, rating: $rating")
                onInputError()
            }
        })
    }

    fun tune(inputId: String, channelId: Long, channelUri: Uri) {
        try {
            Log.d(TAG, "Tuning input: $inputId, channelId: $channelId, uri: $channelUri")
            currentInputId = inputId
            currentChannelId = channelId
            lastSelectedAudioTrackId = null
            hasReceivedFirstFrame = false
            watchdogAttempts = 0
            tvView.tune(inputId, channelUri)

            watchdogHandler.removeCallbacks(audioWatchdogRunnable)
            watchdogHandler.postDelayed(audioWatchdogRunnable, AUDIO_WATCHDOG_INTERVAL_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error tuning: ${e.message}", e)
            onInputError()
        }
    }

    fun isTunedTo(channelId: Long): Boolean = currentChannelId == channelId

    fun hasStartedPlayback(): Boolean = hasReceivedFirstFrame

    private fun ensureAudioTrackSelected(): Boolean {
        val audioTracks = getAudioTracks()
        if (audioTracks.isEmpty()) return false

        val selected = getSelectedAudioTrack()
        val selectedStillValid = selected != null && audioTracks.any { it.id == selected }

        if (selectedStillValid) {
            watchdogHandler.removeCallbacks(audioWatchdogRunnable)
            return true
        }

        val preferredStillValid = lastSelectedAudioTrackId?.takeIf { preferred ->
            audioTracks.any { it.id == preferred }
        }
        val targetTrack = preferredStillValid ?: audioTracks[0].id
        Log.d(TAG, "No valid audio track selected (was: $selected). Selecting: $targetTrack")
        selectAudioTrack(targetTrack)
        return true
    }

    private fun getAudioTracks(): List<TvTrackInfo> {
        return try {
            tvView.getTracks(TvTrackInfo.TYPE_AUDIO) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio tracks: ${e.message}", e)
            emptyList()
        }
    }

    private fun selectAudioTrack(trackId: String) {
        try {
            lastSelectedAudioTrackId = trackId
            tvView.selectTrack(TvTrackInfo.TYPE_AUDIO, trackId)
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting audio track: ${e.message}", e)
        }
    }

    private fun getSelectedAudioTrack(): String? {
        return try {
            tvView.getSelectedTrack(TvTrackInfo.TYPE_AUDIO)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting selected audio track: ${e.message}", e)
            null
        }
    }

    fun cleanup() {
        watchdogHandler.removeCallbacks(audioWatchdogRunnable)
        try {
            tvView.setCallback(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing TvView callback: ${e.message}", e)
        }
    }

    fun reset() {
        try {
            tvView.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting TvView: ${e.message}", e)
        } finally {
            currentChannelId = null
            hasReceivedFirstFrame = false
            watchdogHandler.removeCallbacks(audioWatchdogRunnable)
        }
    }
}
