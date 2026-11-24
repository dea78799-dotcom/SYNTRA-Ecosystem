package com.syntra.nexus

import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Low-level EC public-key pinning helper.
 *
 * This helper alone does not establish pairing provenance. TLS callers must use
 * isTrustedForSession(), which additionally checks the exact request ID and
 * transcript hash against the current trusted-device registry.
 */
object PinnedPeerKeyPolicy {
    const val MAX_PUBLIC_KEY_BYTES = 512

    fun isTrusted(
        peerPublicKey: PublicKey,
        trustedDevices: Collection<TrustedDeviceRecord>,
        expectedPeerFingerprint: String? = null
    ): Boolean {
        if (expectedPeerFingerprint != null &&
            !expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) return false
        if (!peerPublicKey.algorithm.equals("EC", ignoreCase = true)) return false

        val encoded = peerPublicKey.encoded ?: return false
        if (encoded.isEmpty() || encoded.size > MAX_PUBLIC_KEY_BYTES) return false

        val canonicalEncoded = try {
            PairingIdentityCrypto.requireP256PublicKey(encoded).encoded
        } catch (_: Exception) {
            return false
        }
        if (!MessageDigest.isEqual(encoded, canonicalEncoded)) return false

        val fingerprint = PairingIdentityCrypto.fingerprint(canonicalEncoded)
        return trustedDevices.any { record ->
            !record.isRevoked &&
                record.fingerprint == fingerprint &&
                (expectedPeerFingerprint == null || record.fingerprint == expectedPeerFingerprint) &&
                MessageDigest.isEqual(record.publicKeyEncoded, canonicalEncoded)
        }
    }
    /**
     * Requires the presented key to match the exact, currently active pairing
     * provenance. Fingerprint pinning without this binding is insufficient.
     */
    fun isTrustedForSession(
        peerPublicKey: PublicKey,
        registry: TrustedDeviceRegistry,
        expectedPeerFingerprint: String,
        expectedPairingRequestId: String,
        expectedTranscriptHashHex: String
    ): Boolean {
        if (!expectedPairingRequestId.matches(Regex("^[A-Za-z0-9_-]{22}$")) ||
            !expectedTranscriptHashHex.matches(Regex("^[a-f0-9]{64}$"))
        ) return false
        if (!isTrusted(
                peerPublicKey,
                registry.activeDevices(),
                expectedPeerFingerprint
            )
        ) return false

        val encoded = peerPublicKey.encoded ?: return false
        return registry.isActiveForSession(
            fingerprint = expectedPeerFingerprint,
            publicKeyEncoded = encoded,
            requestId = expectedPairingRequestId,
            transcriptHashHex = expectedTranscriptHashHex
        )
    }

}

/**
 * Fail-closed X.509 trust manager for a mutually authenticated NEXUS TLS
 * channel. It accepts only leaf certificates whose EC public key and exact
 * pairing request/transcript provenance remain active in the registry.
 *
 * This class validates peer certificates during a TLS handshake. It does not
 * create sockets, select local credentials or perform invitation redemption.
 */
class PinnedPeerTrustManager(
    private val trustedDeviceRegistry: TrustedDeviceRegistry,
    private val expectedPeerFingerprint: String,
    private val expectedPairingRequestId: String,
    private val expectedTranscriptHashHex: String
) : X509TrustManager {

    init {
        require(expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) {
            "Expected peer fingerprint must be 64 lowercase hexadecimal characters."
        }
        require(expectedPairingRequestId.matches(Regex("^[A-Za-z0-9_-]{22}$"))) {
            "Expected pairing request ID is invalid."
        }
        require(expectedTranscriptHashHex.matches(Regex("^[a-f0-9]{64}$"))) {
            "Expected transcript hash is invalid."
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        validatePeerCertificate(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        validatePeerCertificate(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun validatePeerCertificate(
        chain: Array<out X509Certificate>,
        authType: String
    ) {
        if (authType.isBlank()) {
            throw CertificateException("TLS authentication type is missing.")
        }
        if (chain.isEmpty() || chain.size > MAX_CERTIFICATE_CHAIN_LENGTH) {
            throw CertificateException("TLS certificate chain length is invalid.")
        }

        val leaf = chain.first()
        try {
            leaf.checkValidity()
        } catch (error: Exception) {
            throw CertificateException("Peer certificate is expired or not yet valid.", error)
        }

        if (leaf.basicConstraints >= 0) {
            throw CertificateException("A CA certificate cannot be used as a NEXUS device identity.")
        }

        val keyUsage = leaf.keyUsage
        if (keyUsage != null && (keyUsage.isEmpty() || !keyUsage[0])) {
            throw CertificateException("Peer certificate does not permit digital signatures.")
        }

        if (!PinnedPeerKeyPolicy.isTrustedForSession(
                peerPublicKey = leaf.publicKey,
                registry = trustedDeviceRegistry,
                expectedPeerFingerprint = expectedPeerFingerprint,
                expectedPairingRequestId = expectedPairingRequestId,
                expectedTranscriptHashHex = expectedTranscriptHashHex
            )) {
            throw CertificateException(
                "Peer certificate is not trusted for the exact active pairing transaction."
            )
        }
    }

    private companion object {
        const val MAX_CERTIFICATE_CHAIN_LENGTH = 8
    }
}
