package com.syntra.nexus

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Creates and retrieves this installation's long-lived device identity.
 *
 * The private key remains non-exportable in Android Keystore. Only the
 * encoded public key and its fingerprint are intended for peer exchange.
 */
class DeviceIdentityStore {
    class DeviceIdentity(
        publicKeyEncoded: ByteArray,
        val fingerprint: String
    ) {
        private val encoded = publicKeyEncoded.copyOf()
        val publicKeyEncoded: ByteArray
            get() = encoded.copyOf()
    }

    fun getOrCreateIdentity(): DeviceIdentity {
        val keyPair = loadExistingKeyPair() ?: generateKeyPair().also(::verifyKeyPair)
        val publicKey = keyPair.public.encoded
        return DeviceIdentity(
            publicKeyEncoded = publicKey,
            fingerprint = PairingIdentityCrypto.fingerprint(publicKey)
        )
    }

    fun signPairingTranscriptHash(
        transcript: PairingTranscript,
        role: PairingSignerRole
    ): ByteArray {
        val keyPair = loadExistingKeyPair()
            ?: throw IllegalStateException("Device identity has not been created.")
        require(keyPair.public.encoded.contentEquals(transcript.publicKeyFor(role))) {
            "Local device identity does not match the transcript signer role."
        }
        return PairingIdentityCrypto.signTranscriptHash(
            keyPair.private,
            transcript.sha256,
            role
        )
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

        val publicKey = certificate.publicKey
        return KeyPair(publicKey, privateKey).also(::verifyKeyPair)
    }

    private fun verifyKeyPair(keyPair: KeyPair) {
        val publicKey = keyPair.public
        val privateKey = keyPair.private
        require(publicKey.algorithm.equals(KeyProperties.KEY_ALGORITHM_EC, ignoreCase = true)) {
            "Stored device identity is not an EC key."
        }
        PairingIdentityCrypto.requireP256PublicKey(publicKey.encoded)
        require(privateKey.algorithm.equals(KeyProperties.KEY_ALGORITHM_EC, ignoreCase = true)) {
            "Stored device identity private key is not EC."
        }

        // Detect an invalid or mismatched Keystore key pair before exposing
        // its fingerprint or using it in a pairing operation.
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(IDENTITY_KEY_CHECK_DOMAIN)
        signer.update(challenge)
        val signature = signer.sign()
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(IDENTITY_KEY_CHECK_DOMAIN)
        verifier.update(challenge)
        check(verifier.verify(signature)) {
            "Stored device identity public and private keys do not match."
        }
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
        val IDENTITY_KEY_CHECK_DOMAIN =
            "SYNTRA-NEXUS-IDENTITY-KEY-CHECK-v1:".toByteArray(Charsets.US_ASCII)
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "syntra.nexus.device.identity.v1"
        const val EC_CURVE = "secp256r1"
    }
}
