package com.syntra.nexus

import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

/**
 * Admission gate for a capability request received over an already-established
 * mutually authenticated TLS session.
 *
 * This class does not create or configure a socket. The caller must pass the
 * exact SSLSocket used for the completed handshake configured with
 * NexusTlsContextFactory and PinnedPeerTrustManager.
 */
class CapabilityRequestTlsIngress(
    private val consentCoordinator: CapabilityConsentCoordinator
) {
    fun prepare(
        envelope: CapabilityRequestEnvelope,
        tlsSocket: SSLSocket,
        expectedPeerFingerprint: String,
        transcript: PairingTranscript,
        pairingSession: PairingHandshakeSession,
        transaction: PairingTransactionSnapshot?,
        trustedDevices: TrustedDeviceRegistry,
        nowMillis: Long
    ): CapabilityConsentCoordinator.PendingCapabilityConsent? {
        if (!expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) return null
        val tlsSession = tlsSocket.session
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

        val trustedPeer = PinnedPeerKeyPolicy.isTrustedForSession(
            peerPublicKey = peerLeaf.publicKey,
            registry = trustedDevices,
            expectedPeerFingerprint = expectedPeerFingerprint,
            expectedPairingRequestId = transcript.requestId,
            expectedTranscriptHashHex = transcript.sha256.toTlsLowerHex()
        )
        if (!trustedPeer) return null

        val sessionAuthorization = TlsSessionAuthorization.capture(
            tlsSession = tlsSession,
            expectedLocalFingerprint = PairingIdentityCrypto.fingerprint(
                transcript.publicKeyFor(PairingSignerRole.INITIATOR)
            ),
            expectedPeerFingerprint = expectedPeerFingerprint,
            expectedPairingRequestId = transcript.requestId,
            expectedTranscriptHashHex = transcript.sha256.toTlsLowerHex(),
            trustedDevices = trustedDevices,
            nowMillis = nowMillis
        ) ?: return null

        val channelBinding = TlsSocketChannelBinding.capture(
            socket = tlsSocket,
            authorization = sessionAuthorization,
            trustedDevices = trustedDevices,
            nowMillis = nowMillis
        ) ?: return null

        return consentCoordinator.prepare(
            request = request,
            transcript = transcript,
            session = pairingSession,
            transaction = transaction,
            trustedDevices = trustedDevices,
            peerApprovalSignature = envelope.peerApprovalSignatureCopy(),
            nowMillis = nowMillis,
            tlsChannelBinding = channelBinding
        )
    }

    private fun containsX509Certificate(certificates: Array<Certificate>): Boolean =
        certificates.isNotEmpty() && certificates.size <= MAX_CERTIFICATE_CHAIN_LENGTH &&
            certificates.firstOrNull() is X509Certificate

    internal companion object {
        const val MAX_CERTIFICATE_CHAIN_LENGTH = 8

        fun isSupportedTlsProtocol(protocol: String?): Boolean =
            protocol == "TLSv1.2" || protocol == "TLSv1.3"
    }
}

private fun ByteArray.toTlsLowerHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xff) }
