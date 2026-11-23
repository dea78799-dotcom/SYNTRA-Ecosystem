package com.syntra.nexus

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingHandshakeStateMachineTest {
    private val initiator = createKeyPair()
    private val responder = createKeyPair()
    private val transcript = PairingTranscript.create(
        requestId = REQUEST_ID,
        expiresAtMillis = EXPIRY,
        initiatorPublicKeyEncoded = initiator.public.encoded,
        responderPublicKeyEncoded = responder.public.encoded,
        initiatorNonce = ByteArray(32) { (it + 1).toByte() },
        responderNonce = ByteArray(32) { (it + 65).toByte() },
        nowMillis = NOW
    )

    @Test
    fun reachesPairedOnlyAfterVerifiedPeerProofAndBothApprovals() {
        val created = session()
        val waiting = created.invitationSent(NOW + 1)
        val verifying = waiting.peerConnected(NOW + 2)
        val verified = verifying.verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signResponder(),
            PairingSignerRole.RESPONDER, NOW + 3
        ).session
        val peerReview = verified.approveLocally(NOW + 4)
        val paired = peerReview.peerApprovalVerified(NOW + 5)

        assertEquals(PairingHandshakeState.PAIRED, paired.state)
        assertEquals(PairingHandshakeDecision.BOTH_APPROVED, paired.decision)
        assertTrue(paired.isTerminal)
        assertEquals(PairingHandshakeState.CREATED, created.state)
    }

    @Test
    fun transitionAtExpiryCannotAdvanceToNextState() {
        val expiredDuringTransition = session().invitationSent(NOW + 1)
            .peerConnected(EXPIRY)

        assertEquals(PairingHandshakeState.EXPIRED, expiredDuringTransition.state)
        assertNotEquals(PairingHandshakeState.VERIFYING_PEER, expiredDuringTransition.state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotApproveBeforePeerProof() {
        session().invitationSent(NOW + 1).approveLocally(NOW + 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotVerifyPeerBeforePeerConnection() {
        session().verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signResponder(),
            PairingSignerRole.RESPONDER, NOW + 1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotMarkPairedBeforeLocalApproval() {
        verifyingSession().verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signResponder(),
            PairingSignerRole.RESPONDER, NOW + 3
        ).session.peerApprovalVerified(NOW + 4)
    }

    @Test
    fun expiresAtExactDeadlineAndCannotBeApproved() {
        val expired = session().invitationSent(NOW + 1).refresh(EXPIRY)
        assertEquals(PairingHandshakeState.EXPIRED, expired.state)
        assertTrue(expired.isTerminal)
    }

    @Test
    fun localRejectionIsTerminal() {
        val rejected = session().invitationSent(NOW + 1).rejectLocally(NOW + 2)
        assertEquals(PairingHandshakeState.REJECTED, rejected.state)
        assertEquals(PairingHandshakeState.REJECTED, rejected.refresh(NOW + 3).state)
    }

    @Test
    fun rejectionAfterExpiryReturnsExpired() {
        val expired = session().invitationSent(NOW + 1).rejectLocally(EXPIRY)
        assertEquals(PairingHandshakeState.EXPIRED, expired.state)
    }

    @Test
    fun failureCannotBeChangedByRefresh() {
        val failed = session().invitationSent(NOW + 1).fail(NOW + 2)
        assertSame(failed, failed.refresh(NOW + 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotTransitionFromTerminalState() {
        session().invitationSent(NOW + 1)
            .rejectLocally(NOW + 2)
            .peerConnected(NOW + 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRequestId() {
        PairingHandshakeSession.create("bad", EXPIRY, NOW)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLifetimeOverTwoMinutes() {
        PairingHandshakeSession.create(
            REQUEST_ID, NOW + PairingRequest.DEFAULT_TTL_MILLIS + 1, NOW
        )
    }

    @Test
    fun intermediateStateIsNotTrusted() {
        val awaitingPeerApproval = verifyingSession().verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signResponder(),
            PairingSignerRole.RESPONDER, NOW + 3
        ).session.approveLocally(NOW + 4)

        assertNotEquals(PairingHandshakeState.PAIRED, awaitingPeerApproval.state)
        assertNotEquals(PairingHandshakeDecision.BOTH_APPROVED, awaitingPeerApproval.decision)
    }

    private fun session() = PairingHandshakeSession.createForTranscript(transcript, NOW)

    private fun verifyingSession() = session().invitationSent(NOW + 1)
        .peerConnected(NOW + 2)

    private fun signResponder() = PairingIdentityCrypto.signTranscriptHash(
        responder.private, transcript.sha256, PairingSignerRole.RESPONDER
    )

    private fun createKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private companion object {
        const val REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val NOW = 10_000L
        const val EXPIRY = 130_000L
    }
}
