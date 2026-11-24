package com.syntra.nexus

/**
 * Durable-workflow boundary for a single pairing attempt.
 *
 * Cryptographic verification remains in PairingHandshakeSession. This
 * coordinator accepts only that verifier-produced session state; it never
 * accepts a peer-approved Boolean from the transport layer.
 */
enum class PairingTransactionState {
    CREATED,
    TOKEN_REDEEMED,
    IDENTITY_VERIFIED,
    LOCAL_APPROVAL_PENDING,
    PAIRED,
    ABORTED,
    EXPIRED,
    RECOVERY_REQUIRED
}

enum class PairingTransactionResult {
    UPDATED,
    ALREADY_TERMINAL,
    INVALID_TRANSITION,
    EXPIRED,
    PERSISTENCE_FAILURE
}

data class PairingTransactionSnapshot(
    val requestId: String,
    val expiresAtMillis: Long,
    val transcriptHashHex: String,
    val peerFingerprint: String,
    val state: PairingTransactionState,
    val updatedAtMillis: Long
)

interface PairingTransactionPersistence {
    fun load(): PairingTransactionSnapshot?
    fun save(snapshot: PairingTransactionSnapshot)
    fun clear()
}

/**
 * Single-transaction coordinator. Persistence is committed before in-memory
 * state changes. A restored non-terminal transaction is downgraded to
 * RECOVERY_REQUIRED: process memory is not proof of fresh peer consent.
 */
