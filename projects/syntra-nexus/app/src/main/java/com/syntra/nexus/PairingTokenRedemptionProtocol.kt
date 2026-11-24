package com.syntra.nexus

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

enum class PairingTokenRedemptionDecision {
    REDEEMED,
    ALREADY_REDEEMED,
    EXPIRED,
    REVOKED,
    UNKNOWN_TOKEN,
    INVALID_TOKEN,
    INVITATION_MISMATCH,
    INVALID_PEER_IDENTITY,
    PERSISTENCE_FAILURE
}

/**
 * A bounded request that proves possession of a QR invitation token.
 *
 * The token is a bearer credential. The encoded request must only be sent
 * after a mutually authenticated, peer-pinned TLS session is established.
 */
class PairingTokenRedemptionRequest private constructor(
    val requestId: String,
    val expiresAtMillis: Long,
    internal val token: String,
    peerPublicKeyEncoded: ByteArray
) {
    private val peerKeyBytes = peerPublicKeyEncoded.copyOf()

    val peerPublicKeyEncoded: ByteArray
        get() = peerKeyBytes.copyOf()

    val peerFingerprint: String
        get() = PairingIdentityCrypto.fingerprint(peerKeyBytes)

    companion object {
        private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
        private const val TOKEN_BYTES = 32
        private const val MAX_PUBLIC_KEY_BYTES = 512

        fun create(
            invitation: PairingRequest,
            peerPublicKeyEncoded: ByteArray,
            nowMillis: Long
        ): PairingTokenRedemptionRequest {
            require(nowMillis >= 0L)
            require(invitation.requestId.matches(REQUEST_ID_PATTERN))
            require(invitation.expiresAtMillis > nowMillis)
            require(invitation.expiresAtMillis - nowMillis <= PairingRequest.DEFAULT_TTL_MILLIS)
            validateToken(invitation.token)
            validatePeerKey(peerPublicKeyEncoded)
            return PairingTokenRedemptionRequest(
                invitation.requestId,
                invitation.expiresAtMillis,
                invitation.token,
                peerPublicKeyEncoded
            )
        }

        internal fun fromWire(
            requestId: String,
            expiresAtMillis: Long,
            token: String,
            peerPublicKeyEncoded: ByteArray
        ): PairingTokenRedemptionRequest {
            require(requestId.matches(REQUEST_ID_PATTERN))
            require(expiresAtMillis > 0L)
            validateToken(token)
            validatePeerKey(peerPublicKeyEncoded)
            return PairingTokenRedemptionRequest(
                requestId, expiresAtMillis, token, peerPublicKeyEncoded
            )
        }

        internal fun validateToken(token: String) {
            require(token.matches(TOKEN_PATTERN)) { "Invitation token has an invalid format." }
            val decoded = Base64.getUrlDecoder().decode(token)
            require(decoded.size == TOKEN_BYTES) { "Invitation token has an invalid length." }
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == token) {
                "Invitation token is not canonical base64url."
            }
        }

        internal fun validatePeerKey(encoded: ByteArray) {
            require(encoded.isNotEmpty() && encoded.size <= MAX_PUBLIC_KEY_BYTES) {
                "Peer public key size is outside the permitted range."
            }
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(encoded)) as? ECPublicKey
                ?: throw IllegalArgumentException("Peer identity must be an EC public key.")
            require(key.encoded.contentEquals(encoded)) { "Peer public key encoding is not canonical." }
            require(key.params.curve.field.fieldSize == 256) {
                "Peer identity must use the P-256 curve."
            }
        }
    }
}

/**
 * Wire format (big-endian):
 * SNR1 | version[1] | requestId[22] | expiry[8] | token[43] | keyLength[2] | key[N]
 *
 * The token is intentionally present only inside the request payload. Callers
 * must keep this payload inside mutually authenticated TLS and must not log it.
 */
object PairingTokenRedemptionCodec {
    const val PROTOCOL_VERSION = 1
    const val REQUEST_ID_BYTES = 22
    const val TOKEN_BYTES = 43
    const val FIXED_HEADER_BYTES = 4 + 1 + REQUEST_ID_BYTES + 8 + TOKEN_BYTES + 2
    const val MAX_PUBLIC_KEY_BYTES = 512
    const val MAX_PACKET_BYTES = FIXED_HEADER_BYTES + MAX_PUBLIC_KEY_BYTES

    private val MAGIC = byteArrayOf(0x53, 0x4e, 0x52, 0x31) // SNR1

