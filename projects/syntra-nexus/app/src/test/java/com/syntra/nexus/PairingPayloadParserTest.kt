package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PairingPayloadParserTest {
    @Test
    fun parsesValidInvitation() {
        val request = PairingRequest.create(nowMillis = 10_000L)

        val parsed = PairingPayloadParser.parse(request.payload, nowMillis = 10_001L)

        assertEquals(request.requestId, parsed.requestId)
        assertEquals(request.token, parsed.token)
        assertEquals(request.expiresAtMillis, parsed.expiresAtMillis)
    }

    @Test
    fun rejectsExpiredInvitation() {
        val request = PairingRequest.create(nowMillis = 10_000L)

        assertInvalid(request.payload, nowMillis = request.expiresAtMillis)
    }

    @Test
    fun rejectsUnsupportedScheme() {
        val request = PairingRequest.create(nowMillis = 10_000L)
        val changed = request.payload.replace("syntra-nexus://", "https://")

        assertInvalid(changed, nowMillis = 10_001L)
    }

    @Test
    fun rejectsDuplicateFields() {
        val request = PairingRequest.create(nowMillis = 10_000L)
        val changed = request.payload + "&v=1"

        assertInvalid(changed, nowMillis = 10_001L)
    }

    @Test
    fun rejectsUnexpectedFields() {
        val request = PairingRequest.create(nowMillis = 10_000L)
        val changed = request.payload + "&admin=true"

        assertInvalid(changed, nowMillis = 10_001L)
    }

    @Test
    fun rejectsExpiryBeyondTwoMinutes() {
        val request = PairingRequest.create(nowMillis = 10_000L)
        val changed = request.payload.replace(
            "exp=${request.expiresAtMillis}",
            "exp=${10_000L + PairingRequest.DEFAULT_TTL_MILLIS + 1L}"
        )

        assertInvalid(changed, nowMillis = 10_001L)
    }

    @Test
    fun rejectsMalformedToken() {
        val request = PairingRequest.create(nowMillis = 10_000L)
        val changed = request.payload.replace("token=${request.token}", "token=bad")

        assertInvalid(changed, nowMillis = 10_001L)
    }

    private fun assertInvalid(payload: String, nowMillis: Long) {
        try {
            PairingPayloadParser.parse(payload, nowMillis)
            fail("Expected invalid invitation to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
