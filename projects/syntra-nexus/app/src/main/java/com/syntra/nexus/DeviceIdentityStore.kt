package com.syntra.nexus

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

/**
 * Creates and retrieves this installation's long-lived device identity.
 *
 * The private key remains non-exportable in Android Keystore. Only the
 * encoded public key and its fingerprint are intended for peer exchange.
 */
class DeviceIdentityStore {
    data class DeviceIdentity(
        val publicKeyEncoded: ByteArray,
        val fingerprint: String
    )

    fun getOrCreateIdentity(): DeviceIdentity {
        val keyPair = loadExistingKeyPair() ?: generateKeyPair()
        val publicKey = keyPair.public.encoded
        return DeviceIdentity(
            publicKeyEncoded = publicKey,
            fingerprint = PairingIdentityCrypto.fingerprint(publicKey)
        )
    }

    fun signPairingTranscriptHash(
        transcriptHash: ByteArray,
        role: PairingSignerRole
    ): ByteArray {
        require(transcriptHash.size == 32) { "Pairing transcript hash must be 32 bytes." }
        val keyPair = loadExistingKeyPair()
            ?: throw IllegalStateException("Device identity has not been created.")
        return PairingIdentityCrypto.signTranscriptHash(keyPair.private, transcriptHash, role)
    }

    fun signPairingApproval(
        transcript: PairingTranscript,
        role: PairingSignerRole
    ): ByteArray {
        val keyPair = loadExistingKeyPair()
            ?: throw IllegalStateException("Device identity has not been created.")
        require(keyPair.public.encoded.contentEquals(transcript.publicKeyFor(role))) {
            "Local device identity does not match the transcript signer role."
        }
        return PairingApprovalVerifier.createApprovalSignature(
            keyPair.private, transcript, role
        )
    }

    /**
     * Deletes the local identity key. Existing peers will no longer be able
     * to authenticate this installation under its previous identity.
     * This does not notify or revoke records on other devices.
     */
    fun deleteLocalIdentity() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun loadExistingKeyPair(): KeyPair? {
        val keyStore = loadKeyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) return null

        val certificate = keyStore.getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("Device identity certificate is missing.")
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
            ?: throw IllegalStateException("Device identity private key is unavailable.")

        return KeyPair(certificate.publicKey, privateKey)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()

        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "syntra.nexus.device.identity.v1"
        const val EC_CURVE = "secp256r1"
    }
}
