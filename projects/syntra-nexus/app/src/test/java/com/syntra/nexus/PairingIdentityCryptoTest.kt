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
        assertEquals(
            PairingIdentityCrypto.fingerprint(pair.public.encoded),
            PairingIdentityCrypto.fingerprint(pair.public.encoded)
        )
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
    fun signsAndVerifiesTranscriptHash() {
        val pair = createKeyPair()
        val transcriptHash = ByteArray(32) { it.toByte() }
        val signature = PairingIdentityCrypto.signTranscriptHash(pair.private, transcriptHash)

        assertTrue(
            PairingIdentityCrypto.verifyTranscriptHash(
                pair.public.encoded,
                transcriptHash,
                signature
            )
        )
    }

    @Test
    fun rejectsSignatureForDifferentTranscript() {
        val pair = createKeyPair()
        val original = ByteArray(32) { it.toByte() }
        val changed = ByteArray(32) { (it + 1).toByte() }
        val signature = PairingIdentityCrypto.signTranscriptHash(pair.private, original)

        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                pair.public.encoded,
                changed,
                signature
            )
        )
    }

    @Test
    fun rejectsWrongKeyAndMalformedSignature() {
        val signer = createKeyPair()
        val other = createKeyPair()
        val transcriptHash = ByteArray(32) { 7 }
        val signature = PairingIdentityCrypto.signTranscriptHash(signer.private, transcriptHash)

        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                other.public.encoded,
                transcriptHash,
                signature
            )
        )
        assertFalse(
            PairingIdentityCrypto.verifyTranscriptHash(
                signer.public.encoded,
                transcriptHash,
                byteArrayOf(1, 2, 3)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongTranscriptHashLength() {
        PairingIdentityCrypto.signTranscriptHash(createKeyPair().private, ByteArray(31))
    }

    private fun createKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
