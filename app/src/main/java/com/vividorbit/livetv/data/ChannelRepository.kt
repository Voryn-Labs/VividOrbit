package com.vividorbit.livetv.data

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChannelRepository(private val context: Context) {

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
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        
        // Sort channels by display number naturally
        channels.sortBy {
            it.displayNumber.toIntOrNull() ?: Int.MAX_VALUE
        }
        channels
    }

    suspend fun getCategories(channels: List<Channel>): List<String> = withContext(Dispatchers.Default) {
        val categories = mutableListOf<String>()
        categories.add("All Channels")
        
        val inputs = channels.map { it.inputId }.distinct()
        for (input in inputs) {
            if (input.isNotEmpty()) {
                val cleanName = cleanInputName(input)
                categories.add(cleanName)
            }
        }
        categories
    }

    fun cleanInputName(inputId: String): String {
        val part = inputId.substringAfterLast('.')
        var name = part.substringBefore('/')
        if (name.endsWith("Service")) {
            name = name.removeSuffix("Service")
        }
        if (name.endsWith("Input")) {
            name = name.removeSuffix("Input")
        }
        if (name.endsWith("Tv")) {
            name = name.removeSuffix("Tv")
        }
        return name.ifEmpty { "External Source" }
    }
}
