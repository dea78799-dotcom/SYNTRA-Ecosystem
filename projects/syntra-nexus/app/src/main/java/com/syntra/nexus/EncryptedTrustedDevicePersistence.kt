package com.syntra.nexus

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
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

/**
 * AES-256-GCM persistence for trusted public identities.
 * The encryption key is non-exportable and managed by Android Keystore.
 * No private key or pairing secret is stored in this file.
 */
class EncryptedTrustedDevicePersistence(context: Context) : TrustedDevicePersistence {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val key: SecretKey by lazy { getOrCreateKey() }

    override fun load(): List<TrustedDeviceRecord> = synchronized(FILE_LOCK) {
        val encrypted = try {
            readBoundedFile()
        } catch (_: FileNotFoundException) {
            return@synchronized emptyList()
        }
        require(encrypted.size > IV_LENGTH + TAG_LENGTH_BYTES) {
            "Encrypted trusted-device registry is truncated."
        }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val plaintext = try {
            decrypt(ciphertext, iv)
        } catch (error: Exception) {
            throw IllegalStateException("Trusted-device registry authentication failed.", error)
        }
        try {
            val array = JSONArray(String(plaintext, Charsets.UTF_8))
            require(array.length() <= MAX_RECORDS) {
                "Trusted-device registry exceeds capacity."
            }
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val revoked = if (item.isNull("revokedAt")) null else item.getLong("revokedAt")
                    add(TrustedDeviceRecord(
                        fingerprint = item.getString("fingerprint"),
                        publicKeyEncoded = Base64.decode(
                            item.getString("publicKey"),
                            Base64.NO_WRAP
                        ),
                        displayName = item.getString("displayName"),
                        trustedAtMillis = item.getLong("trustedAt"),
                        revokedAtMillis = revoked
                    ))
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Encrypted trusted-device registry data is invalid.", error)
        }
    }

    override fun save(records: List<TrustedDeviceRecord>) = synchronized(FILE_LOCK) {
        require(records.size <= MAX_RECORDS) {
            "Trusted-device registry exceeds capacity."
        }
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject()
                .put("fingerprint", record.fingerprint)
                .put("publicKey", Base64.encodeToString(record.publicKeyEncoded, Base64.NO_WRAP))
                .put("displayName", record.displayName)
                .put("trustedAt", record.trustedAtMillis)
                .put("revokedAt", record.revokedAtMillis ?: JSONObject.NULL))
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.iv + cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        require(encrypted.size.toLong() <= MAX_FILE_BYTES) {
            "Trusted-device registry exceeds the storage limit."
        }

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IllegalStateException("Unable to save encrypted trusted-device registry.", error)
        }
    }

    /** AtomicFile.openRead() restores a backup even if the base file is absent. */
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
                    "Trusted-device registry exceeds the storage limit."
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
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
            "Trusted-device Keystore key is missing while encrypted data still exists."
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
        const val FILE_NAME = "nexus_trusted_devices.enc"
        const val KEY_ALIAS = "syntra.nexus.trusted.devices.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        const val MAX_RECORDS = 128
        const val MAX_FILE_BYTES = 2L * 1024L * 1024L
    }
}
