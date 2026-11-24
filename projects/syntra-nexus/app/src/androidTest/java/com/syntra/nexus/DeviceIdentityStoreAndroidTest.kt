package com.syntra.nexus

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class DeviceIdentityStoreAndroidTest {
    private val store = DeviceIdentityStore()

    private fun deleteIdentityKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    @Before
    fun setUp() {
        deleteIdentityKey()
    }

    @After
    fun tearDown() {
        deleteIdentityKey()
    }

    @Test
    fun identityUsesP256AndIsStableAcrossStoreInstances() {
        val first = store.getOrCreateIdentity()
        val second = DeviceIdentityStore().getOrCreateIdentity()

        assertArrayEquals(first.publicKeyEncoded, second.publicKeyEncoded)
        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(
            first.fingerprint,
            PairingIdentityCrypto.fingerprint(first.publicKeyEncoded)
        )
        PairingIdentityCrypto.requireP256PublicKey(first.publicKeyEncoded)
    }

    @Test
    fun identityPublicKeyBytesAreDefensivelyCopied() {
        val identity = store.getOrCreateIdentity()
        val expected = identity.publicKeyEncoded
        val exposed = identity.publicKeyEncoded
        exposed[0] = (exposed[0].toInt() xor 0x7f).toByte()

        assertArrayEquals(expected, identity.publicKeyEncoded)
    }

    @Test
    fun keystorePrivateKeySignsOnlyForItsMatchingPublicIdentity() {
        val identity = store.getOrCreateIdentity()
        val peer = java.security.KeyPairGenerator.getInstance("EC").apply {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val transcript = PairingTranscript.create(
            requestId = "abcdefghijklmnopqrstuv",
            expiresAtMillis = 120_000L,
            initiatorPublicKeyEncoded = identity.publicKeyEncoded,
            responderPublicKeyEncoded = peer.public.encoded,
            initiatorNonce = ByteArray(32) { (it + 1).toByte() },
            responderNonce = ByteArray(32) { (it + 65).toByte() },
            nowMillis = 60_000L
        )
        val signature = store.signPairingTranscriptHash(
            transcript,
            PairingSignerRole.INITIATOR
        )

        assertTrue(PairingIdentityCrypto.verifyTranscriptHash(
            identity.publicKeyEncoded,
            transcript.sha256,
            signature,
            PairingSignerRole.INITIATOR
        ))
        assertFalse(PairingIdentityCrypto.verifyTranscriptHash(
            identity.publicKeyEncoded,
            transcript.sha256,
            signature,
            PairingSignerRole.RESPONDER
        ))
        assertThrows(IllegalArgumentException::class.java) {
            store.signPairingTranscriptHash(transcript, PairingSignerRole.RESPONDER)
        }
    }

    @Test
    fun deletingIdentityRemovesAliasAndNextIdentityIsNew() {
        val oldIdentity = store.getOrCreateIdentity()
        store.deleteLocalIdentity()

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        assertFalse(keyStore.containsAlias(KEY_ALIAS))

        val newIdentity = DeviceIdentityStore().getOrCreateIdentity()
        assertFalse(oldIdentity.fingerprint == newIdentity.fingerprint)
    }

    private companion object {
        const val KEY_ALIAS = "syntra.nexus.device.identity.v1"
    }
}
