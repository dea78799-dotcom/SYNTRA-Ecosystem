package com.syntra.nexus

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val transcriptHash = ByteArray(32) { (it + 3).toByte() }
        val signature = store.signPairingTranscriptHash(
            transcriptHash,
            PairingSignerRole.INITIATOR
        )

        assertTrue(PairingIdentityCrypto.verifyTranscriptHash(
            identity.publicKeyEncoded,
            transcriptHash,
            signature,
            PairingSignerRole.INITIATOR
        ))
        assertFalse(PairingIdentityCrypto.verifyTranscriptHash(
            identity.publicKeyEncoded,
            transcriptHash,
            signature,
            PairingSignerRole.RESPONDER
        ))
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
