package com.syntra.nexus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairingTokenRecord(
    val fingerprint: String,
    val expiresAtMillis: Long,
    val state: String
)

interface PairingTokenPersistence {
    fun load(): List<PairingTokenRecord>
    fun save(records: List<PairingTokenRecord>)
}

/**
 * Encrypts local invitation fingerprints and their states using an AES-256-GCM
 * key held in Android Keystore. Raw invitation tokens are never persisted.
 */
class EncryptedPairingTokenPersistence(context: Context) : PairingTokenPersistence {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val key: SecretKey by lazy { getOrCreateKey() }

    override fun load(): List<PairingTokenRecord> = synchronized(FILE_LOCK) {
        val encrypted = try {
            readBoundedFile()
        } catch (_: FileNotFoundException) {
            return@synchronized emptyList()
        }
        require(encrypted.size > IV_LENGTH + TAG_LENGTH_BYTES) {
            "Encrypted token registry is truncated."
        }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val plaintext = try {
            decrypt(ciphertext, iv)
        } catch (error: Exception) {
            throw IllegalStateException("Token registry authentication failed.", error)
        }
        try {
            val array = JSONArray(String(plaintext, Charsets.UTF_8))
            require(array.length() <= MAX_ENTRIES) {
                "Token registry exceeds its entry limit."
            }
            val records = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(PairingTokenRecord(
                        fingerprint = item.getString("fingerprint"),
                        expiresAtMillis = item.getLong("expiresAt"),
                        state = item.getString("state")
                    ))
                }
            }
            validateRecords(records)
            records
        } catch (error: Exception) {
            throw IllegalStateException("Encrypted token registry data is invalid.", error)
        }
    }

    override fun save(records: List<PairingTokenRecord>) = synchronized(FILE_LOCK) {
        validateRecords(records)
        val json = JSONArray()
        records.forEach { record ->
            json.put(JSONObject()
                .put("fingerprint", record.fingerprint)
                .put("expiresAt", record.expiresAtMillis)
                .put("state", record.state))
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        require(encrypted.size.toLong() <= MAX_FILE_BYTES) {
            "Token registry exceeds the storage limit."
        }

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IllegalStateException("Unable to save encrypted token registry.", error)
        }
    }

    /** Use openRead() before checking absence so AtomicFile can restore .bak. */
    private fun readBoundedFile(): ByteArray {
        val input = file.openRead()
        input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_FILE_BYTES) {
                    "Token registry exceeds the storage limit."
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun validateRecords(records: List<PairingTokenRecord>) {
        require(records.size <= MAX_ENTRIES)
        val fingerprints = HashSet<String>(records.size)
        records.forEach { record ->
            require(record.fingerprint.matches(FINGERPRINT_PATTERN))
            require(record.expiresAtMillis > 0L)
            require(record.state in VALID_STATES)
            require(fingerprints.add(record.fingerprint)) {
                "Token registry contains duplicate fingerprints."
            }
        }
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(!hasPersistedFile()) {
            "Token-registry Keystore key is missing while encrypted data still exists."
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun hasPersistedFile(): Boolean =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private companion object {
        val FILE_LOCK = Any()
        const val FILE_NAME = "nexus_pairing_tokens.enc"
        const val KEY_ALIAS = "syntra.nexus.token.registry.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        const val MAX_ENTRIES = 4_096
        const val MAX_FILE_BYTES = 1L * 1024L * 1024L
        val FINGERPRINT_PATTERN = Regex("^[a-f0-9]{64}$")
        val VALID_STATES = setOf("ACTIVE", "REDEEMED", "REVOKED")
    }
}
