package com.vividorbit.livetv.player

import android.media.tv.TvTrackInfo
import android.media.tv.TvView
import android.net.Uri
import android.util.Log

class TvViewHelper(
    private val tvView: TvView,
    private val onVideoAvailable: () -> Unit,
    private val onVideoUnavailable: (reason: Int) -> Unit
) {
    companion object {
        private const val TAG = "TvViewHelper"
    }

    private var currentInputId: String? = null
    private var lastSelectedAudioTrackId: String? = null

    init {
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                super.onVideoAvailable(inputId)
                Log.d(TAG, "Video available on input: $inputId")
                currentInputId = inputId
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

    fun tune(inputId: String, channelUri: Uri) {
        try {
            Log.d(TAG, "Tuning input: $inputId, uri: $channelUri")
            currentInputId = inputId
            lastSelectedAudioTrackId = null
            tvView.tune(inputId, channelUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error tuning: ${e.message}", e)
        }
    }

    private fun ensureAudioTrackSelected() {
        val audioTracks = getAudioTracks()
        if (audioTracks.isNotEmpty()) {
            val selected = getSelectedAudioTrack()
            if (selected == null) {
                val targetTrack = lastSelectedAudioTrackId ?: audioTracks[0].id
                Log.d(TAG, "No audio track active. Auto-selecting track: $targetTrack")
                selectAudioTrack(targetTrack)
            }
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
        }
    }
}
