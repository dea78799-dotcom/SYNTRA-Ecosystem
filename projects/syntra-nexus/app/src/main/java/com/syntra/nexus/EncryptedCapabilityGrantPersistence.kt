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

interface CapabilityGrantPersistence {
    fun load(): List<CapabilityGrant>
    fun save(grants: List<CapabilityGrant>)
}

/**
 * Encrypted local capability ledger. Only public request metadata, approval
 * signatures and revocation timestamps are stored. No private keys or bearer
 * tokens are accepted by this adapter.
 */
class EncryptedCapabilityGrantPersistence(context: Context) : CapabilityGrantPersistence {
    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val key: SecretKey by lazy { getOrCreateKey() }

    override fun load(): List<CapabilityGrant> = synchronized(FILE_LOCK) {
        val encrypted = try {
            readBoundedFile()
        } catch (_: FileNotFoundException) {
            return@synchronized emptyList()
        }
        require(encrypted.size > IV_LENGTH + TAG_LENGTH_BYTES) {
            "Encrypted capability ledger is truncated."
        }
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
        val plaintext = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalStateException("Capability ledger authentication failed.", error)
        }
        try {
            val array = JSONArray(String(plaintext, Charsets.UTF_8))
            require(array.length() <= MAX_GRANTS) { "Capability ledger exceeds capacity." }
            val ids = HashSet<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val capabilityName = item.getString("capability")
                    val capability = NexusCapability.values().firstOrNull {
                        it.wireName == capabilityName
                    } ?: throw IllegalArgumentException("Unknown capability.")
                    val request = CapabilityGrantRequest(
                        grantId = item.getString("grantId"),
                        peerFingerprint = item.getString("peerFingerprint"),
                        pairingRequestId = item.getString("pairingRequestId"),
                        transcriptHashHex = item.getString("transcriptHashHex"),
                        sessionId = item.getString("sessionId"),
                        capability = capability,
                        issuedAtMillis = item.getLong("issuedAt"),
                        expiresAtMillis = item.getLong("expiresAt")
                    )
                    require(ids.add(request.grantId)) { "Duplicate capability grant ID." }
                    val localSignature = Base64.decode(item.getString("localSignature"), Base64.NO_WRAP)
                    val peerSignature = Base64.decode(item.getString("peerSignature"), Base64.NO_WRAP)
                    require(localSignature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES &&
                        peerSignature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES
                    ) { "Capability approval signature length is invalid." }
                    val revokedAt = if (item.isNull("revokedAt")) null else item.getLong("revokedAt")
                    require(revokedAt == null || revokedAt >= request.issuedAtMillis) {
                        "Capability revocation timestamp is invalid."
                    }
                    add(CapabilityGrant(request, localSignature, peerSignature, revokedAt))
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Capability ledger data is invalid.", error)
        }
    }

    override fun save(grants: List<CapabilityGrant>) = synchronized(FILE_LOCK) {
        require(grants.size <= MAX_GRANTS) { "Capability ledger exceeds capacity." }
        val ids = HashSet<String>()
        val array = JSONArray()
        grants.forEach { grant ->
            val request = grant.request
            require(ids.add(request.grantId)) { "Duplicate capability grant ID." }
            val localSignature = grant.localSignatureCopy()
            val peerSignature = grant.peerSignatureCopy()
            require(localSignature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES &&
                peerSignature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES
            ) { "Capability approval signature length is invalid." }
            require(grant.revokedAtMillis == null ||
                grant.revokedAtMillis >= request.issuedAtMillis
            ) { "Capability revocation timestamp is invalid." }
            array.put(JSONObject()
                .put("grantId", request.grantId)
                .put("peerFingerprint", request.peerFingerprint)
                .put("pairingRequestId", request.pairingRequestId)
                .put("transcriptHashHex", request.transcriptHashHex)
                .put("sessionId", request.sessionId)
                .put("capability", request.capability.wireName)
                .put("issuedAt", request.issuedAtMillis)
                .put("expiresAt", request.expiresAtMillis)
                .put("localSignature", Base64.encodeToString(localSignature, Base64.NO_WRAP))
                .put("peerSignature", Base64.encodeToString(peerSignature, Base64.NO_WRAP))
                .put("revokedAt", grant.revokedAtMillis ?: JSONObject.NULL))
        }
        val plaintext = array.toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Capability ledger exceeds storage limit." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(AAD)
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        require(encrypted.size <= MAX_FILE_BYTES) { "Encrypted capability ledger exceeds storage limit." }

        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            if (stream != null) file.failWrite(stream)
            throw IllegalStateException("Unable to save encrypted capability ledger.", error)
        }
    }

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
                require(total <= MAX_FILE_BYTES) { "Encrypted capability ledger exceeds storage limit." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(!hasPersistedFile()) {
            "Capability ledger Keystore key is missing while encrypted data still exists."
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
        const val FILE_NAME = "nexus_capability_grants.enc"
        const val KEY_ALIAS = "syntra.nexus.capability.grants.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        const val MIN_SIGNATURE_BYTES = 8
        const val MAX_SIGNATURE_BYTES = 128
        const val MAX_GRANTS = 128
        const val MAX_PLAINTEXT_BYTES = 256 * 1024
        const val MAX_FILE_BYTES = 512 * 1024
        val AAD = "SYNTRA-NEXUS-CAPABILITY-GRANTS-v1".toByteArray(Charsets.UTF_8)
    }
}
