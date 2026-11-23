package com.syntra.nexus

/**
 * Pure local state machine for one pairing attempt.
 *
 * Call peerProofVerified() only after the caller has verified the peer's
 * role-bound signature against the exact canonical transcript and checked
 * that the invitation has not been redeemed. This class does not perform
 * cryptography, networking, persistence, or remote token redemption.
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

data class PairingHandshakeSession private constructor(
    val requestId: String,
    val expiresAtMillis: Long,
    val state: PairingHandshakeState,
    val decision: PairingHandshakeDecision
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
     * This is a trust boundary: invoke only after cryptographic verification
     * of the responder identity and role-bound transcript signature succeeds.
     */
    fun peerProofVerified(nowMillis: Long): PairingHandshakeSession {
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
     * Call only after the authenticated peer has explicitly approved this
     * exact request. A transport message alone is not sufficient evidence.
     */
    fun peerApprovalVerified(nowMillis: Long): PairingHandshakeSession {
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
                decision = PairingHandshakeDecision.NONE
            )
        }
    }
}
