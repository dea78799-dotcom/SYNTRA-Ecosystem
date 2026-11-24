package com.syntra.nexus

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingIdentityCryptoTest {
    @Test
    fun fingerprintIsStableForSamePublicKey() {
        val pair = createKeyPair()
        val fingerprint = PairingIdentityCrypto.fingerprint(pair.public.encoded)
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(fingerprint, PairingIdentityCrypto.fingerprint(pair.public.encoded))
    }

    @Test
    fun fingerprintChangesForDifferentKeys() {
        val first = createKeyPair()
        val second = createKeyPair()
        assertNotEquals(
            PairingIdentityCrypto.fingerprint(first.public.encoded),
            PairingIdentityCrypto.fingerprint(second.public.encoded)
        )
    }

    @Test
    fun signsAndVerifiesInitiatorTranscriptHash() {
        val pair = createKeyPair()
        val hash = ByteArray(32) { it.toByte() }
        val signature = PairingIdentityCrypto.signTranscriptHash(
            pair.private, hash, PairingSignerRole.INITIATOR
        )
        assertTrue(
            PairingIdentityCrypto.verifyTranscriptHash(
                pair.public.encoded, hash, signature, PairingSignerRole.INITIATOR
            )
        )
    }

    @Test
    fun signaturesAreBoundToSignerRole() {
        val pair = createKeyPair()
        val hash = ByteArray(32) { it.toByte() }
        val signature = PairingIdentityCrypto.signTranscriptHash(
            pair.private, hash, PairingSignerRole.INITIATOR
        )
        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                pair.public.encoded, hash, signature, PairingSignerRole.RESPONDER
            )
        )
    }

    @Test
    fun rejectsSignatureForDifferentTranscript() {
        val pair = createKeyPair()
        val original = ByteArray(32) { it.toByte() }
        val changed = ByteArray(32) { (it + 1).toByte() }
        val signature = PairingIdentityCrypto.signTranscriptHash(
            pair.private, original, PairingSignerRole.INITIATOR
        )
        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                pair.public.encoded, changed, signature, PairingSignerRole.INITIATOR
            )
        )
    }

    @Test
    fun rejectsWrongKeyAndMalformedSignature() {
        val signer = createKeyPair()
        val other = createKeyPair()
        val hash = ByteArray(32) { 7 }
        val signature = PairingIdentityCrypto.signTranscriptHash(
            signer.private, hash, PairingSignerRole.RESPONDER
        )
        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                other.public.encoded, hash, signature, PairingSignerRole.RESPONDER
            )
        )
        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                signer.public.encoded, hash, byteArrayOf(1, 2, 3), PairingSignerRole.RESPONDER
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongTranscriptHashLength() {
        PairingIdentityCrypto.signTranscriptHash(
            createKeyPair().private, ByteArray(31), PairingSignerRole.INITIATOR
        )
    }


    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonP256EcPublicKey() {
        val otherCurve = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair()
        PairingIdentityCrypto.requireP256PublicKey(otherCurve.public.encoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRsaPublicKeyAsDeviceIdentity() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        PairingIdentityCrypto.requireP256PublicKey(rsa.public.encoded)
    }


    @Test
    fun rejectsSignatureFromNonP256Curve() {
        val otherCurve = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }.generateKeyPair()
        val hash = ByteArray(32) { 9 }
        val signature = PairingIdentityCrypto.signTranscriptHash(
            otherCurve.private, hash, PairingSignerRole.INITIATOR
        )
        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                otherCurve.public.encoded, hash, signature, PairingSignerRole.INITIATOR
            )
        )
    }

    private fun createKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
