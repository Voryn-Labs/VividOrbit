package com.vorynlabs.vividorbit

import com.vorynlabs.vividorbit.data.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTest {

    @Test
    fun testProgressPercentAtStart() {
        val start = 1000L
        val end = 2000L
        val program = Program("Test Show", start, end)
        assertEquals(0, program.getProgressPercent(1000L))
    }

    @Test
    fun testProgressPercentAtMiddle() {
        val start = 1000L
        val end = 2000L
        val program = Program("Test Show", start, end)
        assertEquals(50, program.getProgressPercent(1500L))
    }

    @Test
    fun testProgressPercentAtEnd() {
        val start = 1000L
        val end = 2000L
        val program = Program("Test Show", start, end)
        assertEquals(100, program.getProgressPercent(2000L))
        assertEquals(100, program.getProgressPercent(3000L))
    }

    @Test
    fun testFormattedTimeWindowNotEmpty() {
        val start = 1700000000000L
        val end = 1700003600000L
        val program = Program("Test Show", start, end)
        val formatted = program.getFormattedTimeWindow()
        assertTrue(formatted.contains("–"))
    }
}
