package com.syntra.nexus

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * The party whose device identity signs a canonical pairing transcript.
 * The role is also included in the signature domain to prevent reflection.
 */
enum class PairingSignerRole(val wireValue: Int) {
    INITIATOR(1),
    RESPONDER(2)
}

/**
 * Immutable, canonical transcript for a future mutual-authentication handshake.
 *
 * The transcript binds the invitation request, expiry, ordered peer identities,
 * and independent fresh nonces. Constructing it does not redeem an invitation,
 * authenticate a peer, or establish user consent/trust.
 */
class PairingTranscript private constructor(
    private val encoded: ByteArray,
    private val digest: ByteArray
) {
    val encodedBytes: ByteArray
        get() = encoded.copyOf()

    val sha256: ByteArray
        get() = digest.copyOf()

    companion object {
        private const val PROTOCOL_VERSION = 1
        private const val MAX_PUBLIC_KEY_BYTES = 512
        private const val NONCE_BYTES = 32
        private const val MAX_FUTURE_LIFETIME_MILLIS = PairingRequest.DEFAULT_TTL_MILLIS
        private val MAGIC = "SYNTRA-NEXUS-TRANSCRIPT".toByteArray(Charsets.US_ASCII)

        /**
         * Encodes fields in a fixed order using big-endian integers and
         * length-prefixed UTF-8/byte strings. The caller must supply a fresh
         * independently generated 32-byte nonce for each peer.
         */
        fun create(
            requestId: String,
            expiresAtMillis: Long,
            initiatorPublicKeyEncoded: ByteArray,
            responderPublicKeyEncoded: ByteArray,
            initiatorNonce: ByteArray,
            responderNonce: ByteArray,
            nowMillis: Long = System.currentTimeMillis()
        ): PairingTranscript {
            require(requestId.matches(Regex("^[A-Za-z0-9_-]{22}$"))) {
                "Pairing request ID has an invalid format."
            }
            require(nowMillis <= Long.MAX_VALUE - MAX_FUTURE_LIFETIME_MILLIS) {
                "Current timestamp is outside the supported range."
            }
            require(expiresAtMillis > nowMillis) { "Pairing invitation has expired." }
            require(expiresAtMillis <= nowMillis + MAX_FUTURE_LIFETIME_MILLIS) {
                "Pairing invitation lifetime exceeds two minutes."
            }
            requirePublicKey(initiatorPublicKeyEncoded, "Initiator")
            requirePublicKey(responderPublicKeyEncoded, "Responder")
            require(!initiatorPublicKeyEncoded.contentEquals(responderPublicKeyEncoded)) {
                "Initiator and responder identities must be different."
            }
            require(initiatorNonce.size == NONCE_BYTES) {
                "Initiator nonce must be exactly 32 bytes."
            }
            require(responderNonce.size == NONCE_BYTES) {
                "Responder nonce must be exactly 32 bytes."
            }
            require(!initiatorNonce.contentEquals(responderNonce)) {
                "Peer nonces must be independently generated."
            }

            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC.size)
                data.write(MAGIC)
                data.writeInt(PROTOCOL_VERSION)
                writeLengthPrefixed(data, requestId.toByteArray(Charsets.UTF_8))
                data.writeLong(expiresAtMillis)
                writeLengthPrefixed(data, initiatorPublicKeyEncoded)
                writeLengthPrefixed(data, responderPublicKeyEncoded)
                writeLengthPrefixed(data, initiatorNonce)
                writeLengthPrefixed(data, responderNonce)
            }

            val canonicalBytes = output.toByteArray()
            require(canonicalBytes.size <= 2_048) { "Pairing transcript is too large." }
            val hash = MessageDigest.getInstance("SHA-256").digest(canonicalBytes)
            return PairingTranscript(canonicalBytes, hash)
        }

        private fun requirePublicKey(encoded: ByteArray, peer: String) {
            require(encoded.isNotEmpty() && encoded.size <= MAX_PUBLIC_KEY_BYTES) {
                "$peer public key has an invalid length."
            }
        }

        private fun writeLengthPrefixed(output: DataOutputStream, value: ByteArray) {
            output.writeInt(value.size)
            output.write(value)
        }
    }
}
