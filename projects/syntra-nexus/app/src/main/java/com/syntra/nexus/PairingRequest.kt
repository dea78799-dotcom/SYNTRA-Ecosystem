package com.syntra.nexus

import java.security.SecureRandom
import java.util.Base64

/**
 * Short-lived invitation payload for the local pairing flow.
 *
 * This is only a QR invitation prototype: it does not authenticate a peer,
 * establish a network connection, or create a trusted device record.
 */
data class PairingRequest(
    val requestId: String,
    val token: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
) {
    val payload: String
        get() = "syntra-nexus://pair?v=1&id=$requestId&token=$token&exp=$expiresAtMillis"

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis >= expiresAtMillis

    companion object {
        const val DEFAULT_TTL_MILLIS = 120_000L
        private const val REQUEST_ID_BYTES = 16
        private const val TOKEN_BYTES = 32

        fun create(
            nowMillis: Long = System.currentTimeMillis(),
            ttlMillis: Long = DEFAULT_TTL_MILLIS,
            secureRandom: SecureRandom = SecureRandom()
        ): PairingRequest {
            require(ttlMillis in 1..DEFAULT_TTL_MILLIS) {
                "Pairing invitation lifetime must be between 1 ms and 2 minutes."
            }
            require(nowMillis <= Long.MAX_VALUE - ttlMillis) {
                "Pairing invitation expiry exceeds the supported timestamp range."
            }
            val requestIdBytes = ByteArray(REQUEST_ID_BYTES).also(secureRandom::nextBytes)
            val tokenBytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
            return PairingRequest(
                requestId = Base64.getUrlEncoder().withoutPadding().encodeToString(requestIdBytes),
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes),
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + ttlMillis
            )
        }
    }
}
