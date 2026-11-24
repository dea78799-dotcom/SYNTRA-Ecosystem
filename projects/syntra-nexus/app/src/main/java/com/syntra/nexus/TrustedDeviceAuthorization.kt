package com.syntra.nexus

/**
 * Re-checks the current local trust record immediately before authorizing a
 * new peer session. A successful TLS pin check alone is not a durable grant.
 *
 * This is a local policy helper only; no socket or remote capability is wired.
 */
object TrustedDeviceAuthorization {
    fun mayAuthorizeNewSession(
        session: PairingHandshakeSession,
        transcript: PairingTranscript,
        transaction: PairingTransactionSnapshot?,
        registry: TrustedDeviceRegistry,
        nowMillis: Long
    ): Boolean {
        if (nowMillis < 0L || nowMillis >= transcript.expiresAtMillis) return false
        if (session.state != PairingHandshakeState.PAIRED ||
            session.decision != PairingHandshakeDecision.BOTH_APPROVED ||
            !session.isBoundToTranscript(transcript)
        ) return false

        val transcriptHashHex = transcript.sha256.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        val expectedFingerprint = PairingIdentityCrypto.fingerprint(
            transcript.publicKeyFor(PairingSignerRole.RESPONDER)
        )
        if (transaction == null ||
            transaction.state != PairingTransactionState.PAIRED ||
            transaction.requestId != transcript.requestId ||
            transaction.expiresAtMillis != transcript.expiresAtMillis ||
            transaction.transcriptHashHex != transcriptHashHex ||
            transaction.peerFingerprint != expectedFingerprint
        ) return false

        return registry.isActiveForSession(
            fingerprint = expectedFingerprint,
            publicKeyEncoded = transcript.publicKeyFor(PairingSignerRole.RESPONDER),
            requestId = transcript.requestId,
            transcriptHashHex = transcriptHashHex
        )
    }
}
