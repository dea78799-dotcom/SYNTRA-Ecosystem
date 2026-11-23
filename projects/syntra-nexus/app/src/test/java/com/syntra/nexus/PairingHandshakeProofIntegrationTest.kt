package com.syntra.nexus

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingHandshakeProofIntegrationTest {
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
    fun validResponderProofAdvancesOnlyToLocalApproval() {
        val signature = PairingIdentityCrypto.signTranscriptHash(
            responder.private, transcript.sha256, PairingSignerRole.RESPONDER
        )
        val result = verifyingSession().verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signature,
            PairingSignerRole.RESPONDER, NOW + 3
        )

        assertEquals(PairingProofResult.VERIFIED, result.proofResult)
        assertEquals(
            PairingHandshakeState.WAITING_FOR_LOCAL_APPROVAL,
            result.session.state
        )
        assertEquals(PairingHandshakeDecision.NONE, result.session.decision)
    }

    @Test
    fun invalidSignatureFailsSession() {
        val signature = PairingIdentityCrypto.signTranscriptHash(
            initiator.private, transcript.sha256, PairingSignerRole.INITIATOR
        )
        val result = verifyingSession().verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signature,
            PairingSignerRole.RESPONDER, NOW + 3
        )

        assertEquals(PairingProofResult.INVALID_SIGNATURE, result.proofResult)
        assertEquals(PairingHandshakeState.FAILED, result.session.state)
    }

    @Test
    fun mismatchedTranscriptRequestFailsSession() {
        val otherTranscript = PairingTranscript.create(
            requestId = "BBBBBBBBBBBBBBBBBBBBBB",
            expiresAtMillis = EXPIRY,
            initiatorPublicKeyEncoded = initiator.public.encoded,
            responderPublicKeyEncoded = responder.public.encoded,
            initiatorNonce = ByteArray(32) { (it + 1).toByte() },
            responderNonce = ByteArray(32) { (it + 65).toByte() },
            nowMillis = NOW
        )
        val signature = PairingIdentityCrypto.signTranscriptHash(
            responder.private, otherTranscript.sha256, PairingSignerRole.RESPONDER
        )
        val result = verifyingSession().verifyPeerProofAndAdvance(
            otherTranscript, responder.public.encoded, signature,
            PairingSignerRole.RESPONDER, NOW + 3
        )

        assertEquals(PairingProofResult.IDENTITY_MISMATCH, result.proofResult)
        assertEquals(PairingHandshakeState.FAILED, result.session.state)
    }

    @Test
    fun expiredProofExpiresSessionWithoutTrust() {
        val signature = PairingIdentityCrypto.signTranscriptHash(
            responder.private, transcript.sha256, PairingSignerRole.RESPONDER
        )
        val result = verifyingSession().verifyPeerProofAndAdvance(
            transcript, responder.public.encoded, signature,
            PairingSignerRole.RESPONDER, EXPIRY
        )

        assertEquals(PairingProofResult.EXPIRED, result.proofResult)
        assertEquals(PairingHandshakeState.EXPIRED, result.session.state)
    }

    private fun verifyingSession() = PairingHandshakeSession.create(
        REQUEST_ID, EXPIRY, NOW
    ).invitationSent(NOW + 1)
        .peerConnected(NOW + 2)

    private fun createKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private companion object {
        const val REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val NOW = 20_000L
        const val EXPIRY = 140_000L
    }
}
