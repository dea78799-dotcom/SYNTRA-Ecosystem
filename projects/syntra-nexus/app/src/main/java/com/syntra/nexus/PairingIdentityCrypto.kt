package com.syntra.nexus

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Cryptographic helpers for NEXUS device identity.
 *
 * Signatures bind the canonical transcript hash to the signer's protocol role.
 * This helper alone does not establish trust or validate user consent.
 */
object PairingIdentityCrypto {
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val KEY_ALGORITHM = "EC"
    private val DOMAIN_SEPARATOR =
        "SYNTRA-NEXUS-PAIRING-V1\u0000".toByteArray(Charsets.UTF_8)

    fun fingerprint(publicKeyEncoded: ByteArray): String {
        require(publicKeyEncoded.isNotEmpty()) { "Public key is empty." }
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded)
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    /** Formats a SHA-256 fingerprint for manual comparison. */
    fun formatFingerprint(fingerprint: String): String {
        require(fingerprint.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "Fingerprint must contain exactly 64 hexadecimal characters."
        }
        return fingerprint.chunked(4).joinToString(" ") { it.uppercase() }
    }
    fun signTranscriptHash(
        privateKey: PrivateKey,
        transcriptHash: ByteArray,
        role: PairingSignerRole
    ): ByteArray {
        require(transcriptHash.size == 32) { "Pairing transcript hash must be 32 bytes." }
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(DOMAIN_SEPARATOR)
        signer.update(role.wireValue.toByte())
        signer.update(transcriptHash)
        return signer.sign()
    }

    fun verifyTranscriptHash(
        publicKeyEncoded: ByteArray,
        transcriptHash: ByteArray,
        signatureBytes: ByteArray,
        role: PairingSignerRole
    ): Boolean {
        if (transcriptHash.size != 32 || signatureBytes.isEmpty()) return false
        return try {
            val publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(publicKeyEncoded))
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(DOMAIN_SEPARATOR)
            verifier.update(role.wireValue.toByte())
            verifier.update(transcriptHash)
            verifier.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }
}
