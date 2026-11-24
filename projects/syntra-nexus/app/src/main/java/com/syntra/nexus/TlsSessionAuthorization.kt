package com.syntra.nexus

import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/**
 * A short-lived authorization handle for one completed mutual-TLS session.
 *
 * It snapshots the JSSE session identity and both certificate fingerprints.
 * Every call to isAuthorized() rechecks session validity, certificate
 * identities, certificate dates and the current trusted-device registry.
 *
 * This does not prove that the underlying socket is still open. A transport
 * caller must separately reject closed/disconnected sockets before I/O.
 */
class TlsSessionAuthorization private constructor(
    private val tlsSession: SSLSession,
    private val expectedLocalFingerprint: String,
    private val expectedPeerFingerprint: String,
    private val expectedPairingRequestId: String,
    private val expectedTranscriptHashHex: String,
    private val sessionIdSnapshot: ByteArray,
    private val sessionCreationTime: Long,
    private val protocolSnapshot: String
) {
    /**
     * Rechecks the exact TLS session and current peer trust immediately before
     * a protected operation. Revocation or provenance changes fail closed.
     */
    fun isAuthorized(
        trustedDevices: TrustedDeviceRegistry,
        nowMillis: Long
    ): Boolean {
        if (nowMillis < 0L) return false
        return try {
            if (!tlsSession.isValid ||
                !CapabilityRequestTlsIngress.isSupportedTlsProtocol(tlsSession.protocol) ||
                tlsSession.protocol != protocolSnapshot ||
                tlsSession.creationTime != sessionCreationTime ||
                !java.security.MessageDigest.isEqual(
                    tlsSession.id ?: ByteArray(0),
                    sessionIdSnapshot
                )
            ) return false

            val localLeaf = firstX509(tlsSession.localCertificates) ?: return false
            val peerCertificates = try {
                tlsSession.peerCertificates
            } catch (_: SSLPeerUnverifiedException) {
                return false
            }
            if (peerCertificates.size !in 1..MAX_CERTIFICATE_CHAIN_LENGTH) return false
            val peerLeaf = peerCertificates.firstOrNull() as? X509Certificate ?: return false

            if (!isValidDeviceCertificate(localLeaf, nowMillis) ||
                !isValidDeviceCertificate(peerLeaf, nowMillis)
            ) return false

            val localFingerprint = PairingIdentityCrypto.fingerprint(localLeaf.publicKey.encoded)
            val peerFingerprint = PairingIdentityCrypto.fingerprint(peerLeaf.publicKey.encoded)
            if (localFingerprint != expectedLocalFingerprint ||
                peerFingerprint != expectedPeerFingerprint
            ) return false

            trustedDevices.isActiveForSession(
                fingerprint = expectedPeerFingerprint,
                publicKeyEncoded = peerLeaf.publicKey.encoded,
                requestId = expectedPairingRequestId,
                transcriptHashHex = expectedTranscriptHashHex
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun firstX509(certificates: Array<Certificate>?): X509Certificate? {
        if (certificates == null || certificates.isEmpty() ||
            certificates.size > MAX_CERTIFICATE_CHAIN_LENGTH
        ) return null
        return certificates.firstOrNull() as? X509Certificate
    }

    private fun isValidDeviceCertificate(
        certificate: X509Certificate,
        nowMillis: Long
    ): Boolean {
        return try {
            certificate.checkValidity(Date(nowMillis))
            if (certificate.basicConstraints >= 0) return false
            val keyUsage = certificate.keyUsage
            if (keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])) return false
            PairingIdentityCrypto.requireP256PublicKey(certificate.publicKey.encoded)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val MAX_CERTIFICATE_CHAIN_LENGTH = 8

        /**
         * Captures a session only if both certificate identities and the
         * exact active peer pairing provenance already match.
         */
        fun capture(
            tlsSession: SSLSession,
            expectedLocalFingerprint: String,
            expectedPeerFingerprint: String,
            expectedPairingRequestId: String,
            expectedTranscriptHashHex: String,
            trustedDevices: TrustedDeviceRegistry,
            nowMillis: Long
        ): TlsSessionAuthorization? {
            if (!expectedLocalFingerprint.matches(Regex("^[a-f0-9]{64}$")) ||
                !expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$")) ||
                !expectedPairingRequestId.matches(Regex("^[A-Za-z0-9_-]{22}$")) ||
                !expectedTranscriptHashHex.matches(Regex("^[a-f0-9]{64}$")) ||
                !CapabilityRequestTlsIngress.isSupportedTlsProtocol(tlsSession.protocol)
            ) return null

            val authorization = TlsSessionAuthorization(
                tlsSession = tlsSession,
                expectedLocalFingerprint = expectedLocalFingerprint,
                expectedPeerFingerprint = expectedPeerFingerprint,
                expectedPairingRequestId = expectedPairingRequestId,
                expectedTranscriptHashHex = expectedTranscriptHashHex,
                sessionIdSnapshot = (tlsSession.id ?: ByteArray(0)).copyOf(),
                sessionCreationTime = tlsSession.creationTime,
                protocolSnapshot = tlsSession.protocol
            )
            return authorization.takeIf { it.isAuthorized(trustedDevices, nowMillis) }
        }
    }
}
