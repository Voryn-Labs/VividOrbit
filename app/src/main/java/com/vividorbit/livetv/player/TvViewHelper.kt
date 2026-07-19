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

    init {
        tvView.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                super.onVideoAvailable(inputId)
                Log.d(TAG, "Video available on input: $inputId")
                onVideoAvailable()
            }

            override fun onVideoUnavailable(inputId: String, reason: Int) {
                super.onVideoUnavailable(inputId, reason)
                Log.w(TAG, "Video unavailable on input: $inputId, reason: $reason")
                onVideoUnavailable(reason)
            }
        })
    }

    fun tune(inputId: String, channelUri: Uri) {
        try {
            Log.d(TAG, "Tuning input: $inputId, uri: $channelUri")
            tvView.tune(inputId, channelUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error tuning: ${e.message}", e)
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

    fun reset() {
        try {
            tvView.reset()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting TvView: ${e.message}", e)
        }
    }
}
