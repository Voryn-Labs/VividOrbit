package com.vividorbit.livetv.data

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.media.tv.TvContract
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChannelRepository(private val context: Context) {

    companion object {
        private const val TAG = "ChannelRepository"
        private const val PREFS_NAME = "vividorbit_prefs"
        private const val PREF_USE_CUSTOM_NUMBERS = "use_custom_channel_numbers"
        private const val PREF_CUSTOM_NUMBERS_JSON = "custom_channel_numbers_json"
        private const val PREF_BACKUP_CUSTOM_NUMBERS_JSON = "backup_custom_channel_numbers_json"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isCustomNumbersEnabled(): Boolean {
        return prefs.getBoolean(PREF_USE_CUSTOM_NUMBERS, false)
    }

    fun setCustomNumbersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_USE_CUSTOM_NUMBERS, enabled).apply()
    }

    fun getCustomNumbersMap(): Map<Long, String> {
        var jsonStr = prefs.getString(PREF_CUSTOM_NUMBERS_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            jsonStr = prefs.getString(PREF_BACKUP_CUSTOM_NUMBERS_JSON, null)
        }
        if (jsonStr.isNullOrBlank()) return emptyMap()

        val result = mutableMapOf<Long, String>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val channelId = key.toLongOrNull()
                if (channelId != null) {
                    result[channelId] = json.getString(key)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing custom channel numbers: ${e.message}", e)
        }
        return result
    }

    fun saveCustomNumbersMap(map: Map<Long, String>) {
        val json = JSONObject()
        for ((k, v) in map) {
            json.put(k.toString(), v)
        }
        val str = json.toString()
        prefs.edit()
            .putString(PREF_CUSTOM_NUMBERS_JSON, str)
            .putString(PREF_BACKUP_CUSTOM_NUMBERS_JSON, str)
            .apply()
    }

    suspend fun assignChannelNumber(channelId: Long, newNumber: String): Long? = withContext(Dispatchers.IO) {
        val currentMap = getCustomNumbersMap().toMutableMap()
        val oldNumber = currentMap[channelId]

        var swappedChannelId: Long? = null
        val conflictingEntry = currentMap.entries.find { it.value == newNumber && it.key != channelId }

        if (conflictingEntry != null) {
            swappedChannelId = conflictingEntry.key
            if (oldNumber != null) {
                currentMap[conflictingEntry.key] = oldNumber
            } else {
                val rawChannels = fetchRawChannels()
                val conflictRaw = rawChannels.find { it.id == conflictingEntry.key }
                if (conflictRaw != null && conflictRaw.originalDisplayNumber.isNotBlank()) {
                    currentMap[conflictingEntry.key] = conflictRaw.originalDisplayNumber
                } else {
                    currentMap.remove(conflictingEntry.key)
                }
            }
        }

        currentMap[channelId] = newNumber
        saveCustomNumbersMap(currentMap)
        swappedChannelId
    }

    suspend fun autoAssignLinearOrder(channels: List<Channel>) = withContext(Dispatchers.IO) {
        val newMap = mutableMapOf<Long, String>()
        channels.forEachIndexed { index, channel ->
            newMap[channel.id] = (index + 1).toString()
        }
        saveCustomNumbersMap(newMap)
    }

    fun resetCustomNumbers() {
        setCustomNumbersEnabled(false)
    }

    private fun fetchRawChannels(): List<Channel> {
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

                    channels.add(Channel(id, number, null, number, name, inputId, logoUri))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error querying raw channels: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        return channels
    }

    suspend fun getChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val rawChannels = fetchRawChannels()
        val useCustom = isCustomNumbersEnabled()
        val customMap = if (useCustom) getCustomNumbersMap() else emptyMap()

        val processedChannels = rawChannels.map { raw ->
            val customNum = customMap[raw.id]
            val activeNum = if (useCustom && !customNum.isNullOrBlank()) {
                customNum
            } else {
                raw.originalDisplayNumber
            }
            raw.copy(customDisplayNumber = customNum, displayNumber = activeNum)
        }.toMutableList()

        if (useCustom) {
            processedChannels.sortWith(
                compareBy(
                    { it.displayNumber.toIntOrNull() == null },
                    { it.displayNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.displayNumber },
                    { it.displayName }
                )
            )
        } else {
            processedChannels.sortWith(
                compareBy(
                    { it.originalDisplayNumber.toIntOrNull() == null },
                    { it.originalDisplayNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.originalDisplayNumber }
                )
            )
        }

        processedChannels
    }
}
