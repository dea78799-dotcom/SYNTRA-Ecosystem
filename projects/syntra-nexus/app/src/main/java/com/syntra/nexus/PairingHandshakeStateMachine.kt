package com.syntra.nexus

/**
 * Pure local state machine for one pairing attempt.
 *
 * Cryptographic proof and approval checks are delegated to dedicated
 * verifiers. This class does not perform networking, persistence, one-time
 * token redemption, or distributed replay prevention.
 */
enum class PairingHandshakeState {
    CREATED,
    WAITING_FOR_PEER,
    VERIFYING_PEER,
    WAITING_FOR_LOCAL_APPROVAL,
    WAITING_FOR_PEER_APPROVAL,
    PAIRED,
    REJECTED,
    EXPIRED,
    FAILED
}

enum class PairingHandshakeDecision {
    NONE,
    LOCAL_APPROVED,
    PEER_APPROVED,
    BOTH_APPROVED
}

data class PairingHandshakeVerification(
    val session: PairingHandshakeSession,
    val proofResult: PairingProofResult
)

data class PairingHandshakeSession private constructor(
    val requestId: String,
    val expiresAtMillis: Long,
    val state: PairingHandshakeState,
    val decision: PairingHandshakeDecision,
    private val expectedTranscriptHashHex: String?
) {
    val isTerminal: Boolean
        get() = state == PairingHandshakeState.PAIRED ||
            state == PairingHandshakeState.REJECTED ||
            state == PairingHandshakeState.EXPIRED ||
            state == PairingHandshakeState.FAILED

    fun invitationSent(nowMillis: Long): PairingHandshakeSession {
        requireState(PairingHandshakeState.CREATED)
        return transitionTo(PairingHandshakeState.CREATED, PairingHandshakeState.WAITING_FOR_PEER, nowMillis)
    }

    fun peerConnected(nowMillis: Long): PairingHandshakeSession {
        requireState(PairingHandshakeState.WAITING_FOR_PEER)
        return transitionTo(PairingHandshakeState.WAITING_FOR_PEER, PairingHandshakeState.VERIFYING_PEER, nowMillis)
    }

    /**
     * Verifies that the proof key is exactly the identity bound to the
     * signer's role in this session's transcript before advancing state.
     */
    fun verifyPeerProofAndAdvance(
        transcript: PairingTranscript,
        peerPublicKeyEncoded: ByteArray,
        signatureBytes: ByteArray,
        signerRole: PairingSignerRole,
        nowMillis: Long
    ): PairingHandshakeVerification {
        requireState(PairingHandshakeState.VERIFYING_PEER)
        val expectedHash = expectedTranscriptHashHex
        if (transcript.requestId != requestId ||
            transcript.expiresAtMillis != expiresAtMillis ||
            expectedHash == null ||
            expectedHash != transcript.sha256.toHexString()
        ) {
            val failed = fail(nowMillis)
            return PairingHandshakeVerification(failed, PairingProofResult.IDENTITY_MISMATCH)
        }

        val proof = PairingProofVerifier.verifyPeerProof(
            transcript = transcript,
            peerPublicKeyEncoded = peerPublicKeyEncoded,
            signatureBytes = signatureBytes,
            signerRole = signerRole,
            nowMillis = nowMillis
        )
        val next = when (proof) {
            PairingProofResult.VERIFIED -> advanceAfterVerifiedPeerProof(nowMillis)
            PairingProofResult.EXPIRED -> refresh(nowMillis)
            PairingProofResult.IDENTITY_MISMATCH,
            PairingProofResult.INVALID_SIGNATURE -> fail(nowMillis)
        }
        return PairingHandshakeVerification(next, proof)
    }

    private fun advanceAfterVerifiedPeerProof(nowMillis: Long): PairingHandshakeSession {
        requireState(PairingHandshakeState.VERIFYING_PEER)
        return transitionTo(
            PairingHandshakeState.VERIFYING_PEER,
            PairingHandshakeState.WAITING_FOR_LOCAL_APPROVAL,
            nowMillis
        )
    }

    fun approveLocally(nowMillis: Long): PairingHandshakeSession {
        requireState(PairingHandshakeState.WAITING_FOR_LOCAL_APPROVAL)
        val current = transitionTo(
            PairingHandshakeState.WAITING_FOR_LOCAL_APPROVAL,
            PairingHandshakeState.WAITING_FOR_PEER_APPROVAL,
            nowMillis
        )
        return if (current.state == PairingHandshakeState.EXPIRED) current
        else current.copy(decision = PairingHandshakeDecision.LOCAL_APPROVED)
    }

    /**
     * Verifies a separately signed APPROVE decision for this exact transcript.
     * A plain transport message can never advance the session to PAIRED.
     */
    fun verifyPeerApprovalAndAdvance(
        transcript: PairingTranscript,
        peerPublicKeyEncoded: ByteArray,
        signatureBytes: ByteArray,
        signerRole: PairingSignerRole,
        nowMillis: Long
    ): PairingHandshakeVerification {
        requireState(PairingHandshakeState.WAITING_FOR_PEER_APPROVAL)
        val expectedHash = expectedTranscriptHashHex
        if (transcript.requestId != requestId ||
            transcript.expiresAtMillis != expiresAtMillis ||
            expectedHash == null ||
            expectedHash != transcript.sha256.toHexString()
        ) {
            val failed = fail(nowMillis)
            return PairingHandshakeVerification(failed, PairingProofResult.IDENTITY_MISMATCH)
        }

        val proof = PairingApprovalVerifier.verifyPeerApproval(
            transcript = transcript,
            peerPublicKeyEncoded = peerPublicKeyEncoded,
            signatureBytes = signatureBytes,
            signerRole = signerRole,
            nowMillis = nowMillis
        )
        val next = when (proof) {
            PairingProofResult.VERIFIED -> advanceAfterVerifiedPeerApproval(nowMillis)
            PairingProofResult.EXPIRED -> refresh(nowMillis)
            PairingProofResult.IDENTITY_MISMATCH,
            PairingProofResult.INVALID_SIGNATURE -> fail(nowMillis)
        }
        return PairingHandshakeVerification(next, proof)
    }

    private fun advanceAfterVerifiedPeerApproval(
        nowMillis: Long
    ): PairingHandshakeSession {
        requireState(PairingHandshakeState.WAITING_FOR_PEER_APPROVAL)
        val current = transitionTo(
            PairingHandshakeState.WAITING_FOR_PEER_APPROVAL,
            PairingHandshakeState.PAIRED,
            nowMillis
        )
        return if (current.state == PairingHandshakeState.EXPIRED) current
        else current.copy(decision = PairingHandshakeDecision.BOTH_APPROVED)
    }

    fun rejectLocally(nowMillis: Long): PairingHandshakeSession {
        require(!isTerminal) { "Pairing session is already terminal." }
        val current = withExpiry(nowMillis)
        if (current.state == PairingHandshakeState.EXPIRED) return current
        return current.copy(state = PairingHandshakeState.REJECTED)
    }

    fun fail(nowMillis: Long): PairingHandshakeSession {
        require(!isTerminal) { "Pairing session is already terminal." }
        val current = withExpiry(nowMillis)
        if (current.state == PairingHandshakeState.EXPIRED) return current
        return current.copy(state = PairingHandshakeState.FAILED)
    }

    fun refresh(nowMillis: Long): PairingHandshakeSession {
        if (isTerminal) return this
        return withExpiry(nowMillis)
    }

    private fun transitionTo(
        expected: PairingHandshakeState,
        next: PairingHandshakeState,
        nowMillis: Long
    ): PairingHandshakeSession {
        requireState(expected)
        val current = withExpiry(nowMillis)
        return if (current.state == PairingHandshakeState.EXPIRED) current
        else current.copy(state = next)
    }

    private fun withExpiry(nowMillis: Long): PairingHandshakeSession {
        require(nowMillis >= 0L) { "Current timestamp cannot be negative." }
        return if (nowMillis >= expiresAtMillis) {
            copy(state = PairingHandshakeState.EXPIRED)
        } else {
            this
        }
    }

    private fun requireState(expected: PairingHandshakeState) {
        require(!isTerminal) { "Pairing session is already terminal." }
        require(state == expected) {
            "Invalid transition: expected $expected but was $state."
        }
    }

    companion object {
        fun create(requestId: String, expiresAtMillis: Long, nowMillis: Long): PairingHandshakeSession {
            require(requestId.matches(Regex("^[A-Za-z0-9_-]{22}$"))) {
                "Pairing request ID has an invalid format."
            }
            require(nowMillis >= 0L) { "Current timestamp cannot be negative." }
            require(expiresAtMillis > nowMillis) { "Pairing invitation has expired." }
            require(expiresAtMillis - nowMillis <= PairingRequest.DEFAULT_TTL_MILLIS) {
                "Pairing invitation lifetime exceeds two minutes."
            }
            return PairingHandshakeSession(
                requestId = requestId,
                expiresAtMillis = expiresAtMillis,
                state = PairingHandshakeState.CREATED,
                decision = PairingHandshakeDecision.NONE,
                expectedTranscriptHashHex = null
            )
        }

        /**
         * Creates a handshake session pinned to the exact canonical transcript.
         * A session without this binding cannot accept peer proof.
         */
        fun createForTranscript(
            transcript: PairingTranscript,
            nowMillis: Long
        ): PairingHandshakeSession {
            require(nowMillis >= 0L) { "Current timestamp cannot be negative." }
            require(transcript.expiresAtMillis > nowMillis) {
                "Pairing invitation has expired."
            }
            require(transcript.expiresAtMillis - nowMillis <= PairingRequest.DEFAULT_TTL_MILLIS) {
                "Pairing invitation lifetime exceeds two minutes."
            }
            return PairingHandshakeSession(
                requestId = transcript.requestId,
                expiresAtMillis = transcript.expiresAtMillis,
                state = PairingHandshakeState.CREATED,
                decision = PairingHandshakeDecision.NONE,
                expectedTranscriptHashHex = transcript.sha256.toHexString()
            )
        }
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
