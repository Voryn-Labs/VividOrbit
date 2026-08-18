package com.vorynlabs.vividorbit.data

import android.content.Context
import android.media.tv.TvContract
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class EpgResult(
    val currentProgram: Program?,
    val nextProgram: Program?,
    val timestamp: Long
)

class EpgRepository(private val context: Context) {

    companion object {
        private const val TAG = "EpgRepository"
        private const val CACHE_TTL_MS = 60_000L // 60 seconds
    }

    private val cache = ConcurrentHashMap<Long, EpgResult>()
    private val upcomingCache = ConcurrentHashMap<Long, Pair<Long, List<Program>>>()

    suspend fun getNowAndNext(channelId: Long): Pair<Program?, Program?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cache[channelId]
        if (cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            return@withContext Pair(cached.currentProgram, cached.nextProgram)
        }

        try {
            val queryUri = TvContract.buildProgramsUriForChannel(
                channelId,
                now - 30 * 60 * 1000L,
                now + 4 * 60 * 60 * 1000L
            )

            val projection = arrayOf(
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS
            )

            var current: Program? = null
            var next: Program? = null

            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(TvContract.Programs.COLUMN_TITLE)
                val startIdx = cursor.getColumnIndex(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS)
                val endIdx = cursor.getColumnIndex(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS)

                while (cursor.moveToNext()) {
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) ?: "" else ""
                    val start = if (startIdx != -1) cursor.getLong(startIdx) else 0L
                    val end = if (endIdx != -1) cursor.getLong(endIdx) else 0L

                    if (title.isBlank()) continue

                    val program = Program(title, start, end)
                    if (now in start until end && current == null) {
                        current = program
                    } else if (start >= now && next == null) {
                        next = program
                    }

                    if (current != null && next != null) break
                }
            }

            val result = EpgResult(current, next, now)
            cache[channelId] = result
            Pair(current, next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "EPG query failed for channel $channelId: ${e.message}")
            val fallback = EpgResult(null, null, now)
            cache[channelId] = fallback
            Pair(null, null)
        }
    }

    suspend fun getUpcoming(channelId: Long, limit: Int = 5): List<Program> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        upcomingCache[channelId]?.let { (cachedAt, list) ->
            if (now - cachedAt < CACHE_TTL_MS) return@withContext list
        }

        val result = try {
            val queryUri = TvContract.buildProgramsUriForChannel(
                channelId,
                now,
                now + 8 * 60 * 60 * 1000L
            )
            val projection = arrayOf(
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS
            )
            val upcoming = mutableListOf<Program>()
            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(TvContract.Programs.COLUMN_TITLE)
                val startIdx = cursor.getColumnIndex(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS)
                val endIdx = cursor.getColumnIndex(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS)

                while (cursor.moveToNext() && upcoming.size < limit) {
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) ?: "" else ""
                    val start = if (startIdx != -1) cursor.getLong(startIdx) else 0L
                    val end = if (endIdx != -1) cursor.getLong(endIdx) else 0L
                    if (title.isBlank() || end <= now) continue
                    upcoming.add(Program(title, start, end))
                }
            }
            upcoming
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Upcoming EPG query failed for channel $channelId: ${e.message}")
            emptyList()
        }

        upcomingCache[channelId] = now to result
        result
    }

    fun clearCache() {
        cache.clear()
        upcomingCache.clear()
    }
}
