package com.vorynlabs.vividorbit

import com.vorynlabs.vividorbit.ui.isLastWalkthroughPage
import com.vorynlabs.vividorbit.ui.isPhoneWalkthroughPage
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
    fun testPageCountIsSeven() {
        assertEquals(8, walkthroughPageCount())
    }

    @Test
    fun testNextAdvancesWithinBounds() {
        assertEquals(1, nextWalkthroughPage(0))
        assertEquals(6, nextWalkthroughPage(5))
        assertEquals(7, nextWalkthroughPage(6))
    }

    @Test
    fun testNextClampsAtLastPage() {
        assertEquals(7, nextWalkthroughPage(7))
    }

    @Test
    fun testPrevClampsAtFirstPage() {
        assertEquals(0, prevWalkthroughPage(0))
        assertEquals(6, prevWalkthroughPage(7))
    }

    @Test
    fun testIsLastWalkthroughPage() {
        assertFalse(isLastWalkthroughPage(6))
        assertTrue(isLastWalkthroughPage(7))
    }

    @Test
    fun testPhonePageIndex() {
        assertTrue(isPhoneWalkthroughPage(6))
        assertFalse(isPhoneWalkthroughPage(0))
    }

    @Test
    fun testShouldShowWalkthroughWhenNotSeen() {
        assertTrue(shouldShowWalkthrough(seen = false))
        assertFalse(shouldShowWalkthrough(seen = true))
    }
}
