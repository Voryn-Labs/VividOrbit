package com.vorynlabs.vividorbit.data

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class StartupMode(val key: String) {
    LAST_WATCHED("last"),
    FIXED_DEFAULT("fixed"),
    FIRST_CHANNEL("first");

    companion object {
        fun fromKey(key: String?): StartupMode {
            return values().find { it.key == key } ?: LAST_WATCHED
        }
    }
}

class ChannelRepository(private val context: Context) {

    companion object {
        private const val TAG = "ChannelRepository"
        private const val PREFS_NAME = "vividorbit_prefs"
        private const val PREF_USE_CUSTOM_NUMBERS = "use_custom_channel_numbers"
        private const val PREF_CUSTOM_NUMBERS_JSON = "custom_channel_numbers_json"
        private const val PREF_PREFERRED_INPUT_ID = "preferred_tuner_input_id"
        private const val PREF_STARTUP_MODE = "startup_mode"
        private const val PREF_DEFAULT_CHANNEL_ID = "default_channel_id"
        private const val PREF_LAST_CHANNEL_ID = "last_channel_id"
        private const val PREF_PREVIOUS_CHANNEL_ID = "previous_channel_id"
        private const val PREF_FAVORITES_JSON = "favorites_channel_ids_json"
        private const val PREF_CHANNELS_CACHE_JSON = "channels_cache_json"
        private const val PREF_BANNER_HIDE_MS = "banner_hide_ms"
        private const val PREF_GUIDE_AUTOHIDE_MS = "guide_autohide_ms"
        private const val PREF_GUIDE_PROGRAM_TITLES = "guide_program_titles"
        private const val PREF_HIDDEN_JSON = "hidden_channel_ids_json"
        private val BANNER_HIDE_OPTIONS = longArrayOf(3000L, 6000L, 10000L)
        private val GUIDE_HIDE_OPTIONS = longArrayOf(10000L, 20000L, 0L)
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var cachedFavorites: Set<Long>? = null
    private var cachedHidden: Set<Long>? = null

    @Synchronized
    fun isCustomNumbersEnabled(): Boolean {
        return prefs.getBoolean(PREF_USE_CUSTOM_NUMBERS, false)
    }

    @Synchronized
    fun setCustomNumbersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_USE_CUSTOM_NUMBERS, enabled).apply()
    }

    @Synchronized
    fun getStartupMode(): StartupMode {
        val key = prefs.getString(PREF_STARTUP_MODE, StartupMode.LAST_WATCHED.key)
        return StartupMode.fromKey(key)
    }

    @Synchronized
    fun setStartupMode(mode: StartupMode) {
        prefs.edit().putString(PREF_STARTUP_MODE, mode.key).apply()
    }

    @Synchronized
    fun getDefaultChannelId(): Long {
        return prefs.getLong(PREF_DEFAULT_CHANNEL_ID, -1L)
    }

    @Synchronized
    fun setDefaultChannelId(id: Long) {
        prefs.edit().putLong(PREF_DEFAULT_CHANNEL_ID, id).apply()
    }

    @Synchronized
    fun getLastChannelId(): Long {
        return prefs.getLong(PREF_LAST_CHANNEL_ID, -1L)
    }

    @Synchronized
    fun setLastChannelId(id: Long) {
        prefs.edit().putLong(PREF_LAST_CHANNEL_ID, id).apply()
    }

    @Synchronized
    fun getPreviousChannelId(): Long {
        return prefs.getLong(PREF_PREVIOUS_CHANNEL_ID, -1L)
    }

    @Synchronized
    fun setPreviousChannelId(id: Long) {
        prefs.edit().putLong(PREF_PREVIOUS_CHANNEL_ID, id).apply()
    }

    @Synchronized
    fun getBannerHideMs(): Long {
        return prefs.getLong(PREF_BANNER_HIDE_MS, 6000L)
    }

    @Synchronized
    fun cycleBannerHideMs(): Long {
        val next = nextOption(getBannerHideMs(), BANNER_HIDE_OPTIONS)
        prefs.edit().putLong(PREF_BANNER_HIDE_MS, next).apply()
        return next
    }

    @Synchronized
    fun getGuideAutoHideMs(): Long {
        return prefs.getLong(PREF_GUIDE_AUTOHIDE_MS, 20000L)
    }

    @Synchronized
    fun cycleGuideAutoHideMs(): Long {
        val next = nextOption(getGuideAutoHideMs(), GUIDE_HIDE_OPTIONS)
        prefs.edit().putLong(PREF_GUIDE_AUTOHIDE_MS, next).apply()
        return next
    }

    @Synchronized
    fun isGuideProgramTitlesEnabled(): Boolean {
        return prefs.getBoolean(PREF_GUIDE_PROGRAM_TITLES, false)
    }

    @Synchronized
    fun setGuideProgramTitlesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_GUIDE_PROGRAM_TITLES, enabled).apply()
    }

    private fun nextOption(current: Long, options: LongArray): Long {
        val index = options.indexOf(current)
        return options[(index + 1).mod(options.size)]
    }

    @Synchronized
    fun getHiddenIds(): Set<Long> {
        val cached = cachedHidden
        if (cached != null) return cached
        val jsonStr = prefs.getString(PREF_HIDDEN_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            cachedHidden = emptySet()
            return emptySet()
        }
        val result = mutableSetOf<Long>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                result.add(array.getLong(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hidden channels JSON: ${e.message}", e)
        }
        cachedHidden = result
        return result
    }

    @Synchronized
    fun isHidden(channelId: Long): Boolean = getHiddenIds().contains(channelId)

    @Synchronized
    fun setHidden(channelId: Long, hidden: Boolean) {
        val ids = getHiddenIds().toMutableSet()
        if (hidden) ids.add(channelId) else ids.remove(channelId)
        cachedHidden = ids
        val array = JSONArray()
        ids.forEach { array.put(it) }
        prefs.edit().putString(PREF_HIDDEN_JSON, array.toString()).apply()
    }

    @Synchronized
    fun getFavoriteIds(): Set<Long> {
        val cached = cachedFavorites
        if (cached != null) return cached

        val jsonStr = prefs.getString(PREF_FAVORITES_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            cachedFavorites = emptySet()
            return emptySet()
        }

        val result = mutableSetOf<Long>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                result.add(array.getLong(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing favorites JSON: ${e.message}", e)
        }
        cachedFavorites = result
        return result
    }

    @Synchronized
    fun setFavoriteIds(ids: Set<Long>) {
        cachedFavorites = ids
        val array = JSONArray()
        ids.forEach { array.put(it) }
        prefs.edit().putString(PREF_FAVORITES_JSON, array.toString()).apply()
    }

    @Synchronized
    fun isFavorite(channelId: Long): Boolean {
        return getFavoriteIds().contains(channelId)
    }

    @Synchronized
    fun toggleFavorite(channelId: Long): Boolean {
        val favs = getFavoriteIds().toMutableSet()
        val isNowFav = toggleFavoriteInSet(favs, channelId)
        setFavoriteIds(favs)
        return isNowFav
    }

    fun resolveStartupChannel(
        channels: List<Channel>,
        preserveCurrentChannel: Boolean = false,
        currentChannel: Channel? = null
    ): Channel? {
        if (channels.isEmpty()) return null
        if (preserveCurrentChannel && currentChannel != null) {
            return channels.find { it.id == currentChannel.id } ?: channels[0]
        }

        return resolveStartupChannelLogic(
            channels,
            getStartupMode(),
            getDefaultChannelId(),
            getLastChannelId()
        )
    }

    @Synchronized
    fun getPreferredInputId(): String? {
        return prefs.getString(PREF_PREFERRED_INPUT_ID, null)
    }

    @Synchronized
    fun setPreferredInputId(inputId: String) {
        prefs.edit().putString(PREF_PREFERRED_INPUT_ID, inputId).apply()
    }

    fun getAvailableTunerInputIds(): List<String> {
        val tvInputManager = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            tvInputManager.tvInputList
                .filter { it.type == TvInputInfo.TYPE_TUNER || !it.isPassthroughInput }
                .map { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying tvInputList: ${e.message}", e)
            emptyList()
        }
    }

    @Synchronized
    fun getCustomNumbersMap(): Map<Long, String> {
        val jsonStr = prefs.getString(PREF_CUSTOM_NUMBERS_JSON, null)
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

    @Synchronized
    fun saveCustomNumbersMap(map: Map<Long, String>) {
        val json = JSONObject()
        for ((k, v) in map) {
            json.put(k.toString(), v)
        }
        prefs.edit().putString(PREF_CUSTOM_NUMBERS_JSON, json.toString()).apply()
    }

    suspend fun assignChannelNumber(channelId: Long, newNumber: String): Long? = withContext(Dispatchers.IO) {
        val currentMap = getCustomNumbersMap().toMutableMap()
        val oldNumber = currentMap[channelId]

        if (oldNumber == newNumber) {
            return@withContext null
        }

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

    fun getCachedChannels(): List<Channel> {
        val jsonStr = prefs.getString(PREF_CHANNELS_CACHE_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<Channel>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val logoUri = item.optString("logoUri").takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
                list.add(
                    Channel(
                        id = item.getLong("id"),
                        originalDisplayNumber = item.getString("originalNumber"),
                        customDisplayNumber = item.optString("customNumber").takeIf { it.isNotEmpty() },
                        displayNumber = item.getString("displayNumber"),
                        displayName = item.getString("name"),
                        inputId = item.getString("inputId"),
                        logoUri = logoUri,
                        genre = item.optString("genre", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels cache: ${e.message}", e)
            emptyList()
        }
    }

    @Synchronized
    fun saveChannelsCache(channels: List<Channel>) {
        val array = JSONArray()
        for (ch in channels) {
            val item = JSONObject()
            item.put("id", ch.id)
            item.put("originalNumber", ch.originalDisplayNumber)
            item.put("displayNumber", ch.displayNumber)
            ch.customDisplayNumber?.let { item.put("customNumber", it) }
            item.put("name", ch.displayName)
            item.put("inputId", ch.inputId)
            ch.logoUri?.toString()?.let { item.put("logoUri", it) }
            item.put("genre", ch.genre)
            array.put(item)
        }
        prefs.edit().putString(PREF_CHANNELS_CACHE_JSON, array.toString()).apply()
    }

    private fun parseChannelsFromCursor(
        cursor: Cursor,
        defaultInputId: String,
        channels: MutableList<Channel>
    ) {
        val idIndex = cursor.getColumnIndex(TvContract.Channels._ID)
        val numberIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER)
        val nameIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME)
        val inputIdIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_INPUT_ID)
        val genreIndex = cursor.getColumnIndex(TvContract.Channels.COLUMN_BROADCAST_GENRE)

        while (cursor.moveToNext()) {
            val id = if (idIndex != -1) cursor.getLong(idIndex) else -1L
            val number = if (numberIndex != -1) cursor.getString(numberIndex) ?: "" else ""
            var name = if (nameIndex != -1) cursor.getString(nameIndex) ?: "" else ""
            val resolvedInputId = if (inputIdIndex != -1) cursor.getString(inputIdIndex) ?: defaultInputId else defaultInputId
            val logoUri = TvContract.buildChannelLogoUri(id)
            val broadcastGenre = if (genreIndex != -1) cursor.getString(genreIndex) ?: "" else ""

            if (name.isBlank()) {
                name = if (number.isNotBlank()) "Channel $number" else "Channel $id"
            }

            if (id != -1L && channels.none { it.id == id }) {
                channels.add(Channel(id, number, null, number, name, resolvedInputId, logoUri, channelGenreLabel(broadcastGenre)))
            }
        }
    }

    private fun fetchRawChannels(): List<Channel> {
        val channels = mutableListOf<Channel>()
        val projection = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_INPUT_ID,
            TvContract.Channels.COLUMN_BROADCAST_GENRE
        )

        val tunerInputs = getAvailableTunerInputIds()
        val targetInputIds = if (tunerInputs.isNotEmpty()) {
            val preferred = getPreferredInputId()
            if (preferred != null && tunerInputs.contains(preferred)) {
                listOf(preferred)
            } else {
                tunerInputs
            }
        } else {
            listOf("")
        }

        for (inputId in targetInputIds) {
            val queryUri = if (inputId.isNotEmpty()) {
                TvContract.buildChannelsUriForInput(inputId)
            } else {
                TvContract.Channels.CONTENT_URI
            }

            try {
                context.contentResolver.query(
                    queryUri,
                    projection,
                    "${TvContract.Channels.COLUMN_BROWSABLE} = 1",
                    null,
                    null
                )?.use { cursor ->
                    parseChannelsFromCursor(cursor, inputId, channels)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    context.contentResolver.query(
                        queryUri,
                        projection,
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        parseChannelsFromCursor(cursor, inputId, channels)
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "Error querying raw channels: ${ex.message}", ex)
                }
            }
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

internal fun toggleFavoriteInSet(favorites: MutableSet<Long>, channelId: Long): Boolean {
    return if (favorites.contains(channelId)) {
        favorites.remove(channelId)
        false
    } else {
        favorites.add(channelId)
        true
    }
}

private val CANONICAL_GENRE_LABELS = mapOf(
    "animal/wildlife" to "Wildlife",
    "arts" to "Arts",
    "shopping" to "Shopping",
    "comedy" to "Comedy",
    "documentary" to "Documentary",
    "education" to "Education",
    "entertainment" to "Entertainment",
    "family/kids" to "Kids",
    "gaming" to "Gaming",
    "lifestyle" to "Lifestyle",
    "movies" to "Movies",
    "music" to "Music",
    "news" to "News",
    "premium" to "Premium",
    "religious" to "Religious",
    "science/nature" to "Science",
    "series" to "Series",
    "sports" to "Sports",
    "technology" to "Technology",
    "travel" to "Travel"
)

internal fun channelGenreLabel(broadcastGenre: String?): String {
    if (broadcastGenre.isNullOrBlank()) return ""
    for (part in broadcastGenre.split(',')) {
        val trimmed = part.trim().lowercase()
        if (trimmed.isEmpty()) continue
        CANONICAL_GENRE_LABELS[trimmed]?.let { return it }
    }
    return broadcastGenre.split(',')[0].trim().replaceFirstChar { it.uppercase() }
}

internal fun resolveStartupChannelLogic(
    channels: List<Channel>,
    mode: StartupMode,
    defaultId: Long,
    lastId: Long
): Channel? {
    if (channels.isEmpty()) return null
    return when (mode) {
        StartupMode.FIXED_DEFAULT -> {
            channels.find { it.id == defaultId }
                ?: (if (lastId != -1L) channels.find { it.id == lastId } else null)
                ?: channels[0]
        }
        StartupMode.FIRST_CHANNEL -> {
            channels[0]
        }
        StartupMode.LAST_WATCHED -> {
            channels.find { it.id == lastId }
                ?: (if (defaultId != -1L) channels.find { it.id == defaultId } else null)
                ?: channels[0]
        }
    }
}
