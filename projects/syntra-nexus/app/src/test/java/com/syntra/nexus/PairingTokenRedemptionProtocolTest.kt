package com.syntra.nexus

import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTokenRedemptionProtocolTest {
    private val identity = createKeyPair()
    private val invitation = PairingRequest.create(nowMillis = NOW, ttlMillis = 60_000L)

    @Test
    fun requestRoundTripsWithoutChangingBoundFields() {
        val request = PairingTokenRedemptionRequest.create(
            invitation, identity.public.encoded, NOW + 1
        )
        val decoded = PairingTokenRedemptionCodec.decode(
            PairingTokenRedemptionCodec.encode(request)
        )
        assertEquals(invitation.requestId, decoded.requestId)
        assertEquals(invitation.expiresAtMillis, decoded.expiresAtMillis)
        assertEquals(request.token, decoded.token)
        assertEquals(request.peerFingerprint, decoded.peerFingerprint)
        assertArrayEquals(identity.public.encoded, decoded.peerPublicKeyEncoded)
    }

    @Test
    fun validRequestRedeemsExactlyOnce() {
        val registry = registryWithInvitation()
        val request = PairingTokenRedemptionRequest.create(
            invitation, identity.public.encoded, NOW + 1
        )
        assertEquals(
            PairingTokenRedemptionDecision.REDEEMED,
            PairingTokenRedemptionVerifier.verifyAndRedeem(
                request, invitation, registry, NOW + 2
            )
        )
        assertEquals(
            PairingTokenRedemptionDecision.ALREADY_REDEEMED,
            PairingTokenRedemptionVerifier.verifyAndRedeem(
                request, invitation, registry, NOW + 3
            )
        )
    }

    @Test
    fun mismatchedRequestIdDoesNotConsumeInvitation() {
        val registry = registryWithInvitation()
        val altered = invitation.copy(requestId = "BBBBBBBBBBBBBBBBBBBBBB")
        val request = PairingTokenRedemptionRequest.create(
            altered, identity.public.encoded, NOW + 1
        )
        assertEquals(
            PairingTokenRedemptionDecision.INVITATION_MISMATCH,
            PairingTokenRedemptionVerifier.verifyAndRedeem(
                request, invitation, registry, NOW + 2
            )
        )
        assertEquals(
            PairingTokenRedemptionDecision.REDEEMED,
            registry.redeem(invitation.token, NOW + 3).toDecision()
        )
    }

    @Test
    fun mismatchedTokenDoesNotConsumeExpectedInvitation() {
        val registry = registryWithInvitation()
        val other = PairingRequest.create(nowMillis = NOW, ttlMillis = 60_000L)
        val altered = invitation.copy(token = other.token)
        val request = PairingTokenRedemptionRequest.create(
            altered, identity.public.encoded, NOW + 1
        )
        assertEquals(
            PairingTokenRedemptionDecision.INVALID_TOKEN,
            PairingTokenRedemptionVerifier.verifyAndRedeem(
                request, invitation, registry, NOW + 2
            )
        )
        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.REDEEMED,
            registry.redeem(invitation.token, NOW + 3)
        )
    }

    @Test
    fun expiredRequestIsRejectedBeforeTokenRedemption() {
        val registry = registryWithInvitation()
        val request = PairingTokenRedemptionRequest.create(
            invitation, identity.public.encoded, NOW + 1
        )
        assertEquals(
            PairingTokenRedemptionDecision.EXPIRED,
            PairingTokenRedemptionVerifier.verifyAndRedeem(
                request, invitation, registry, invitation.expiresAtMillis
            )
        )
        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.REDEEMED,
            registry.redeem(invitation.token, invitation.expiresAtMillis - 1)
        )
    }

    @Test
    fun codecRejectsTruncatedTrailingAndWrongMarkerPackets() {
        val request = PairingTokenRedemptionRequest.create(
            invitation, identity.public.encoded, NOW + 1
        )
        val encoded = PairingTokenRedemptionCodec.encode(request)
        assertTrue(runCatching {
            PairingTokenRedemptionCodec.decode(encoded.copyOf(encoded.size - 1))
        }.isFailure)
        assertTrue(runCatching {
            PairingTokenRedemptionCodec.decode(encoded + byteArrayOf(0))
        }.isFailure)
        val wrongMagic = encoded.copyOf().also { it[0] = 0 }
        assertTrue(runCatching {
            PairingTokenRedemptionCodec.decode(wrongMagic)
        }.isFailure)
    }

    @Test
    fun codecRejectsUnknownVersionAndInvalidKeyLength() {
        val request = PairingTokenRedemptionRequest.create(
            invitation, identity.public.encoded, NOW + 1
        )
        val encoded = PairingTokenRedemptionCodec.encode(request)
        val unknownVersion = encoded.copyOf().also { it[4] = 99 }
        assertTrue(runCatching {
            PairingTokenRedemptionCodec.decode(unknownVersion)
        }.isFailure)

        val invalidLength = encoded.copyOf()
        ByteBuffer.wrap(invalidLength).putShort(
            PairingTokenRedemptionCodec.FIXED_HEADER_BYTES - 2,
            0.toShort()
        )
        assertTrue(runCatching {
            PairingTokenRedemptionCodec.decode(invalidLength)
        }.isFailure)
    }

    @Test
    fun publicKeyIsDefensivelyCopied() {
        val mutable = identity.public.encoded
        val request = PairingTokenRedemptionRequest.create(
            invitation, mutable, NOW + 1
        )
        val expected = mutable.copyOf()
        mutable.fill(0)
        val exposed = request.peerPublicKeyEncoded
        exposed.fill(1)
        assertArrayEquals(expected, request.peerPublicKeyEncoded)
    }

    @Test
    fun rejectsNonEcPeerIdentity() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        assertTrue(runCatching {
            PairingTokenRedemptionRequest.create(
                invitation, rsa.public.encoded, NOW + 1
            )
        }.isFailure)
    }

    @Test
    fun encodedPayloadIsBounded() {
        val request = PairingTokenRedemptionRequest.create(
            invitation, identity.public.encoded, NOW + 1
        )
        val encoded = PairingTokenRedemptionCodec.encode(request)
        assertTrue(encoded.size <= PairingTokenRedemptionCodec.MAX_PACKET_BYTES)
        assertNotEquals(0, encoded.size)
    }

    private fun registryWithInvitation(): OneTimePairingTokenRegistry =
        OneTimePairingTokenRegistry().also {
            it.register(
                ParsedPairingInvitation(
                    invitation.requestId,
                    invitation.token,
                    invitation.expiresAtMillis
                ),
                NOW + 1
            )
        }

    private fun OneTimePairingTokenRegistry.RedemptionResult.toDecision() = when (this) {
        OneTimePairingTokenRegistry.RedemptionResult.REDEEMED -> PairingTokenRedemptionDecision.REDEEMED
        OneTimePairingTokenRegistry.RedemptionResult.ALREADY_REDEEMED -> PairingTokenRedemptionDecision.ALREADY_REDEEMED
        OneTimePairingTokenRegistry.RedemptionResult.EXPIRED -> PairingTokenRedemptionDecision.EXPIRED
        OneTimePairingTokenRegistry.RedemptionResult.REVOKED -> PairingTokenRedemptionDecision.REVOKED
        OneTimePairingTokenRegistry.RedemptionResult.UNKNOWN_TOKEN -> PairingTokenRedemptionDecision.UNKNOWN_TOKEN
        OneTimePairingTokenRegistry.RedemptionResult.INVALID_TOKEN -> PairingTokenRedemptionDecision.INVALID_TOKEN
    }

    private fun createKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private companion object {
        const val NOW = 50_000L
    }
}