class PairingTransactionCoordinator(
    private val persistence: PairingTransactionPersistence? = null
) {
    @Volatile
    private var current: PairingTransactionSnapshot? = null

    init {
        val restored = persistence?.load()
        if (restored != null) {
            validate(restored)
            val recovered = if (restored.state == PairingTransactionState.ABORTED ||
                restored.state == PairingTransactionState.EXPIRED) restored
            else restored.copy(state = PairingTransactionState.RECOVERY_REQUIRED)
            if (recovered != restored) persistence?.save(recovered)
            current = recovered
        }
    }

    @Synchronized
    fun snapshot(): PairingTransactionSnapshot? = current

    @Synchronized
    fun begin(
        requestId: String,
        expiresAtMillis: Long,
        transcriptHashHex: String,
        peerFingerprint: String,
        nowMillis: Long
    ): PairingTransactionResult {
        if (nowMillis < 0L || expiresAtMillis <= nowMillis ||
            expiresAtMillis - nowMillis > PairingRequest.DEFAULT_TTL_MILLIS ||
            !requestId.matches(Regex("^[A-Za-z0-9_-]{22}$")) ||
            !transcriptHashHex.matches(Regex("^[a-f0-9]{64}$")) ||
            !peerFingerprint.matches(Regex("^[a-f0-9]{64}$"))
        ) return PairingTransactionResult.INVALID_TRANSITION

        val old = current
        if (old != null && old.state != PairingTransactionState.ABORTED &&
            old.state != PairingTransactionState.EXPIRED
        ) return PairingTransactionResult.INVALID_TRANSITION

        return commit(PairingTransactionSnapshot(
            requestId, expiresAtMillis, transcriptHashHex, peerFingerprint,
            PairingTransactionState.CREATED, nowMillis
        ))
    }

    @Synchronized
    fun markTokenRedeemed(nowMillis: Long): PairingTransactionResult =
        advance(PairingTransactionState.CREATED, PairingTransactionState.TOKEN_REDEEMED, nowMillis)

    /**
     * Must be called only after PairingHandshakeSession has verified the
     * responder proof against the exact transcript and pinned key.
     */
    @Synchronized
    fun markPeerIdentityVerified(
        verifiedSession: PairingHandshakeSession,
        transcript: PairingTranscript,
        nowMillis: Long
    ): PairingTransactionResult {
        val active = current ?: return PairingTransactionResult.INVALID_TRANSITION
        if (!verifiedSession.isBoundToTranscript(transcript) ||
            verifiedSession.state != PairingHandshakeState.WAITING_FOR_LOCAL_APPROVAL &&
            verifiedSession.state != PairingHandshakeState.WAITING_FOR_PEER_APPROVAL &&
            verifiedSession.state != PairingHandshakeState.PAIRED ||
            transcript.requestId != active.requestId ||
            transcript.expiresAtMillis != active.expiresAtMillis ||
            transcript.sha256.toHexString() != active.transcriptHashHex ||
            PairingIdentityCrypto.fingerprint(transcript.publicKeyFor(PairingSignerRole.RESPONDER)) != active.peerFingerprint
        ) return PairingTransactionResult.INVALID_TRANSITION
        return advance(
            PairingTransactionState.TOKEN_REDEEMED,
            PairingTransactionState.IDENTITY_VERIFIED,
            nowMillis
        )
    }

    /** Local approval is recorded only after the user explicitly approves. */
    @Synchronized
    fun markLocalApproval(
        verifiedSession: PairingHandshakeSession,
        transcript: PairingTranscript,
        nowMillis: Long
    ): PairingTransactionResult {
        val active = current ?: return PairingTransactionResult.INVALID_TRANSITION
        if (!verifiedSession.isBoundToTranscript(transcript) ||
            verifiedSession.state != PairingHandshakeState.WAITING_FOR_PEER_APPROVAL &&
            verifiedSession.state != PairingHandshakeState.PAIRED ||
            transcript.sha256.toHexString() != active.transcriptHashHex
        ) return PairingTransactionResult.INVALID_TRANSITION
        return advance(
            PairingTransactionState.IDENTITY_VERIFIED,
            PairingTransactionState.LOCAL_APPROVAL_PENDING,
            nowMillis
        )
    }

    /**
     * The only path to PAIRED. The supplied session must have independently
     * verified the peer's signed approval and carry BOTH_APPROVED.
     */
    @Synchronized
    fun complete(
        verifiedSession: PairingHandshakeSession,
        transcript: PairingTranscript,
        nowMillis: Long
    ): PairingTransactionResult {
        val active = current ?: return PairingTransactionResult.INVALID_TRANSITION
        if (!verifiedSession.isBoundToTranscript(transcript) ||
            verifiedSession.state != PairingHandshakeState.PAIRED ||
            verifiedSession.decision != PairingHandshakeDecision.BOTH_APPROVED ||
            transcript.sha256.toHexString() != active.transcriptHashHex ||
            transcript.requestId != active.requestId ||
            transcript.expiresAtMillis != active.expiresAtMillis
        ) return PairingTransactionResult.INVALID_TRANSITION
        return advance(
            PairingTransactionState.LOCAL_APPROVAL_PENDING,
            PairingTransactionState.PAIRED,
            nowMillis
        )
    }

    @Synchronized
    fun abort(nowMillis: Long): PairingTransactionResult {
        val active = current ?: return PairingTransactionResult.INVALID_TRANSITION
        if (isTerminal(active.state)) return PairingTransactionResult.ALREADY_TERMINAL
        if (nowMillis < 0L) return PairingTransactionResult.INVALID_TRANSITION
        if (nowMillis >= active.expiresAtMillis) return expire(active, nowMillis)
        return commit(active.copy(state = PairingTransactionState.ABORTED, updatedAtMillis = nowMillis))
    }

    @Synchronized
    fun clearTerminal(): PairingTransactionResult {
        val active = current ?: return PairingTransactionResult.ALREADY_TERMINAL
        if (!isTerminal(active.state) && active.state != PairingTransactionState.RECOVERY_REQUIRED) return PairingTransactionResult.INVALID_TRANSITION
        return try {
            persistence?.clear()
            current = null
            PairingTransactionResult.UPDATED
        } catch (_: Exception) {
            PairingTransactionResult.PERSISTENCE_FAILURE
        }
    }

    private fun advance(
        expected: PairingTransactionState,
        next: PairingTransactionState,
        nowMillis: Long
    ): PairingTransactionResult {
        val active = current ?: return PairingTransactionResult.INVALID_TRANSITION
        if (active.state != expected) return PairingTransactionResult.INVALID_TRANSITION
        if (nowMillis < 0L) return PairingTransactionResult.INVALID_TRANSITION
        if (nowMillis >= active.expiresAtMillis) return expire(active, nowMillis)
        return commit(active.copy(state = next, updatedAtMillis = nowMillis))
    }

    private fun expire(
        active: PairingTransactionSnapshot,
        nowMillis: Long
    ): PairingTransactionResult = when (
        commit(active.copy(state = PairingTransactionState.EXPIRED, updatedAtMillis = nowMillis))
    ) {
        PairingTransactionResult.UPDATED -> PairingTransactionResult.EXPIRED
        else -> PairingTransactionResult.PERSISTENCE_FAILURE
    }

    private fun commit(next: PairingTransactionSnapshot): PairingTransactionResult {
        return try {
            persistence?.save(next)
            current = next
            PairingTransactionResult.UPDATED
        } catch (_: Exception) {
            PairingTransactionResult.PERSISTENCE_FAILURE
        }
    }

    private fun validate(value: PairingTransactionSnapshot) {
        require(value.requestId.matches(Regex("^[A-Za-z0-9_-]{22}$")))
        require(value.expiresAtMillis > 0L && value.updatedAtMillis >= 0L)
        require(value.transcriptHashHex.matches(Regex("^[a-f0-9]{64}$")))
        require(value.peerFingerprint.matches(Regex("^[a-f0-9]{64}$")))
    }

    private fun isTerminal(state: PairingTransactionState): Boolean =
        state == PairingTransactionState.PAIRED ||
            state == PairingTransactionState.ABORTED ||
            state == PairingTransactionState.EXPIRED

    private companion object {
        fun ByteArray.toHexString(): String =
            joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
