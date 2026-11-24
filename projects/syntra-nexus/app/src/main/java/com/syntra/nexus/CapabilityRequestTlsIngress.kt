package com.syntra.nexus

import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/**
 * Admission gate for a capability request received over an already-established
 * mutually authenticated TLS session.
 *
 * This class does not create or configure a socket. The caller must pass the
 * SSLSession obtained from the completed SSLSocket/SSLEngine handshake that
 * used NexusTlsContextFactory and PinnedPeerTrustManager.
 */
class CapabilityRequestTlsIngress(
    private val consentCoordinator: CapabilityConsentCoordinator
) {
    fun prepare(
        envelope: CapabilityRequestEnvelope,
        tlsSession: SSLSession,
        expectedPeerFingerprint: String,
        transcript: PairingTranscript,
        pairingSession: PairingHandshakeSession,
        transaction: PairingTransactionSnapshot?,
        trustedDevices: TrustedDeviceRegistry,
        nowMillis: Long
    ): CapabilityConsentCoordinator.PendingCapabilityConsent? {
        if (!expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) return null
        if (!tlsSession.isValid || !isSupportedTlsProtocol(tlsSession.protocol)) return null

        val localCertificates = tlsSession.localCertificates ?: return null
        if (!containsX509Certificate(localCertificates)) return null

        val peerCertificates = try {
            tlsSession.peerCertificates
        } catch (_: SSLPeerUnverifiedException) {
            return null
        }
        if (peerCertificates.size !in 1..MAX_CERTIFICATE_CHAIN_LENGTH) return null
        val peerLeaf = peerCertificates.firstOrNull() as? X509Certificate ?: return null

        try {
            peerLeaf.checkValidity()
        } catch (_: Exception) {
            return null
        }
        if (peerLeaf.basicConstraints >= 0) return null

        val request = envelope.request
        if (request.peerFingerprint != expectedPeerFingerprint) return null

        val trustedPeer = PinnedPeerKeyPolicy.isTrusted(
            peerLeaf.publicKey,
            trustedDevices.activeDevices(),
            expectedPeerFingerprint
        )
        if (!trustedPeer) return null

        return consentCoordinator.prepare(
            request = request,
            transcript = transcript,
            session = pairingSession,
            transaction = transaction,
            trustedDevices = trustedDevices,
            peerApprovalSignature = envelope.peerApprovalSignatureCopy(),
            nowMillis = nowMillis
        )
    }

    private fun containsX509Certificate(certificates: Array<Certificate>): Boolean =
        certificates.isNotEmpty() && certificates.size <= MAX_CERTIFICATE_CHAIN_LENGTH &&
            certificates.firstOrNull() is X509Certificate

    private fun isSupportedTlsProtocol(protocol: String?): Boolean =
        protocol == "TLSv1.2" || protocol == "TLSv1.3"

    private companion object {
        const val MAX_CERTIFICATE_CHAIN_LENGTH = 8
    }
}
