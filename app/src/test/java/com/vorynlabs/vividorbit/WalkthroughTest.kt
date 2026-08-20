package com.vorynlabs.vividorbit

import com.vorynlabs.vividorbit.ui.isLastWalkthroughPage
import com.vorynlabs.vividorbit.ui.nextWalkthroughPage
import com.vorynlabs.vividorbit.ui.prevWalkthroughPage
import com.vorynlabs.vividorbit.ui.shouldShowWalkthrough
import com.vorynlabs.vividorbit.ui.walkthroughPageCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkthroughTest {

    @Test
    fun testPageCountIsFour() {
        assertEquals(4, walkthroughPageCount())
    }

    @Test
    fun testNextAdvancesWithinBounds() {
        assertEquals(1, nextWalkthroughPage(0))
        assertEquals(2, nextWalkthroughPage(1))
        assertEquals(3, nextWalkthroughPage(2))
    }

    @Test
    fun testNextClampsAtLastPage() {
        assertEquals(3, nextWalkthroughPage(3))
    }

    @Test
    fun testPrevClampsAtFirstPage() {
        assertEquals(0, prevWalkthroughPage(0))
        assertEquals(2, prevWalkthroughPage(3))
    }

    @Test
    fun testIsLastWalkthroughPage() {
        assertFalse(isLastWalkthroughPage(2))
        assertTrue(isLastWalkthroughPage(3))
    }

    @Test
    fun testShouldShowWalkthroughWhenNotSeen() {
        assertTrue(shouldShowWalkthrough(seen = false))
        assertFalse(shouldShowWalkthrough(seen = true))
    }
}
