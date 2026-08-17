package com.vividorbit.livetv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class TokenValidationTest {

    private fun isTokenValid(token: String, sessionToken: String): Boolean {
        if (token.isEmpty() || sessionToken.isEmpty()) return false
        return MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), sessionToken.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun testValidTokenMatches() {
        val sessionToken = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertTrue(isTokenValid(sessionToken, sessionToken))
    }

    @Test
    fun testInvalidTokenFails() {
        val sessionToken = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertFalse(isTokenValid("wrong_token", sessionToken))
    }

    @Test
    fun testEmptyTokenFails() {
        val sessionToken = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertFalse(isTokenValid("", sessionToken))
        assertFalse(isTokenValid(sessionToken, ""))
    }
}
