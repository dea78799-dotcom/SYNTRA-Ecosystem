package com.syntra.nexus

import java.security.MessageDigest

enum class PairingProofResult {
    VERIFIED,
    EXPIRED,
    IDENTITY_MISMATCH,
    INVALID_SIGNATURE
}

/**
 * Verifies a peer's signature against the exact canonical transcript and
 * the public identity assigned to that peer's protocol role.
 *
 * This is cryptographic proof verification only. It does not establish user
 * approval, redeem a one-time invitation, prevent distributed replay, or
 * persist a trusted-device record.
 */
object PairingProofVerifier {
    fun verifyPeerProof(
        transcript: PairingTranscript,
        peerPublicKeyEncoded: ByteArray,
        signatureBytes: ByteArray,
        signerRole: PairingSignerRole,
        nowMillis: Long
    ): PairingProofResult {
        if (nowMillis < 0L || nowMillis >= transcript.expiresAtMillis) {
            return PairingProofResult.EXPIRED
        }

        val expectedKey = transcript.publicKeyFor(signerRole)
        if (!MessageDigest.isEqual(expectedKey, peerPublicKeyEncoded)) {
            return PairingProofResult.IDENTITY_MISMATCH
        }

        val valid = PairingIdentityCrypto.verifyTranscriptHash(
            publicKeyEncoded = expectedKey,
            transcriptHash = transcript.sha256,
            signatureBytes = signatureBytes,
            role = signerRole
        )
        return if (valid) PairingProofResult.VERIFIED
        else PairingProofResult.INVALID_SIGNATURE
    }
}
