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
    fun testPageCountIsOne() {
        assertEquals(1, walkthroughPageCount())
    }

    @Test
    fun testNextClampsAtLastPage() {
        assertEquals(0, nextWalkthroughPage(0))
    }

    @Test
    fun testPrevClampsAtFirstPage() {
        assertEquals(0, prevWalkthroughPage(0))
    }

    @Test
    fun testIsLastWalkthroughPage() {
        assertTrue(isLastWalkthroughPage(0))
    }

    @Test
    fun testShouldShowWalkthroughWhenNotSeen() {
        assertTrue(shouldShowWalkthrough(seen = false))
        assertFalse(shouldShowWalkthrough(seen = true))
    }
}
