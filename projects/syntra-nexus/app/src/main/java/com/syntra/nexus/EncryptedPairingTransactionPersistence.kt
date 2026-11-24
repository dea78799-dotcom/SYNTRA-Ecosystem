package com.syntra.nexus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encrypted, atomic persistence for one pairing transaction.
 * The file contains no QR bearer token, private key, approval secret or payload.
 */
class EncryptedPairingTransactionPersistence(context: Context) : PairingTransactionPersistence {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val key: SecretKey by lazy { getOrCreateKey() }

    override fun load(): PairingTransactionSnapshot? {
        val encrypted = try {
            file.openRead().use { input ->
                val output = ByteArrayOutputStream(MIN_ENCRYPTED_BYTES)
                val buffer = ByteArray(256)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_ENCRYPTED_BYTES) {
                        "Pairing transaction file exceeds the storage limit."
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (error: Exception) {
            throw IllegalStateException("Unable to read pairing transaction storage.", error)
        }
        require(encrypted.size in MIN_ENCRYPTED_BYTES..MAX_ENCRYPTED_BYTES) {
            "Pairing transaction ciphertext is outside the permitted size."
        }

        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val plaintext = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Pairing transaction authentication failed. Storage was not reset.",
                error
            )
        }

        return try {
            PairingTransactionSnapshotCodec.decode(plaintext)
        } catch (error: Exception) {
            throw IllegalStateException("Pairing transaction contents are invalid.", error)
        }
    }

    override fun save(snapshot: PairingTransactionSnapshot) {
        val plaintext = PairingTransactionSnapshotCodec.encode(snapshot)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(AAD)
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        require(encrypted.size in MIN_ENCRYPTED_BYTES..MAX_ENCRYPTED_BYTES) {
            "Pairing transaction exceeds the storage limit."
        }

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IllegalStateException("Unable to persist pairing transaction.", error)
        }
    }

    override fun clear() {
        try {
            file.delete()
        } catch (error: Exception) {
            throw IllegalStateException("Unable to clear pairing transaction.", error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(!file.baseFile.exists()) {
            "Pairing transaction key is missing while encrypted data still exists."
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "nexus_pairing_transaction.enc"
        const val KEY_ALIAS = "syntra.nexus.pairing.transaction.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        const val PLAINTEXT_LENGTH = 172
        const val MIN_ENCRYPTED_BYTES = IV_LENGTH + TAG_LENGTH_BYTES + PLAINTEXT_LENGTH
        const val MAX_ENCRYPTED_BYTES = MIN_ENCRYPTED_BYTES
        val AAD = "SYNTRA-NEXUS-PAIRING-TRANSACTION-v1".toByteArray(Charsets.US_ASCII)
    }
}

/** Fixed-width codec so malformed or trailing transaction data is rejected. */
internal object PairingTransactionSnapshotCodec {
    private val MAGIC = byteArrayOf(0x53, 0x4e, 0x54, 0x58) // SNTX
    private const val VERSION = 1
    private const val ENCODED_LENGTH = 172

    fun encode(snapshot: PairingTransactionSnapshot): ByteArray {
        validate(snapshot)
        val bytes = ByteArrayOutputStream(ENCODED_LENGTH)
        DataOutputStream(bytes).use { output ->
            output.write(MAGIC)
            output.writeByte(VERSION)
            output.write(snapshot.requestId.toByteArray(Charsets.US_ASCII))
            output.writeLong(snapshot.expiresAtMillis)
            output.write(snapshot.transcriptHashHex.toByteArray(Charsets.US_ASCII))
            output.write(snapshot.peerFingerprint.toByteArray(Charsets.US_ASCII))
            output.writeByte(PairingTransactionState.values().indexOf(snapshot.state))
            output.writeLong(snapshot.updatedAtMillis)
        }
        return bytes.toByteArray().also {
            check(it.size == ENCODED_LENGTH) { "Unexpected pairing transaction encoding length." }
        }
    }

    fun decode(bytes: ByteArray): PairingTransactionSnapshot {
        require(bytes.size == ENCODED_LENGTH) { "Invalid pairing transaction length." }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Unknown pairing transaction marker." }
            require(input.readUnsignedByte() == VERSION) { "Unsupported pairing transaction version." }

            val requestId = readAscii(input, 22)
            val expiry = input.readLong()
            val transcript = readAscii(input, 64)
            val fingerprint = readAscii(input, 64)
            val stateIndex = input.readUnsignedByte()
            val states = PairingTransactionState.values()
            require(stateIndex in states.indices) { "Unknown pairing transaction state." }
            val updatedAt = input.readLong()
            require(input.available() == 0) { "Unexpected trailing transaction data." }

            return PairingTransactionSnapshot(
                requestId = requestId,
                expiresAtMillis = expiry,
                transcriptHashHex = transcript,
                peerFingerprint = fingerprint,
                state = states[stateIndex],
                updatedAtMillis = updatedAt
            ).also(::validate)
        }
    }

    private fun readAscii(input: DataInputStream, length: Int): String {
        val bytes = ByteArray(length)
        input.readFully(bytes)
        require(bytes.all { (it.toInt() and 0xff) in 0x20..0x7e }) {
            "Pairing transaction contains non-ASCII data."
        }
        return String(bytes, Charsets.US_ASCII)
    }

    private fun validate(snapshot: PairingTransactionSnapshot) {
        require(snapshot.requestId.matches(Regex("^[A-Za-z0-9_-]{22}$")))
        require(snapshot.expiresAtMillis > 0L && snapshot.updatedAtMillis >= 0L)
        require(snapshot.transcriptHashHex.matches(Regex("^[a-f0-9]{64}$")))
        require(snapshot.peerFingerprint.matches(Regex("^[a-f0-9]{64}$")))
    }
}
