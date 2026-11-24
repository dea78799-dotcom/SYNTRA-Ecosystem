package com.syntra.nexus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    override fun load(): List<PairingTokenRecord> {
        if (!file.baseFile.exists()) return emptyList()
        val encrypted = try {
            file.openRead().use { it.readBytes() }
        } catch (e: Exception) {
            throw IllegalStateException("Unable to read encrypted token registry.", e)
        }
        require(encrypted.size > IV_LENGTH + TAG_LENGTH_BYTES) {
            "Encrypted registry is truncated."
        }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val plaintext = decrypt(ciphertext, iv)
        return try {
            val array = JSONArray(String(plaintext, Charsets.UTF_8))
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        PairingTokenRecord(
                            fingerprint = item.getString("fingerprint"),
                            expiresAtMillis = item.getLong("expiresAt"),
                            state = item.getString("state")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Encrypted registry data is invalid.", e)
        }
    }

    override fun save(records: List<PairingTokenRecord>) {
        val json = JSONArray()
        records.forEach { record ->
            json.put(
                JSONObject()
                    .put("fingerprint", record.fingerprint)
                    .put("expiresAt", record.expiresAtMillis)
                    .put("state", record.state)
            )
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val actualIv = cipher.iv
        val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = actualIv + ciphertext

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(output)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (e: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IllegalStateException("Unable to save encrypted token registry.", e)
        }
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val FILE_NAME = "nexus_pairing_tokens.enc"
        private const val KEY_ALIAS = "syntra.nexus.token.registry.aes.v1"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
    }
}
