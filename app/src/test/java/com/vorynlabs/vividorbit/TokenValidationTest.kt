package com.vorynlabs.vividorbit

import com.vorynlabs.vividorbit.server.isConstantTimeTokenValid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenValidationTest {

    @Test
    fun testValidTokenMatches() {
        val sessionToken = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertTrue(isConstantTimeTokenValid(sessionToken, sessionToken))
    }

    @Test
    fun testInvalidTokenFails() {
        val sessionToken = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertFalse(isConstantTimeTokenValid("wrong_token", sessionToken))
    }

    @Test
    fun testEmptyTokenFails() {
        val sessionToken = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertFalse(isConstantTimeTokenValid("", sessionToken))
        assertFalse(isConstantTimeTokenValid(sessionToken, ""))
    }
}