    fun encode(request: PairingTokenRedemptionRequest): ByteArray {
        val requestId = request.requestId.toByteArray(StandardCharsets.US_ASCII)
        val token = request.token.toByteArray(StandardCharsets.US_ASCII)
        val publicKey = request.peerPublicKeyEncoded
        require(requestId.size == REQUEST_ID_BYTES && token.size == TOKEN_BYTES)
        require(publicKey.size in 1..MAX_PUBLIC_KEY_BYTES)
        return ByteBuffer.allocate(FIXED_HEADER_BYTES + publicKey.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(PROTOCOL_VERSION.toByte())
            .put(requestId)
            .putLong(request.expiresAtMillis)
            .put(token)
            .putShort(publicKey.size.toShort())
            .put(publicKey)
            .array()
    }

    fun decode(encoded: ByteArray): PairingTokenRedemptionRequest {
        require(encoded.size in (FIXED_HEADER_BYTES + 1)..MAX_PACKET_BYTES) {
            "Redemption packet length is outside the permitted range."
        }
        val input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size)
        input.get(magic)
        require(magic.contentEquals(MAGIC)) { "Invalid redemption packet marker." }
        require((input.get().toInt() and 0xff) == PROTOCOL_VERSION) {
            "Unsupported redemption protocol version."
        }
        val requestIdBytes = ByteArray(REQUEST_ID_BYTES)
        input.get(requestIdBytes)
        val requestId = String(requestIdBytes, StandardCharsets.US_ASCII)
        val expiry = input.long
        val tokenBytes = ByteArray(TOKEN_BYTES)
        input.get(tokenBytes)
        val token = String(tokenBytes, StandardCharsets.US_ASCII)
        val keyLength = input.short.toInt() and 0xffff
        require(keyLength in 1..MAX_PUBLIC_KEY_BYTES)
        require(input.remaining() == keyLength) {
            "Declared public-key length does not match packet length."
        }
        val publicKey = ByteArray(keyLength)
        input.get(publicKey)
        require(!input.hasRemaining())
        return PairingTokenRedemptionRequest.fromWire(requestId, expiry, token, publicKey)
    }
}

/**
 * Issuer-side verifier. The caller must invoke this only after the request
 * arrived through mutually authenticated TLS with the expected pinned peer.
 */
object PairingTokenRedemptionVerifier {
    fun verifyAndRedeem(
        request: PairingTokenRedemptionRequest,
        expectedInvitation: PairingRequest,
        registry: OneTimePairingTokenRegistry,
        nowMillis: Long
    ): PairingTokenRedemptionDecision {
        require(nowMillis >= 0L)
        if (nowMillis >= expectedInvitation.expiresAtMillis ||
            nowMillis >= request.expiresAtMillis
        ) return PairingTokenRedemptionDecision.EXPIRED

        if (request.requestId != expectedInvitation.requestId ||
            request.expiresAtMillis != expectedInvitation.expiresAtMillis
        ) return PairingTokenRedemptionDecision.INVITATION_MISMATCH

        val requestToken = request.token.toByteArray(StandardCharsets.US_ASCII)
        val expectedToken = expectedInvitation.token.toByteArray(StandardCharsets.US_ASCII)
        if (!MessageDigest.isEqual(requestToken, expectedToken)) {
            return PairingTokenRedemptionDecision.INVALID_TOKEN
        }

        try {
            PairingTokenRedemptionRequest.validatePeerKey(request.peerPublicKeyEncoded)
        } catch (_: Exception) {
            return PairingTokenRedemptionDecision.INVALID_PEER_IDENTITY
        }

        return try {
            when (registry.redeem(expectedInvitation.token, nowMillis)) {
                OneTimePairingTokenRegistry.RedemptionResult.REDEEMED ->
                    PairingTokenRedemptionDecision.REDEEMED
                OneTimePairingTokenRegistry.RedemptionResult.ALREADY_REDEEMED ->
                    PairingTokenRedemptionDecision.ALREADY_REDEEMED
                OneTimePairingTokenRegistry.RedemptionResult.EXPIRED ->
                    PairingTokenRedemptionDecision.EXPIRED
                OneTimePairingTokenRegistry.RedemptionResult.REVOKED ->
                    PairingTokenRedemptionDecision.REVOKED
                OneTimePairingTokenRegistry.RedemptionResult.UNKNOWN_TOKEN ->
                    PairingTokenRedemptionDecision.UNKNOWN_TOKEN
                OneTimePairingTokenRegistry.RedemptionResult.INVALID_TOKEN ->
                    PairingTokenRedemptionDecision.INVALID_TOKEN
            }
        } catch (_: Exception) {
            PairingTokenRedemptionDecision.PERSISTENCE_FAILURE
        }
    }
}
