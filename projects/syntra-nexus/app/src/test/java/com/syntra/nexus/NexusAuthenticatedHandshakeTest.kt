package com.syntra.nexus

import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusAuthenticatedHandshakeTest {
    private val initiator = ecKeyPair()
    private val responder = ecKeyPair()
    private val transcript = PairingTranscript.create(
        requestId = "AAAAAAAAAAAAAAAAAAAAAA",
        expiresAtMillis = EXPIRY,
        initiatorPublicKeyEncoded = initiator.public.encoded,
        responderPublicKeyEncoded = responder.public.encoded,
        initiatorNonce = ByteArray(32) { (it + 1).toByte() },
        responderNonce = ByteArray(32) { (it + 65).toByte() },
        nowMillis = NOW
    )

    @Test
    fun signedFrameRoundTripsAndVerifiesAgainstExactTranscript() {
        val frame = signedFrame(sequence = 0)
        val decoded = NexusHandshakeFrameCodec.decode(NexusHandshakeFrameCodec.encode(frame))

        assertEquals(
            NexusHandshakeVerificationResult.VERIFIED,
            NexusHandshakeFrameVerifier.verify(
                decoded, transcript, PairingSignerRole.RESPONDER, responder.public, NOW + 1
            )
        )
    }

    @Test
    fun rejectsFrameWithTamperedSignature() {
        val encoded = NexusHandshakeFrameCodec.encode(signedFrame(0))
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        val decoded = NexusHandshakeFrameCodec.decode(encoded)

        assertEquals(
            NexusHandshakeVerificationResult.INVALID,
            NexusHandshakeFrameVerifier.verify(
                decoded, transcript, PairingSignerRole.RESPONDER, responder.public, NOW + 1
            )
        )
    }

    @Test
    fun rejectsWrongPeerKeyAndWrongRole() {
        val frame = signedFrame(0)
        assertEquals(
            NexusHandshakeVerificationResult.INVALID,
            NexusHandshakeFrameVerifier.verify(
                frame, transcript, PairingSignerRole.RESPONDER, initiator.public, NOW + 1
            )
        )
        assertEquals(
            NexusHandshakeVerificationResult.INVALID,
            NexusHandshakeFrameVerifier.verify(
                frame, transcript, PairingSignerRole.INITIATOR, responder.public, NOW + 1
            )
        )
    }

    @Test
    fun rejectsExpiredFrame() {
        val frame = signedFrame(0)
        assertEquals(
            NexusHandshakeVerificationResult.EXPIRED,
            NexusHandshakeFrameVerifier.verify(
                frame, transcript, PairingSignerRole.RESPONDER, responder.public, EXPIRY
            )
        )
    }

    @Test
    fun rejectsMalformedFrameLengthAndTrailingBytes() {
        val encoded = NexusHandshakeFrameCodec.encode(signedFrame(0))
        val truncated = encoded.copyOf(encoded.size - 1)
        val trailing = encoded + byteArrayOf(0)

        assertFalse(runCatching { NexusHandshakeFrameCodec.decode(truncated) }.isSuccess)
        assertFalse(runCatching { NexusHandshakeFrameCodec.decode(trailing) }.isSuccess)
    }

    @Test
    fun replayGuardAcceptsOnlyNextSequenceOnce() {
        val guard = NexusHandshakeReplayGuard()
        val first = signedFrame(0)
        val second = signedFrame(1)

        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(first, NOW + 1))
        assertEquals(NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER, guard.accept(first, NOW + 1))
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(second, NOW + 2))
        assertEquals(NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER, guard.accept(first, NOW + 3))
    }

    @Test
    fun replayGuardRejectsSequenceGapAndExpiredFrame() {
        val guard = NexusHandshakeReplayGuard()
        assertEquals(
            NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER,
            guard.accept(signedFrame(1), NOW + 1)
        )
        assertEquals(
            NexusReplayDecision.EXPIRED,
            guard.accept(signedFrame(0), EXPIRY)
        )
    }

    @Test
    fun replayGuardHasBoundedCapacityAndExpiresOldSessions() {
        val guard = NexusHandshakeReplayGuard(maxSessions = 1)
        val first = signedFrame(0)
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(first, NOW + 1))

        val otherTranscript = PairingTranscript.create(
            requestId = "BBBBBBBBBBBBBBBBBBBBBB",
            expiresAtMillis = EXPIRY + 10_000,
            initiatorPublicKeyEncoded = initiator.public.encoded,
            responderPublicKeyEncoded = responder.public.encoded,
            initiatorNonce = ByteArray(32) { (it + 2).toByte() },
            responderNonce = ByteArray(32) { (it + 66).toByte() },
            nowMillis = NOW
        )
        val second = NexusHandshakeFrame.createSigned(
            NexusHandshakeKind.IDENTITY_PROOF,
            otherTranscript,
            PairingSignerRole.RESPONDER,
            0,
            responder.private,
            SecureRandom()
        )
        assertEquals(NexusReplayDecision.CAPACITY_REACHED, guard.accept(second, NOW + 2))
        assertEquals(NexusReplayDecision.EXPIRED, guard.accept(second, EXPIRY))
    }

    @Test
    fun replayGuardPrunesExpiredSessionsBeforeCapacityCheck() {
        val shortExpiry = NOW + 30_000L
        val earlyTranscript = PairingTranscript.create(
            requestId = "CCCCCCCCCCCCCCCCCCCCCC",
            expiresAtMillis = shortExpiry,
            initiatorPublicKeyEncoded = initiator.public.encoded,
            responderPublicKeyEncoded = responder.public.encoded,
            initiatorNonce = ByteArray(32) { (it + 3).toByte() },
            responderNonce = ByteArray(32) { (it + 67).toByte() },
            nowMillis = NOW
        )
        val laterTranscript = PairingTranscript.create(
            requestId = "DDDDDDDDDDDDDDDDDDDDDD",
            expiresAtMillis = EXPIRY,
            initiatorPublicKeyEncoded = initiator.public.encoded,
            responderPublicKeyEncoded = responder.public.encoded,
            initiatorNonce = ByteArray(32) { (it + 4).toByte() },
            responderNonce = ByteArray(32) { (it + 68).toByte() },
            nowMillis = NOW
        )
        val guard = NexusHandshakeReplayGuard(maxSessions = 1)
        val early = NexusHandshakeFrame.createSigned(
            NexusHandshakeKind.IDENTITY_PROOF, earlyTranscript,
            PairingSignerRole.RESPONDER, 0, responder.private
        )
        val later = NexusHandshakeFrame.createSigned(
            NexusHandshakeKind.IDENTITY_PROOF, laterTranscript,
            PairingSignerRole.RESPONDER, 0, responder.private
        )
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(early, NOW + 1))
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(later, shortExpiry))
    }

    @Test
    fun verifiedHandshakeCombinesSignatureAndReplayChecks() {
        val guard = NexusHandshakeReplayGuard()
        val frame = signedFrame(0)
        assertEquals(
            NexusReplayDecision.ACCEPTED,
            NexusAuthenticatedHandshake.verifyAndAccept(
                frame, transcript, PairingSignerRole.RESPONDER,
                responder.public, NOW + 1, guard
            )
        )
        assertEquals(
            NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER,
            NexusAuthenticatedHandshake.verifyAndAccept(
                frame, transcript, PairingSignerRole.RESPONDER,
                responder.public, NOW + 1, guard
            )
        )
        val badBytes = NexusHandshakeFrameCodec.encode(signedFrame(0))
        badBytes[badBytes.lastIndex] = (badBytes.last().toInt() xor 1).toByte()
        val badFrame = NexusHandshakeFrameCodec.decode(badBytes)
        assertEquals(
            NexusReplayDecision.INVALID_SIGNATURE_OR_TRANSCRIPT,
            NexusAuthenticatedHandshake.verifyAndAccept(
                badFrame, transcript, PairingSignerRole.RESPONDER,
                responder.public, NOW + 1, NexusHandshakeReplayGuard()
            )
        )
    }

    @Test
    fun frameDefensivelyCopiesNonceAndSignature() {
        val frame = signedFrame(0)
        val nonce = frame.nonce
        val signature = frame.signature
        nonce[0] = (nonce[0].toInt() xor 1).toByte()
        signature[0] = (signature[0].toInt() xor 1).toByte()

        assertTrue(frame.nonce[0] != nonce[0])
        assertTrue(frame.signature[0] != signature[0])
    }

    private fun signedFrame(sequence: Long): NexusHandshakeFrame =
        NexusHandshakeFrame.createSigned(
            kind = NexusHandshakeKind.IDENTITY_PROOF,
            transcript = transcript,
            senderRole = PairingSignerRole.RESPONDER,
            sequence = sequence,
            privateKey = responder.private
        )

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private companion object {
        const val NOW = 20_000L
        const val EXPIRY = 140_000L
    }
}
