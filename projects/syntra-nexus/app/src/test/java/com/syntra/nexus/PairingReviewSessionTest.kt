package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.fail
import org.junit.Test

class PairingReviewSessionTest {
    @Test
    fun startsInPendingReview() {
        val session = session()

        assertEquals(PairingReviewStatus.PENDING_REVIEW, session.status)
    }

    @Test
    fun acceptsOnlyOnThisDevice() {
        val accepted = session().acceptLocally(nowMillis = 10_001L)

        assertEquals(PairingReviewStatus.ACCEPTED_LOCALLY, accepted.status)
    }

    @Test
    fun acceptanceDoesNotMutateOriginalSession() {
        val original = session()
        val accepted = original.acceptLocally(nowMillis = 10_001L)

        assertNotSame(original, accepted)
        assertEquals(PairingReviewStatus.PENDING_REVIEW, original.status)
    }

    @Test
    fun expiresPendingInvitation() {
        val expired = session().refresh(nowMillis = 12_000L)

        assertEquals(PairingReviewStatus.EXPIRED, expired.status)
    }

    @Test
    fun doesNotChangeAcceptedSessionToExpired() {
        val accepted = session().acceptLocally(nowMillis = 10_001L)
        val refreshed = accepted.refresh(nowMillis = 99_000L)

        assertEquals(PairingReviewStatus.ACCEPTED_LOCALLY, refreshed.status)
    }

    @Test
    fun rejectsAcceptanceAfterExpiry() {
        assertInvalid { session().acceptLocally(nowMillis = 12_000L) }
    }

    @Test
    fun rejectsSecondDecision() {
        val accepted = session().acceptLocally(nowMillis = 10_001L)

        assertInvalid { accepted.rejectLocally(nowMillis = 10_002L) }
    }

    @Test
    fun rejectsAlreadyExpiredInvitationWhenCreatingSession() {
        val invitation = ParsedPairingInvitation(
            requestId = "abcdefghijklmnopqrstuv",
            token = "a".repeat(43),
            expiresAtMillis = 10_000L
        )

        assertInvalid { PairingReviewSession.from(invitation, nowMillis = 10_000L) }
    }

    private fun session(): PairingReviewSession = PairingReviewSession(
        requestId = "abcdefghijklmnopqrstuv",
        expiresAtMillis = 12_000L
    )

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid state transition to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected state validation failure.
        }
    }
}
