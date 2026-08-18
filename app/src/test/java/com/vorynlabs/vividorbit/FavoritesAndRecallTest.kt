package com.vividorbit.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesAndRecallTest {

    @Test
    fun testFavoriteToggleLogic() {
        val favorites = mutableSetOf<Long>()

        fun toggle(id: Long): Boolean {
            return if (favorites.contains(id)) {
                favorites.remove(id)
                false
            } else {
                favorites.add(id)
                true
            }
        }

        assertTrue(toggle(101L))
        assertTrue(favorites.contains(101L))
        assertTrue(toggle(102L))
        assertEquals(2, favorites.size)

        assertFalse(toggle(101L))
        assertFalse(favorites.contains(101L))
        assertEquals(1, favorites.size)
    }

    @Test
    fun testPreviousChannelTracking() {
        var current: Long? = null
        var previous: Long? = null

        fun tune(id: Long) {
            if (current != null && current != id) {
                previous = current
            }
            current = id
        }

        tune(101L)
        assertEquals(101L, current)
        assertEquals(null, previous)

        tune(105L)
        assertEquals(105L, current)
        assertEquals(101L, previous)

        tune(110L)
        assertEquals(110L, current)
        assertEquals(105L, previous)

        // Recall
        val recallTarget = previous
        if (recallTarget != null) tune(recallTarget)
        assertEquals(105L, current)
        assertEquals(110L, previous)
    }
}
