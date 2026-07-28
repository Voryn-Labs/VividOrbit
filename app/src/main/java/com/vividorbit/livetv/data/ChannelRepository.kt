package com.vividorbit.livetv.data

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChannelRepository(private val context: Context) {

    companion object {
        private const val TAG = "ChannelRepository"
    }

    suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        val projection = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_INPUT_ID
        )

        var cursor: Cursor? = null
        try {
            val tunerInputId = "com.droidlogic.dtvkit.inputsource/.DtvkitTvInput/HW19"
            val queryUri = TvContract.buildChannelsUriForInput(tunerInputId)
            cursor = context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                null
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(TvContract.Channels._ID)
                val numberIndex = it.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER)
                val nameIndex = it.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME)
                val inputIdIndex = it.getColumnIndex(TvContract.Channels.COLUMN_INPUT_ID)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getLong(idIndex) else -1L
                    val number = if (numberIndex != -1) it.getString(numberIndex) ?: "" else ""
                    val name = if (nameIndex != -1) it.getString(nameIndex) ?: "" else ""
                    val inputId = if (inputIdIndex != -1) it.getString(inputIdIndex) ?: "" else ""
                    val logoUri = TvContract.buildChannelLogoUri(id)

                    channels.add(Channel(id, number, name, inputId, logoUri))
                }
            }
        } catch (e: CancellationException) {
            // A cancelled coroutine (e.g. the activity was torn down mid-load)
            // is expected, cooperative behavior, not an error - it must be
            // rethrown so the coroutine machinery actually completes the
            // cancellation, and shouldn't be logged as if something broke.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channels: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        // Sort numerically-numbered channels first (in numeric order), then
        // any alphanumeric ones after (alphabetically among themselves,
        // rather than left in whatever arbitrary order the cursor returned
        // them in).
        channels.sortWith(
            compareBy(
                { it.displayNumber.toIntOrNull() == null },
                { it.displayNumber.toIntOrNull() ?: Int.MAX_VALUE },
                { it.displayNumber }
            )
        )
        channels
    }
}
