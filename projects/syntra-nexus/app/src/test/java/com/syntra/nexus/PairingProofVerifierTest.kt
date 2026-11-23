package com.syntra.nexus

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingProofVerifierTest {
    private val initiator = createKeyPair()
    private val responder = createKeyPair()
    private val initiatorNonce = ByteArray(32) { (it + 1).toByte() }
    private val responderNonce = ByteArray(32) { (it + 65).toByte() }
    private val transcript = PairingTranscript.create(
        requestId = REQUEST_ID,
        expiresAtMillis = EXPIRY,
        initiatorPublicKeyEncoded = initiator.public.encoded,
        responderPublicKeyEncoded = responder.public.encoded,
        initiatorNonce = initiatorNonce,
        responderNonce = responderNonce,
        nowMillis = NOW
    )

    @Test
    fun verifiesBothInitiatorAndResponderProofs() {
        val initiatorSignature = sign(initiator, PairingSignerRole.INITIATOR)
        val responderSignature = sign(responder, PairingSignerRole.RESPONDER)

        assertEquals(
            PairingProofResult.VERIFIED,
            PairingProofVerifier.verifyPeerProof(
                transcript, initiator.public.encoded, initiatorSignature,
                PairingSignerRole.INITIATOR, NOW + 1
            )
        )
        assertEquals(
            PairingProofResult.VERIFIED,
            PairingProofVerifier.verifyPeerProof(
                transcript, responder.public.encoded, responderSignature,
                PairingSignerRole.RESPONDER, NOW + 1
            )
        )
    }

    @Test
    fun rejectsPublicKeyNotBoundToSignerRole() {
        val signature = sign(responder, PairingSignerRole.RESPONDER)
        assertEquals(
            PairingProofResult.IDENTITY_MISMATCH,
            PairingProofVerifier.verifyPeerProof(
                transcript, initiator.public.encoded, signature,
                PairingSignerRole.RESPONDER, NOW + 1
            )
        )
    }

    @Test
    fun rejectsSignatureWithWrongRole() {
        val signature = sign(responder, PairingSignerRole.RESPONDER)
        assertEquals(
            PairingProofResult.INVALID_SIGNATURE,
            PairingProofVerifier.verifyPeerProof(
                transcript, initiator.public.encoded, signature,
                PairingSignerRole.INITIATOR, NOW + 1
            )
        )
    }

    @Test
    fun rejectsTamperedTranscriptHash() {
        val altered = PairingTranscript.create(
            requestId = REQUEST_ID,
            expiresAtMillis = EXPIRY,
            initiatorPublicKeyEncoded = initiator.public.encoded,
            responderPublicKeyEncoded = responder.public.encoded,
            initiatorNonce = initiatorNonce,
            responderNonce = ByteArray(32) { (it + 66).toByte() },
            nowMillis = NOW
        )
        val signature = sign(responder, PairingSignerRole.RESPONDER)
        assertFalse(transcript.sha256.contentEquals(altered.sha256))
        assertEquals(
            PairingProofResult.INVALID_SIGNATURE,
            PairingProofVerifier.verifyPeerProof(
                altered, responder.public.encoded, signature,
                PairingSignerRole.RESPONDER, NOW + 1
            )
        )
    }

    @Test
    fun rejectsExpiredProof() {
        val signature = sign(responder, PairingSignerRole.RESPONDER)
        assertEquals(
            PairingProofResult.EXPIRED,
            PairingProofVerifier.verifyPeerProof(
                transcript, responder.public.encoded, signature,
                PairingSignerRole.RESPONDER, EXPIRY
            )
        )
    }

    @Test
    fun publicKeyAccessorReturnsDefensiveCopy() {
        val first = transcript.publicKeyFor(PairingSignerRole.INITIATOR)
        first[0] = (first[0].toInt() xor 0xff).toByte()
        assertTrue(
            transcript.publicKeyFor(PairingSignerRole.INITIATOR)
                .contentEquals(initiator.public.encoded)
        )
    }

    private fun sign(pair: KeyPair, role: PairingSignerRole) =
        PairingIdentityCrypto.signTranscriptHash(pair.private, transcript.sha256, role)

    private fun createKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private companion object {
        const val REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val NOW = 50_000L
        const val EXPIRY = 150_000L
    }
}
