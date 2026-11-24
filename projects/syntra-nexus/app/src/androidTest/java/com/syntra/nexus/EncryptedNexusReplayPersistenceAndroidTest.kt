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
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedNexusReplayPersistenceAndroidTest {
    private lateinit var context: Context
    private lateinit var baseFile: File
    private lateinit var backupFile: File

    private fun persistence() = EncryptedNexusReplayPersistence(context)

    private fun record() = NexusReplaySessionRecord(
        requestId = REQUEST_ID,
        senderFingerprint = "a".repeat(64),
        expiresAtMillis = 120_000L,
        transcriptHashHex = "b".repeat(64),
        lastSequence = 0L,
        seenNonceHashes = listOf("c".repeat(64))
    )

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
    fun encryptedReplayLedgerSurvivesPersistenceObjectRecreation() {
        persistence().save(listOf(record()))

        val restored = persistence().load()

        assertEquals(listOf(record()), restored)
        assertTrue(baseFile.isFile)
    }

    @Test
    fun openReadRecoversCommittedBackupWhenBaseFileIsMissing() {
        persistence().save(listOf(record()))
        assertTrue(baseFile.isFile)
        assertTrue(baseFile.renameTo(backupFile))

        val restored = persistence().load()

        assertEquals(listOf(record()), restored)
        assertTrue(baseFile.isFile)
        assertTrue(!backupFile.exists())
    }

    @Test
    fun openReadRecoversCommittedBackupOverPartialReplacement() {
        persistence().save(listOf(record()))
        assertTrue(baseFile.renameTo(backupFile))
        baseFile.writeBytes(byteArrayOf(0x53, 0x4e, 0x58, 0x31))

        val restored = persistence().load()

        assertEquals(listOf(record()), restored)
        assertTrue(baseFile.isFile)
        assertTrue(!backupFile.exists())
    }

    @Test
    fun ciphertextTamperingFailsClosedAndPreservesFile() {
        persistence().save(listOf(record()))
        val original = baseFile.readBytes()
        val tampered = original.copyOf()
        tampered[IV_LENGTH] = (tampered[IV_LENGTH].toInt() xor 0x01).toByte()
        baseFile.writeBytes(tampered)

        assertThrows(IllegalStateException::class.java) {
            persistence().load()
        }
        assertTrue(baseFile.isFile)
    }

    @Test
    fun missingKeystoreKeyDoesNotCreateReplacementOrEraseLedger() {
        persistence().save(listOf(record()))
        val original = baseFile.readBytes()
        deleteKey()

        assertThrows(IllegalStateException::class.java) {
            persistence().load()
        }

        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        assertTrue(!store.containsAlias(KEY_ALIAS))
        assertTrue(original.contentEquals(baseFile.readBytes()))
    }

    private companion object {
        const val FILE_NAME = "nexus_handshake_replay.enc"
        const val KEY_ALIAS = "syntra.nexus.handshake.replay.aes.v1"
        const val IV_LENGTH = 12
        const val REQUEST_ID = "abcdefghijklmnopqrstuv"
    }
}
