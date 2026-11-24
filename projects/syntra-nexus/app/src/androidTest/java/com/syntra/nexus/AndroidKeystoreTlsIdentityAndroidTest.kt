package com.syntra.nexus

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.net.ssl.SSLSocket

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreTlsIdentityAndroidTest {
    private val identityStore = DeviceIdentityStore()

    private fun deleteIdentityAlias() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (keyStore.containsAlias(IDENTITY_ALIAS)) {
            keyStore.deleteEntry(IDENTITY_ALIAS)
        }
    }

    private fun registryFor(publicKeyEncoded: ByteArray, revokedAt: Long? = null) =
        TrustedDeviceRegistry(object : TrustedDevicePersistence {
            private val records = listOf(
                TrustedDeviceRecord(
                    fingerprint = PairingIdentityCrypto.fingerprint(publicKeyEncoded),
                    publicKeyEncoded = publicKeyEncoded.copyOf(),
                    displayName = "Android TLS test peer",
                    trustedAtMillis = 10L,
                    revokedAtMillis = revokedAt,
                    pairingRequestId = TEST_PAIRING_REQUEST_ID,
                    transcriptHashHex = TEST_TRANSCRIPT_HASH
                )
            )

            override fun load(): List<TrustedDeviceRecord> = records
            override fun save(records: List<TrustedDeviceRecord>) = Unit
        })

    private fun createPeerPublicKey(): ByteArray =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair().public.encoded

    @Before
    fun setUp() {
        deleteIdentityAlias()
    }

    @After
    fun tearDown() {
        deleteIdentityAlias()
    }

    @Test
    fun keyManagerUsesAndroidKeystoreIdentityAndPrivateKeyIsNotExportable() {
        val identity = identityStore.getOrCreateIdentity()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(IDENTITY_ALIAS, null) as java.security.PrivateKey
        val manager = AndroidKeystoreX509KeyManager()

        assertNull("Android Keystore private key encoding must not be exportable.", privateKey.encoded)
        assertNull("Android Keystore private key format must not be exportable.", privateKey.format)
        assertEquals(identity.fingerprint, manager.localFingerprint)
        assertArrayEquals(
            identity.publicKeyEncoded,
            manager.getCertificateChain(IDENTITY_ALIAS)!!.first().publicKey.encoded
        )
        assertEquals(listOf(IDENTITY_ALIAS), manager.getClientAliases("EC", null)?.toList())
        assertEquals(listOf(IDENTITY_ALIAS), manager.getServerAliases("EC", null)?.toList())
        assertEquals(IDENTITY_ALIAS, manager.chooseClientAlias(arrayOf("EC"), null, null))
        assertEquals(IDENTITY_ALIAS, manager.chooseServerAlias("EC", null, null))
        assertNotNull(manager.getPrivateKey(IDENTITY_ALIAS))
        assertNull(manager.getPrivateKey("unrelated-alias"))

        val challenge = ByteArray(32) { (it + 11).toByte() }
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(manager.getPrivateKey(IDENTITY_ALIAS))
        signer.update(challenge)
        val proof = signer.sign()
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(manager.getCertificateChain(IDENTITY_ALIAS)!!.first().publicKey)
        verifier.update(challenge)
        assertTrue("Keystore key must prove possession against its certificate.", verifier.verify(proof))

        val exposedChain = manager.getCertificateChain(IDENTITY_ALIAS)!!
        val secondChain = manager.getCertificateChain(IDENTITY_ALIAS)!!
        assertNotSame("Certificate chain arrays must not be shared with callers.", exposedChain, secondChain)
        assertEquals(identity.fingerprint, manager.localFingerprint)
    }

    @Test
    fun tlsContextFactoryBuildsTlsContextForAnActivePinnedPeer() {
        val localIdentity = identityStore.getOrCreateIdentity()
        val remotePublicKey = createPeerPublicKey()
        val remoteFingerprint = PairingIdentityCrypto.fingerprint(remotePublicKey)
        val context = NexusTlsContextFactory.create(
            registryFor(remotePublicKey),
            remoteFingerprint,
            TEST_PAIRING_REQUEST_ID,
            TEST_TRANSCRIPT_HASH
        )

        assertNotNull(context)
        val socket = context.socketFactory.createSocket() as SSLSocket
        socket.use {
            NexusTlsProtocolPolicy.configureClientSocket(it)
            assertTrue(it.enabledProtocols.isNotEmpty())
            assertTrue(it.enabledProtocols.all { protocol ->
                protocol == "TLSv1.2" || protocol == "TLSv1.3"
            })
        }
        assertFalse(localIdentity.fingerprint == remoteFingerprint)
    }

    @Test
    fun tlsContextFactoryRejectsUnknownAndRevokedPeerIdentities() {
        identityStore.getOrCreateIdentity()
        val unknownPublicKey = createPeerPublicKey()
        val unknownFingerprint = PairingIdentityCrypto.fingerprint(unknownPublicKey)

        assertThrows(IllegalArgumentException::class.java) {
            NexusTlsContextFactory.create(
                TrustedDeviceRegistry(), unknownFingerprint,
                TEST_PAIRING_REQUEST_ID, TEST_TRANSCRIPT_HASH
            )
        }

        val revokedPublicKey = createPeerPublicKey()
        val revokedFingerprint = PairingIdentityCrypto.fingerprint(revokedPublicKey)
        assertThrows(IllegalArgumentException::class.java) {
            NexusTlsContextFactory.create(
                registryFor(revokedPublicKey, revokedAt = 20L),
                revokedFingerprint,
                TEST_PAIRING_REQUEST_ID,
                TEST_TRANSCRIPT_HASH
            )
        }
    }

    @Test
    fun tlsKeyManagerFailsClosedWhenLocalIdentityIsMissing() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidKeystoreX509KeyManager()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val IDENTITY_ALIAS = "syntra.nexus.device.identity.v1"
        const val TEST_PAIRING_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        const val TEST_TRANSCRIPT_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
