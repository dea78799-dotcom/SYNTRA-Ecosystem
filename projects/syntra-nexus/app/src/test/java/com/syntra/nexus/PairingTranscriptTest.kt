package com.syntra.nexus

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTranscriptTest {
    private val initiator = createKeyPair()
    private val responder = createKeyPair()
    private val initiatorNonce = ByteArray(32) { (it + 1).toByte() }
    private val responderNonce = ByteArray(32) { (it + 65).toByte() }

    @Test
    fun encodingAndHashAreDeterministic() {
        val first = createTranscript()
        val second = createTranscript()

        assertArrayEquals(first.encodedBytes, second.encodedBytes)
        assertArrayEquals(first.sha256, second.sha256)
        assertEquals32Bytes(first.sha256)
    }

    @Test
    fun changingAnyBoundFieldChangesTranscriptHash() {
        val baseline = createTranscript().sha256
        val changedRequest = createTranscript(requestId = "BBBBBBBBBBBBBBBBBBBBBB").sha256
        val changedExpiry = createTranscript(expiry = 101_001L).sha256
        val changedInitiatorNonce = createTranscript(
            initiatorNonce = ByteArray(32) { (it + 2).toByte() }
        ).sha256
        val changedResponderNonce = createTranscript(
            responderNonce = ByteArray(32) { (it + 66).toByte() }
        ).sha256
        val changedInitiator = createTranscript(initiatorKey = createKeyPair()).sha256
        val changedResponder = createTranscript(responderKey = createKeyPair()).sha256

        listOf(
            changedRequest, changedExpiry, changedInitiatorNonce,
            changedResponderNonce, changedInitiator, changedResponder
        ).forEach { assertFalse(baseline.contentEquals(it)) }
    }

    @Test
    fun peerOrderingIsBoundIntoTranscript() {
        val normal = createTranscript()
        val reversed = PairingTranscript.create(
            requestId = REQUEST_ID,
            expiresAtMillis = EXPIRY,
            initiatorPublicKeyEncoded = responder.public.encoded,
            responderPublicKeyEncoded = initiator.public.encoded,
            initiatorNonce = responderNonce,
            responderNonce = initiatorNonce,
            nowMillis = NOW
        )
        assertFalse(normal.sha256.contentEquals(reversed.sha256))
    }

    @Test
    fun returnedByteArraysCannotMutateTranscript() {
        val transcript = createTranscript()
        val originalHash = transcript.sha256
        transcript.sha256[0] = (transcript.sha256[0].toInt() xor 0xff).toByte()
        transcript.encodedBytes[0] = 0
        assertArrayEquals(originalHash, transcript.sha256)
        assertTrue(transcript.encodedBytes.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedRequestId() {
        createTranscript(requestId = "not-a-valid-id")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExpiredInvitation() {
        createTranscript(expiry = NOW)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvitationBeyondTwoMinutes() {
        createTranscript(expiry = NOW + PairingRequest.DEFAULT_TTL_MILLIS + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortNonce() {
        createTranscript(initiatorNonce = ByteArray(31))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEqualPeerNonces() {
        createTranscript(responderNonce = initiatorNonce.copyOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSamePublicIdentityForBothPeers() {
        createTranscript(responderKey = initiator)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyPublicKey() {
        PairingTranscript.create(
            REQUEST_ID, EXPIRY, ByteArray(0), responder.public.encoded,
            initiatorNonce, responderNonce, NOW
        )
    }

    private fun createTranscript(
        requestId: String = REQUEST_ID,
        expiry: Long = EXPIRY,
        initiatorKey: java.security.KeyPair = initiator,
        responderKey: java.security.KeyPair = responder,
        initiatorNonce: ByteArray = this.initiatorNonce,
        responderNonce: ByteArray = this.responderNonce
    ) = PairingTranscript.create(
        requestId, expiry, initiatorKey.public.encoded, responderKey.public.encoded,
        initiatorNonce, responderNonce, NOW
    )

    private fun createKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun assertEquals32Bytes(value: ByteArray) {
        assertTrue(value.size == 32)
    }

    private companion object {
        const val REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val NOW = 1_000L
        const val EXPIRY = 101_000L
    }
}
