package com.vorynlabs.vividorbit

import com.vorynlabs.vividorbit.data.Channel
import com.vorynlabs.vividorbit.data.StartupMode
import com.vorynlabs.vividorbit.data.resolveStartupChannelLogic
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

    @Test
    fun testLastWatchedWithValidLastId() {
        val result = resolveStartupChannelLogic(channels, StartupMode.LAST_WATCHED, defaultId = 101L, lastId = 102L)
        assertEquals(102L, result?.id)
    }

    @Test
    fun testLastWatchedWithStaleLastIdFallsBackToDefault() {
        val result = resolveStartupChannelLogic(channels, StartupMode.LAST_WATCHED, defaultId = 103L, lastId = 999L)
        assertEquals(103L, result?.id)
    }

    @Test
    fun testLastWatchedWithStaleLastIdAndNoDefaultFallsBackToFirst() {
        val result = resolveStartupChannelLogic(channels, StartupMode.LAST_WATCHED, defaultId = -1L, lastId = 999L)
        assertEquals(101L, result?.id)
    }

    @Test
    fun testFixedDefaultWithValidId() {
        val result = resolveStartupChannelLogic(channels, StartupMode.FIXED_DEFAULT, defaultId = 103L, lastId = 101L)
        assertEquals(103L, result?.id)
    }

    @Test
    fun testFixedDefaultWithStaleIdFallsBackToLastOrFirst() {
        val result = resolveStartupChannelLogic(channels, StartupMode.FIXED_DEFAULT, defaultId = 999L, lastId = 102L)
        assertEquals(102L, result?.id)
    }

    @Test
    fun testFirstChannelModeAlwaysReturnsFirst() {
        val result = resolveStartupChannelLogic(channels, StartupMode.FIRST_CHANNEL, defaultId = 102L, lastId = 103L)
        assertEquals(101L, result?.id)
    }

    @Test
    fun testEmptyChannelListReturnsNull() {
        val result = resolveStartupChannelLogic(emptyList(), StartupMode.LAST_WATCHED, defaultId = 101L, lastId = 102L)
        assertNull(result)
    }
}