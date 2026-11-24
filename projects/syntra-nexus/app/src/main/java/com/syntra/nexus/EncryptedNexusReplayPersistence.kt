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

/** Encrypted atomic storage for local replay counters and hashes; no raw nonce or secret is stored. */
class EncryptedNexusReplayPersistence(context: Context) : NexusReplayPersistence {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val key: SecretKey by lazy { getOrCreateKey() }

    override fun load(): List<NexusReplaySessionRecord> {
        if (!file.baseFile.exists()) return emptyList()
        require(file.baseFile.length() in (IV_LENGTH + TAG_LENGTH_BYTES + 1).toLong()..MAX_FILE_BYTES) {
            "Encrypted replay ledger has an invalid size."
        }
        val encrypted = file.openRead().use { it.readBytes() }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val plaintext = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalStateException("Replay ledger authentication failed.", error)
        }
        return try {
            val root = JSONObject(String(plaintext, Charsets.UTF_8))
            require(root.getInt("version") == FORMAT_VERSION)
            val array = root.getJSONArray("sessions")
            require(array.length() <= NexusHandshakeReplayGuard.HARD_MAX_SESSIONS)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val hashes = item.getJSONArray("nonces")
                    require(hashes.length() <= NexusHandshakeReplayGuard.MAX_FRAMES_PER_SESSION)
                    val nonceHashes = buildList {
                        for (nonceIndex in 0 until hashes.length()) add(hashes.getString(nonceIndex))
                    }
                    add(NexusReplaySessionRecord(
                        item.getString("requestId"),
                        item.getString("sender"),
                        item.getLong("expiresAt"),
                        item.getString("transcript"),
                        item.getLong("lastSequence"),
                        nonceHashes
                    ))
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Replay ledger contents are invalid.", error)
        }
    }

    override fun save(records: List<NexusReplaySessionRecord>) {
        require(records.size <= NexusHandshakeReplayGuard.HARD_MAX_SESSIONS)
        val sessions = JSONArray()
        records.forEach { record ->
            val nonces = JSONArray()
            record.seenNonceHashes.forEach { hash -> nonces.put(hash) }
            sessions.put(JSONObject()
                .put("requestId", record.requestId)
                .put("sender", record.senderFingerprint)
                .put("expiresAt", record.expiresAtMillis)
                .put("transcript", record.transcriptHashHex)
                .put("lastSequence", record.lastSequence)
                .put("nonces", nonces))
        }
        val plaintext = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("sessions", sessions)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(AAD)
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        require(encrypted.size.toLong() <= MAX_FILE_BYTES) { "Replay ledger exceeds storage limit." }

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IllegalStateException("Unable to persist handshake replay state.", error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "nexus_handshake_replay.enc"
        const val KEY_ALIAS = "syntra.nexus.handshake.replay.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        const val MAX_FILE_BYTES = 20L * 1024L * 1024L
        val AAD = "SYNTRA-NEXUS-REPLAY-v1".toByteArray(Charsets.US_ASCII)
    }
}
