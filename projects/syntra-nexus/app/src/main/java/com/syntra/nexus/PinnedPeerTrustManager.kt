package com.syntra.nexus

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import javax.net.ssl.X509TrustManager

/**
 * Matches a presented EC public key to an active, locally approved NEXUS peer.
 *
 * The policy is identity pinning, not CA/hostname validation. It must only be
 * used after the key was approved through the transcript-bound pairing flow.
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

        val canonicalKey = try {
            KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(encoded))
        } catch (_: Exception) {
            return false
        }

        val canonicalEncoded = canonicalKey.encoded ?: return false
        if (!MessageDigest.isEqual(encoded, canonicalEncoded)) return false

        val fingerprint = PairingIdentityCrypto.fingerprint(canonicalEncoded)
        return trustedDevices.any { record ->
            !record.isRevoked &&
                record.fingerprint == fingerprint &&
                (expectedPeerFingerprint == null || record.fingerprint == expectedPeerFingerprint) &&
                MessageDigest.isEqual(record.publicKeyEncoded, canonicalEncoded)
        }
    }
}

/**
 * Fail-closed X.509 trust manager for a future mutually authenticated NEXUS
 * TLS channel. It accepts only leaf certificates whose EC public key is
 * already present in the active trusted-device registry.
 *
 * This class does not create sockets, select local credentials, perform
 * invitation redemption, or complete a TLS handshake.
 */
class PinnedPeerTrustManager(
    private val trustedDeviceRegistry: TrustedDeviceRegistry,
    private val expectedPeerFingerprint: String? = null
) : X509TrustManager {

    init {
        require(expectedPeerFingerprint == null ||
            expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) {
            "Expected peer fingerprint must be 64 lowercase hexadecimal characters."
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

        if (!PinnedPeerKeyPolicy.isTrusted(
                leaf.publicKey,
                trustedDeviceRegistry.activeDevices(),
                expectedPeerFingerprint
            )) {
            throw CertificateException("Peer certificate key is not in the active trusted-device registry.")
        }
    }

    private companion object {
        const val MAX_CERTIFICATE_CHAIN_LENGTH = 8
    }
}
