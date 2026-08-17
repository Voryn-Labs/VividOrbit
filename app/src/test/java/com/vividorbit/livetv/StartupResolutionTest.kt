package com.vividorbit.livetv

import com.vividorbit.livetv.data.Channel
import com.vividorbit.livetv.data.StartupMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupResolutionTest {

    private fun mockChannel(id: Long, num: String, name: String): Channel {
        return Channel(
            id = id,
            displayNumber = num,
            customDisplayNumber = num,
            originalDisplayNumber = num,
            displayName = name,
            inputId = "tuner",
            logoUri = null
        )
    }

    private val channels = listOf(
        mockChannel(101L, "1", "Zee TV HD"),
        mockChannel(102L, "2", "Sony SAB HD"),
        mockChannel(103L, "3", "DD News HD")
    )

    private fun resolve(
        list: List<Channel>,
        mode: StartupMode,
        lastId: Long,
        defaultId: Long,
        preserve: Boolean = false,
        current: Channel? = null
    ): Channel? {
        if (list.isEmpty()) return null
        if (preserve && current != null) {
            return list.find { it.id == current.id } ?: list[0]
        }
        return when (mode) {
            StartupMode.FIXED_DEFAULT -> {
                list.find { it.id == defaultId }
                    ?: (if (lastId != -1L) list.find { it.id == lastId } else null)
                    ?: list[0]
            }
            StartupMode.FIRST_CHANNEL -> {
                list[0]
            }
            StartupMode.LAST_WATCHED -> {
                list.find { it.id == lastId }
                    ?: (if (defaultId != -1L) list.find { it.id == defaultId } else null)
                    ?: list[0]
            }
        }
    }

    @Test
    fun testLastWatchedWithValidLastId() {
        val result = resolve(channels, StartupMode.LAST_WATCHED, lastId = 102L, defaultId = 101L)
        assertEquals(102L, result?.id)
    }

    @Test
    fun testLastWatchedWithStaleLastIdFallsBackToDefault() {
        val result = resolve(channels, StartupMode.LAST_WATCHED, lastId = 999L, defaultId = 103L)
        assertEquals(103L, result?.id)
    }

    @Test
    fun testLastWatchedWithStaleLastIdAndNoDefaultFallsBackToFirst() {
        val result = resolve(channels, StartupMode.LAST_WATCHED, lastId = 999L, defaultId = -1L)
        assertEquals(101L, result?.id)
    }

    @Test
    fun testFixedDefaultWithValidId() {
        val result = resolve(channels, StartupMode.FIXED_DEFAULT, lastId = 101L, defaultId = 103L)
        assertEquals(103L, result?.id)
    }

    @Test
    fun testFixedDefaultWithStaleIdFallsBackToLastOrFirst() {
        val result = resolve(channels, StartupMode.FIXED_DEFAULT, lastId = 102L, defaultId = 999L)
        assertEquals(102L, result?.id)
    }

    @Test
    fun testFirstChannelModeAlwaysReturnsFirst() {
        val result = resolve(channels, StartupMode.FIRST_CHANNEL, lastId = 103L, defaultId = 102L)
        assertEquals(101L, result?.id)
    }

    @Test
    fun testEmptyChannelListReturnsNull() {
        val result = resolve(emptyList(), StartupMode.LAST_WATCHED, lastId = 101L, defaultId = 102L)
        assertNull(result)
    }
}
