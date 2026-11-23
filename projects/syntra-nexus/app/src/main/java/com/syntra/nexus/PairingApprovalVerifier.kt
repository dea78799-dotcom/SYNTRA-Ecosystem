package com.syntra.nexus

import java.security.MessageDigest
import java.security.PrivateKey

/**
 * Verifies a peer's explicit APPROVE decision for this exact transcript.
 * The approval signature is distinct from the initial identity proof.
 */
object PairingApprovalVerifier {
    private val APPROVAL_DOMAIN =
        "SYNTRA-NEXUS-APPROVAL-V1\u0000".toByteArray(Charsets.UTF_8)
    private val APPROVE = "APPROVE".toByteArray(Charsets.US_ASCII)

    fun createApprovalSignature(
        privateKey: PrivateKey,
        transcript: PairingTranscript,
        signerRole: PairingSignerRole
    ): ByteArray {
        val digest = approvalDigest(transcript.sha256, signerRole)
        return PairingIdentityCrypto.signTranscriptHash(privateKey, digest, signerRole)
    }

    fun verifyPeerApproval(
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

        val digest = approvalDigest(transcript.sha256, signerRole)
        val valid = PairingIdentityCrypto.verifyTranscriptHash(
            publicKeyEncoded = expectedKey,
            transcriptHash = digest,
            signatureBytes = signatureBytes,
            role = signerRole
        )
        return if (valid) PairingProofResult.VERIFIED
        else PairingProofResult.INVALID_SIGNATURE
    }

    private fun approvalDigest(
        transcriptHash: ByteArray,
        signerRole: PairingSignerRole
    ): ByteArray {
        require(transcriptHash.size == 32) {
            "Pairing transcript hash must be 32 bytes."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(APPROVAL_DOMAIN)
        digest.update(signerRole.wireValue.toByte())
        digest.update(transcriptHash)
        digest.update(APPROVE)
        return digest.digest()
    }
}
