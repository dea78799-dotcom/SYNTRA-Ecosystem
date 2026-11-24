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
class EncryptedPairingTransactionPersistenceAndroidTest {
    private lateinit var context: Context
    private lateinit var baseFile: File
    private lateinit var backupFile: File

    private fun persistence() = EncryptedPairingTransactionPersistence(context)

    private fun snapshot(state: PairingTransactionState = PairingTransactionState.CREATED) =
        PairingTransactionSnapshot(
            requestId = "abcdefghijklmnopqrstuv",
            expiresAtMillis = 120_000L,
            transcriptHashHex = "a".repeat(64),
            peerFingerprint = "b".repeat(64),
            state = state,
            updatedAtMillis = 60_000L
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
    fun encryptedSnapshotSurvivesPersistenceObjectRecreation() {
        persistence().save(snapshot(PairingTransactionState.LOCAL_APPROVAL_PENDING))

        val restored = persistence().load()

        assertEquals(snapshot(PairingTransactionState.LOCAL_APPROVAL_PENDING), restored)
        assertTrue(baseFile.isFile)
        assertEquals(200L, baseFile.length())
    }

    @Test
    fun ciphertextModificationFailsAuthenticationAndPreservesFile() {
        persistence().save(snapshot())
        val tampered = baseFile.readBytes()
        tampered[IV_LENGTH] = (tampered[IV_LENGTH].toInt() xor 0x01).toByte()
        baseFile.writeBytes(tampered)

        assertThrows(IllegalStateException::class.java) {
            persistence().load()
        }
        assertTrue("Corrupted evidence must not be silently deleted.", baseFile.isFile)
    }

    @Test
    fun missingKeystoreKeyDoesNotCreateReplacementOrEraseCiphertext() {
        persistence().save(snapshot())
        val original = baseFile.readBytes()
        deleteKey()

        assertThrows(IllegalStateException::class.java) {
            persistence().load()
        }

        assertTrue(baseFile.isFile)
        assertTrue(original.contentEquals(baseFile.readBytes()))
    }

    @Test
    fun atomicFileRestoresLastGoodBackupAfterInterruptedReplacement() {
        val expected = snapshot(PairingTransactionState.TOKEN_REDEEMED)
        persistence().save(expected)

        assertTrue("The committed base file must exist.", baseFile.isFile)
        assertTrue("Could not stage AtomicFile backup.", baseFile.renameTo(backupFile))
        baseFile.writeBytes(byteArrayOf(0x53, 0x4e, 0x54, 0x58))

        val restored = persistence().load()

        assertEquals(expected, restored)
        assertTrue(baseFile.isFile)
        assertTrue("Recovered backup should be consumed.", !backupFile.exists())
    }

    private companion object {
        const val FILE_NAME = "nexus_pairing_transaction.enc"
        const val KEY_ALIAS = "syntra.nexus.pairing.transaction.aes.v1"
        const val IV_LENGTH = 12
    }
}
