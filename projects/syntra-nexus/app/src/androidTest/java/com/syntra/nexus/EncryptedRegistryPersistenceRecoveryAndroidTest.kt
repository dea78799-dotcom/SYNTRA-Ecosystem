package com.syntra.nexus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedRegistryPersistenceRecoveryAndroidTest {
    private lateinit var context: Context
    private lateinit var trustedBase: File
    private lateinit var trustedBackup: File
    private lateinit var tokenBase: File
    private lateinit var tokenBackup: File
    private lateinit var trustedRecord: TrustedDeviceRecord

    private fun trustedStore() = EncryptedTrustedDevicePersistence(context)
    private fun tokenStore() = EncryptedPairingTokenPersistence(context)

    private fun deleteAlias(alias: String) {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        trustedBase = File(context.filesDir, TRUSTED_FILE)
        trustedBackup = File(trustedBase.path + ".bak")
        tokenBase = File(context.filesDir, TOKEN_FILE)
        tokenBackup = File(tokenBase.path + ".bak")
        listOf(trustedBase, trustedBackup, tokenBase, tokenBackup).forEach(File::delete)
        deleteAlias(TRUSTED_KEY)
        deleteAlias(TOKEN_KEY)

        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(256)
        val publicKey = generator.generateKeyPair().public.encoded
        trustedRecord = TrustedDeviceRecord(
            fingerprint = PairingIdentityCrypto.fingerprint(publicKey),
            publicKeyEncoded = publicKey,
            displayName = "NEXUS test device",
            trustedAtMillis = 10_000L
        )
    }

    @After
    fun tearDown() {
        listOf(trustedBase, trustedBackup, tokenBase, tokenBackup).forEach(File::delete)
        deleteAlias(TRUSTED_KEY)
        deleteAlias(TOKEN_KEY)
    }

    @Test
    fun trustedRegistryRecoversBackupWhenBaseFileIsMissing() {
        trustedStore().save(listOf(trustedRecord))
        assertTrue(trustedBase.renameTo(trustedBackup))

        val restored = trustedStore().load()

        assertEquals(1, restored.size)
        assertEquals(trustedRecord.fingerprint, restored.single().fingerprint)
        assertTrue(trustedBase.isFile)
        assertTrue(!trustedBackup.exists())
    }

    @Test
    fun trustedRegistryDoesNotCreateReplacementKeyWhenKeyIsMissing() {
        trustedStore().save(listOf(trustedRecord))
        val original = trustedBase.readBytes()
        deleteAlias(TRUSTED_KEY)

        assertThrows(IllegalStateException::class.java) {
            trustedStore().load()
        }

        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        assertTrue(!store.containsAlias(TRUSTED_KEY))
        assertTrue(original.contentEquals(trustedBase.readBytes()))
    }

    @Test
    fun tokenRegistryRecoversBackupWhenBaseFileIsMissing() {
        val expected = listOf(PairingTokenRecord("d".repeat(64), 120_000L, "ACTIVE"))
        tokenStore().save(expected)
        assertTrue(tokenBase.renameTo(tokenBackup))

        val restored = tokenStore().load()

        assertEquals(expected, restored)
        assertTrue(tokenBase.isFile)
        assertTrue(!tokenBackup.exists())
    }

    @Test
    fun tokenRegistryDoesNotCreateReplacementKeyWhenKeyIsMissing() {
        tokenStore().save(listOf(PairingTokenRecord("e".repeat(64), 120_000L, "REDEEMED")))
        val original = tokenBase.readBytes()
        deleteAlias(TOKEN_KEY)

        assertThrows(IllegalStateException::class.java) {
            tokenStore().load()
        }

        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        assertTrue(!store.containsAlias(TOKEN_KEY))
        assertTrue(original.contentEquals(tokenBase.readBytes()))
    }

    @Test
    fun tokenRegistryRejectsInvalidPersistedState() {
        assertThrows(IllegalArgumentException::class.java) {
            tokenStore().save(listOf(PairingTokenRecord("f".repeat(64), 120_000L, "UNKNOWN")))
        }
        assertTrue(!tokenBase.exists())
    }

    private companion object {
        const val TRUSTED_FILE = "nexus_trusted_devices.enc"
        const val TOKEN_FILE = "nexus_pairing_tokens.enc"
        const val TRUSTED_KEY = "syntra.nexus.trusted.devices.aes.v1"
        const val TOKEN_KEY = "syntra.nexus.token.registry.aes.v1"
    }
}
