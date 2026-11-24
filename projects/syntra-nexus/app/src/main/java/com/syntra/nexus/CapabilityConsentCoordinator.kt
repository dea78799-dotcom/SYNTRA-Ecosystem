package com.syntra.nexus

/**
 * Validates a peer-signed capability request before a consent dialog is shown.
 *
 * This is a local policy boundary only. A future transport must authenticate
 * the sender with the existing pinned mutual-TLS session before constructing
 * PendingCapabilityConsent. The local signing callback is invoked only when
 * the UI explicitly calls approve() from its Allow button.
 */
class CapabilityConsentCoordinator(
    private val registry: CapabilityGrantRegistry
) {
    class PendingCapabilityConsent internal constructor(
        val request: CapabilityGrantRequest,
        val transcript: PairingTranscript,
        val session: PairingHandshakeSession,
        val transaction: PairingTransactionSnapshot,
        peerApprovalSignature: ByteArray
    ) {
        private val peerSignature = peerApprovalSignature.copyOf()
        internal fun peerApprovalSignatureCopy(): ByteArray = peerSignature.copyOf()
    }

    fun prepare(
        request: CapabilityGrantRequest,
        transcript: PairingTranscript,
        session: PairingHandshakeSession,
        transaction: PairingTransactionSnapshot?,
        trustedDevices: TrustedDeviceRegistry,
        peerApprovalSignature: ByteArray,
        nowMillis: Long
    ): PendingCapabilityConsent? {
        if (!isValid(
                request, transcript, session, transaction, trustedDevices,
                peerApprovalSignature, nowMillis
            )
        ) return null

        return PendingCapabilityConsent(
            request, transcript, session, requireNotNull(transaction),
            peerApprovalSignature
        )
    }

    /**
     * Call only from the explicit local Allow action. The signature callback
     * is deliberately lazy so merely displaying a dialog cannot approve.
     */
    fun approve(
        pending: PendingCapabilityConsent,
        trustedDevices: TrustedDeviceRegistry,
        nowMillis: Long,
        createLocalApprovalSignature: () -> ByteArray
    ): CapabilityGrant? {
        val request = pending.request
        val peerSignature = pending.peerApprovalSignatureCopy()
        if (!isValid(
                request, pending.transcript, pending.session, pending.transaction,
                trustedDevices, peerSignature, nowMillis
            )
        ) return null

        val localSignature = createLocalApprovalSignature()
        return registry.issue(
            request = request,
            transcript = pending.transcript,
            session = pending.session,
            transaction = pending.transaction,
            trustedDevices = trustedDevices,
            localApprovalSignature = localSignature,
            peerApprovalSignature = peerSignature,
            nowMillis = nowMillis
        )
    }

    private fun isValid(
        request: CapabilityGrantRequest,
        transcript: PairingTranscript,
        session: PairingHandshakeSession,
        transaction: PairingTransactionSnapshot?,
        trustedDevices: TrustedDeviceRegistry,
        peerApprovalSignature: ByteArray,
        nowMillis: Long
    ): Boolean {
        if (nowMillis < request.issuedAtMillis ||
            nowMillis >= request.expiresAtMillis ||
            nowMillis - request.issuedAtMillis > MAX_REQUEST_AGE_MILLIS ||
            request.pairingRequestId != transcript.requestId ||
            request.transcriptHashHex != transcript.sha256.toLowerHex() ||
            request.peerFingerprint != PairingIdentityCrypto.fingerprint(
                transcript.publicKeyFor(PairingSignerRole.RESPONDER)
            ) ||
            request.expiresAtMillis > transcript.expiresAtMillis
        ) return false

        if (!TrustedDeviceAuthorization.mayAuthorizeNewSession(
                session, transcript, transaction, trustedDevices, nowMillis
            )
        ) return false

        return CapabilityGrantRegistry.verifyApprovalSignature(
            transcript.publicKeyFor(PairingSignerRole.RESPONDER),
            request,
            PairingSignerRole.RESPONDER,
            peerApprovalSignature
        )
    }

    private companion object {
        const val MAX_REQUEST_AGE_MILLIS = 30_000L
    }
}

private fun ByteArray.toLowerHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xff) }
