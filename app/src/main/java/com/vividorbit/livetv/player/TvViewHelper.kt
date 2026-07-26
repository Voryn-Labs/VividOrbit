package com.vividorbit.livetv.player

import android.media.tv.TvTrackInfo
import android.media.tv.TvView
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class TvViewHelper(
    private val tvView: TvView,
    private val onVideoAvailable: () -> Unit,
    private val onVideoUnavailable: (reason: Int) -> Unit
) {
    companion object {
        private const val TAG = "TvViewHelper"

        // How often we proactively re-validate that the audio track we think
        // is selected is still actually present in the input's current track
        // list. Some tuner HALs silently renegotiate/regenerate track ids
        // when audio glitches, which leaves the "selected" id stale but
        // non-null - a plain null-check misses that case entirely.
        private const val AUDIO_WATCHDOG_INTERVAL_MS = 4000L
    }

    private var currentInputId: String? = null
    private var currentChannelId: Long? = null
    private var lastSelectedAudioTrackId: String? = null
    private var hasReceivedFirstFrame = false

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val audioWatchdogRunnable = object : Runnable {
        override fun run() {
            ensureAudioTrackSelected()
            watchdogHandler.postDelayed(this, AUDIO_WATCHDOG_INTERVAL_MS)
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
                ensureAudioTrackSelected()
            }

            override fun onTrackSelected(inputId: String, type: Int, trackId: String?) {
                super.onTrackSelected(inputId, type, trackId)
                if (type == TvTrackInfo.TYPE_AUDIO && trackId != null) {
                    Log.d(TAG, "Audio track selected on input $inputId: $trackId")
                    lastSelectedAudioTrackId = trackId
                }
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
            tvView.tune(inputId, channelUri)

            watchdogHandler.removeCallbacks(audioWatchdogRunnable)
            watchdogHandler.postDelayed(audioWatchdogRunnable, AUDIO_WATCHDOG_INTERVAL_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error tuning: ${e.message}", e)
        }
    }

    /** True if [channelId] is the channel we last issued a tune() for (whether or not it has produced a frame yet). */
    fun isTunedTo(channelId: Long): Boolean = currentChannelId == channelId

    /** True once the current tune has produced at least one video frame. */
    fun hasStartedPlayback(): Boolean = hasReceivedFirstFrame

    private fun ensureAudioTrackSelected() {
        val audioTracks = getAudioTracks()
        if (audioTracks.isEmpty()) return

        val selected = getSelectedAudioTrack()
        val selectedStillValid = selected != null && audioTracks.any { it.id == selected }

        if (!selectedStillValid) {
            val preferredStillValid = lastSelectedAudioTrackId?.takeIf { preferred ->
                audioTracks.any { it.id == preferred }
            }
            val targetTrack = preferredStillValid ?: audioTracks[0].id
            Log.d(TAG, "No valid audio track selected (was: $selected). Selecting: $targetTrack")
            selectAudioTrack(targetTrack)
        }
    }

    fun recoverAudio() {
        try {
            val audioTracks = getAudioTracks()
            if (audioTracks.isNotEmpty()) {
                val currentTrack = getSelectedAudioTrack() ?: audioTracks[0].id
                Log.d(TAG, "Recovering audio by re-selecting track: $currentTrack")
                // Toggle off and back on to re-initialize hardware audio sink
                tvView.selectTrack(TvTrackInfo.TYPE_AUDIO, null)
                tvView.postDelayed({
                    try {
                        tvView.selectTrack(TvTrackInfo.TYPE_AUDIO, currentTrack)
                        lastSelectedAudioTrackId = currentTrack
                    } catch (e: Exception) {
                        Log.e(TAG, "Error re-selecting audio track during recovery: ${e.message}", e)
                    }
                }, 150)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering audio: ${e.message}", e)
        }
    }

    fun getAudioTracks(): List<TvTrackInfo> {
        return try {
            tvView.getTracks(TvTrackInfo.TYPE_AUDIO) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio tracks: ${e.message}", e)
            emptyList()
        }
    }

    fun selectAudioTrack(trackId: String) {
        try {
            lastSelectedAudioTrackId = trackId
            tvView.selectTrack(TvTrackInfo.TYPE_AUDIO, trackId)
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting audio track: ${e.message}", e)
        }
    }

    fun getSelectedAudioTrack(): String? {
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
            currentChannelId = null
            tvView.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting TvView: ${e.message}", e)
        }
    }
}
