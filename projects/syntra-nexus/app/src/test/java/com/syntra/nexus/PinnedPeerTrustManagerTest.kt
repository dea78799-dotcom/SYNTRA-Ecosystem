package com.syntra.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class PinnedPeerTrustManagerTest {

    @Test
    fun acceptsExactActiveEcPeerKey() {
        val peer = ecKeyPair()
        val record = trustedRecord(peer)

        assertTrue(PinnedPeerKeyPolicy.isTrusted(peer.public, listOf(record)))
    }

    @Test
    fun rejectsUnknownPeerKey() {
        val approved = ecKeyPair()
        val presented = ecKeyPair()

        assertFalse(PinnedPeerKeyPolicy.isTrusted(presented.public, listOf(trustedRecord(approved))))
    }

    @Test
    fun rejectsRevokedPeerKey() {
        val peer = ecKeyPair()
        val record = trustedRecord(peer).copy(revokedAtMillis = 2_000L)

        assertFalse(PinnedPeerKeyPolicy.isTrusted(peer.public, listOf(record)))
    }

    @Test
    fun rejectsKeyWhenStoredFingerprintDoesNotMatch() {
        val peer = ecKeyPair()
        val other = ecKeyPair()
        val record = trustedRecord(peer).copy(
            fingerprint = PairingIdentityCrypto.fingerprint(other.public.encoded)
        )

        assertFalse(PinnedPeerKeyPolicy.isTrusted(peer.public, listOf(record)))
    }

    @Test
    fun rejectsMalformedStoredPublicKey() {
        val peer = ecKeyPair()
        val record = TrustedDeviceRecord(
            fingerprint = "0".repeat(64),
            publicKeyEncoded = byteArrayOf(1, 2, 3),
            displayName = "Broken record",
            trustedAtMillis = 1_000L
        )

        assertFalse(PinnedPeerKeyPolicy.isTrusted(peer.public, listOf(record)))
    }

    @Test
    fun rejectsRsaKeyEvenIfItHasARegistryRecord() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val record = trustedRecord(rsa)

        assertFalse(PinnedPeerKeyPolicy.isTrusted(rsa.public, listOf(record)))
    }

    @Test
    fun rejectsOversizedPublicKeyEncoding() {
        val oversized = object : java.security.PublicKey {
            override fun getAlgorithm() = "EC"
            override fun getFormat() = "X.509"
            override fun getEncoded() = ByteArray(PinnedPeerKeyPolicy.MAX_PUBLIC_KEY_BYTES + 1)
        }

        assertFalse(PinnedPeerKeyPolicy.isTrusted(oversized, emptyList()))
    }

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private fun trustedRecord(keyPair: KeyPair): TrustedDeviceRecord {
        val encoded = keyPair.public.encoded
        return TrustedDeviceRecord(
            fingerprint = PairingIdentityCrypto.fingerprint(encoded),
            publicKeyEncoded = encoded.copyOf(),
            displayName = "Test peer",
            trustedAtMillis = 1_000L
        )
    }
}
