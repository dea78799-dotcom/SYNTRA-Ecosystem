package com.syntra.nexus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedCapabilityGrantPersistenceAndroidTest {
    private lateinit var context: Context
    private lateinit var baseFile: File
    private lateinit var backupFile: File

    private fun persistence() = EncryptedCapabilityGrantPersistence(context)

    private fun grant(revokedAt: Long? = null): CapabilityGrant {
        val request = CapabilityGrantRequest(
            grantId = "GGGGGGGGGGGGGGGGGGGGGG",
            peerFingerprint = "a".repeat(64),
            pairingRequestId = "PPPPPPPPPPPPPPPPPPPPPP",
            transcriptHashHex = "b".repeat(64),
            sessionId = "SSSSSSSSSSSSSSSSSSSSSS",
            capability = NexusCapability.USAGE_SUMMARY_READ,
            issuedAtMillis = 1_000L,
            expiresAtMillis = 60_000L
        )
        return CapabilityGrant(request, ByteArray(64) { 1 }, ByteArray(64) { 2 }, revokedAt)
    }

    private fun deleteKey() {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        baseFile = File(context.filesDir, FILE_NAME)
        backupFile = File(baseFile.path + ".bak")
        baseFile.delete()
        backupFile.delete()
        deleteKey()
    }

    @After
    fun tearDown() {
        baseFile.delete()
        backupFile.delete()
        deleteKey()
    }

    @Test
    fun encryptedGrantSurvivesPersistenceObjectRecreation() {
        persistence().save(listOf(grant()))
        val restored = persistence().load()
        assertEquals(1, restored.size)
        assertEquals(grant().request, restored.single().request)
        assertTrue(restored.single().localSignatureCopy().contentEquals(ByteArray(64) { 1 }))
        assertTrue(baseFile.isFile)
    }

    @Test
    fun revokedGrantStateSurvivesRestart() {
        persistence().save(listOf(grant(revokedAt = 2_000L)))
        assertEquals(2_000L, persistence().load().single().revokedAtMillis)
    }

    @Test
    fun ciphertextModificationFailsAuthenticationAndPreservesFile() {
        persistence().save(listOf(grant()))
        val tampered = baseFile.readBytes()
        tampered[IV_LENGTH] = (tampered[IV_LENGTH].toInt() xor 0x01).toByte()
        baseFile.writeBytes(tampered)
        assertThrows(IllegalStateException::class.java) { persistence().load() }
        assertTrue(baseFile.isFile)
    }

    @Test
    fun missingKeystoreKeyDoesNotReplaceKeyOrEraseCiphertext() {
        persistence().save(listOf(grant()))
        val original = baseFile.readBytes()
        deleteKey()
        assertThrows(IllegalStateException::class.java) { persistence().load() }
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        assertTrue(!store.containsAlias(KEY_ALIAS))
        assertTrue(baseFile.isFile)
        assertTrue(original.contentEquals(baseFile.readBytes()))
    }

    @Test
    fun atomicFileRestoresPreviousCommittedGrantLedger() {
        persistence().save(listOf(grant()))
        assertTrue(baseFile.renameTo(backupFile))
        baseFile.writeBytes(byteArrayOf(0x53, 0x4e, 0x58, 0x47))
        assertEquals(grant().request, persistence().load().single().request)
        assertTrue(baseFile.isFile)
        assertTrue(!backupFile.exists())
    }

    private companion object {
        const val FILE_NAME = "nexus_capability_grants.enc"
        const val KEY_ALIAS = "syntra.nexus.capability.grants.aes.v1"
        const val IV_LENGTH = 12
    }
}
