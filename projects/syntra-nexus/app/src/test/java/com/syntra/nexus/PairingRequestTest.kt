package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingRequestTest {
    @Test
    fun createsShortLivedUrlSafeInvitation() {
        val request = PairingRequest.create(nowMillis = 1_000L)

        assertTrue(request.requestId.matches(Regex("^[A-Za-z0-9_-]{22}$")))
        assertTrue(request.token.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertTrue(request.payload.startsWith("syntra-nexus://pair?v=1&"))
        assertEquals(121_000L, request.expiresAtMillis)
    }

    @Test
    fun eachInvitationUsesDifferentRandomValues() {
        val first = PairingRequest.create(nowMillis = 1_000L)
        val second = PairingRequest.create(nowMillis = 1_000L)

        assertNotEquals(first.requestId, second.requestId)
        assertNotEquals(first.token, second.token)
    }

    @Test
    fun expiresAtTheDeadline() {
        val request = PairingRequest.create(nowMillis = 5_000L, ttlMillis = 10_000L)

        assertFalse(request.isExpired(14_999L))
        assertTrue(request.isExpired(15_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLifetimeLongerThanTwoMinutes() {
        PairingRequest.create(ttlMillis = PairingRequest.DEFAULT_TTL_MILLIS + 1)
    }
}
