package com.vorynlabs.vividorbit

import com.vorynlabs.vividorbit.ui.assignDemoNumberOne
import com.vorynlabs.vividorbit.ui.isLastWalkthroughPage
import com.vorynlabs.vividorbit.ui.isPlaygroundPage
import com.vorynlabs.vividorbit.ui.nextWalkthroughPage
import com.vorynlabs.vividorbit.ui.prevWalkthroughPage
import com.vorynlabs.vividorbit.ui.seedDemoChannels
import com.vorynlabs.vividorbit.ui.shouldShowWalkthrough
import com.vorynlabs.vividorbit.ui.toggleDemoFavorite
import com.vorynlabs.vividorbit.ui.toggleDemoHidden
import com.vorynlabs.vividorbit.ui.walkthroughPageCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughTest {

    @Test
    fun testPageCountIsSix() {
        assertEquals(6, walkthroughPageCount())
    }

    @Test
    fun testNextAdvancesWithinBounds() {
        assertEquals(1, nextWalkthroughPage(0))
        assertEquals(2, nextWalkthroughPage(1))
        assertEquals(5, nextWalkthroughPage(4))
    }

    @Test
    fun testNextClampsAtLastPage() {
        assertEquals(5, nextWalkthroughPage(5))
    }

    @Test
    fun testPrevClampsAtFirstPage() {
        assertEquals(0, prevWalkthroughPage(0))
        assertEquals(0, prevWalkthroughPage(1))
    }

    @Test
    fun testPrevStepsBackWithinBounds() {
        assertEquals(4, prevWalkthroughPage(5))
        assertEquals(2, prevWalkthroughPage(3))
    }

    @Test
    fun testIsLastWalkthroughPage() {
        assertFalse(isLastWalkthroughPage(0))
        assertFalse(isLastWalkthroughPage(4))
        assertTrue(isLastWalkthroughPage(5))
    }

    @Test
    fun testPlaygroundPages() {
        assertFalse(isPlaygroundPage(0))
        assertTrue(isPlaygroundPage(2))
        assertTrue(isPlaygroundPage(3))
        assertFalse(isPlaygroundPage(4))
    }

    @Test
    fun testShouldShowWalkthroughWhenNotSeen() {
        assertTrue(shouldShowWalkthrough(seen = false))
    }

    @Test
    fun testShouldNotShowWalkthroughWhenSeen() {
        assertFalse(shouldShowWalkthrough(seen = true))
    }

    @Test
    fun testAssignOneWhenFree() {
        val channels = seedDemoChannels()
        val result = assignDemoNumberOne(channels, 1)!!
        assertEquals("1", result.assignedNumber)
        assertNull(result.swappedWith)
        assertEquals("1", channels[0].customNumber)
    }

    @Test
    fun testAssignOneSwapsWithHolder() {
        val channels = seedDemoChannels()
        assignDemoNumberOne(channels, 1)
        val result = assignDemoNumberOne(channels, 2)!!
        assertEquals("Sony SAB HD", channels[1].name)
        assertEquals("1", channels[1].customNumber)
        assertEquals("201", channels[0].customNumber)
        assertEquals("Zee TV HD", result.swappedWith)
    }

    @Test
    fun testToggleFavoriteAndHidden() {
        val channels = seedDemoChannels()
        assertTrue(toggleDemoFavorite(channels, 3))
        assertTrue(channels[2].favorite)
        assertFalse(toggleDemoFavorite(channels, 3))
        assertTrue(toggleDemoHidden(channels, 2))
        assertTrue(channels[1].hidden)
        assertFalse(toggleDemoHidden(channels, 2))
    }
}
