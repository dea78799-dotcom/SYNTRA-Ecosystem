package com.syntra.nexus

import java.security.MessageDigest

enum class NexusReplayDecision {
    ACCEPTED, REPLAYED_OR_OUT_OF_ORDER, EXPIRED, CAPACITY_REACHED,
    FRAME_LIMIT_REACHED, INVALID_SIGNATURE_OR_TRANSCRIPT, PERSISTENCE_FAILURE
}

data class NexusReplaySessionRecord(
    val requestId: String,
    val senderFingerprint: String,
    val expiresAtMillis: Long,
    val transcriptHashHex: String,
    val lastSequence: Long,
    val seenNonceHashes: List<String>
)

interface NexusReplayPersistence {
    fun load(): List<NexusReplaySessionRecord>
    fun save(records: List<NexusReplaySessionRecord>)
}

/** Commits each accepted frame before changing memory; storage failures fail closed. */
class NexusHandshakeReplayGuard(
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS,
    private val persistence: NexusReplayPersistence? = null
) {
    private data class SessionReplayState(
        val expiresAtMillis: Long,
        val transcriptHashHex: String,
        var lastSequence: Long,
        val seenNonceHashes: MutableSet<String>
    ) {
        fun snapshot(requestId: String, senderFingerprint: String) =
            NexusReplaySessionRecord(
                requestId, senderFingerprint, expiresAtMillis, transcriptHashHex,
                lastSequence, seenNonceHashes.toList().sorted()
            )
    }

    private val sessions = LinkedHashMap<String, SessionReplayState>()

    init {
        require(maxSessions in 1..HARD_MAX_SESSIONS)
        if (persistence != null) {
            val restored = try {
                persistence.load()
            } catch (error: Exception) {
                throw IllegalStateException("Unable to restore durable handshake replay state.", error)
            }
            require(restored.size <= maxSessions) { "Persisted replay state exceeds configured capacity." }
            restored.forEach { record ->
                validateRecord(record)
                val key = sessionKey(record.requestId, record.senderFingerprint)
                require(!sessions.containsKey(key)) { "Duplicate persisted replay session." }
                sessions[key] = SessionReplayState(
                    record.expiresAtMillis, record.transcriptHashHex,
                    record.lastSequence, record.seenNonceHashes.toMutableSet()
                )
            }
        }
    }

    @Synchronized
    fun accept(frame: NexusHandshakeFrame, nowMillis: Long): NexusReplayDecision {
        require(nowMillis >= 0L)
        if (nowMillis >= frame.expiresAtMillis) return NexusReplayDecision.EXPIRED

        val candidate = LinkedHashMap<String, SessionReplayState>()
        sessions.forEach { (key, value) ->
            if (value.expiresAtMillis > nowMillis) {
                candidate[key] = SessionReplayState(
                    value.expiresAtMillis, value.transcriptHashHex,
                    value.lastSequence, value.seenNonceHashes.toMutableSet()
                )
            }
        }

        val key = sessionKey(frame.requestId, frame.senderFingerprint)
        val nonceHash = sha256Hex(frame.nonce)
        val current = candidate[key]
        if (current == null) {
            if (candidate.size >= maxSessions) return NexusReplayDecision.CAPACITY_REACHED
            if (frame.sequence != 0L) return NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER
            candidate[key] = SessionReplayState(
                frame.expiresAtMillis, sha256Hex(frame.transcriptHash),
                0L, mutableSetOf(nonceHash)
            )
        } else {
            if (frame.expiresAtMillis != current.expiresAtMillis ||
                sha256Hex(frame.transcriptHash) != current.transcriptHashHex ||
                frame.sequence != current.lastSequence + 1L ||
                nonceHash in current.seenNonceHashes
            ) return NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER
            if (current.seenNonceHashes.size >= MAX_FRAMES_PER_SESSION) {
                return NexusReplayDecision.FRAME_LIMIT_REACHED
            }
            current.lastSequence = frame.sequence
            current.seenNonceHashes.add(nonceHash)
        }

        if (!commit(candidate)) return NexusReplayDecision.PERSISTENCE_FAILURE
        sessions.clear()
        sessions.putAll(candidate)
        return NexusReplayDecision.ACCEPTED
    }

    private fun commit(candidate: LinkedHashMap<String, SessionReplayState>): Boolean {
        val store = persistence ?: return true
        return try {
            store.save(candidate.map { (key, state) ->
                val separator = key.indexOf(':')
                state.snapshot(key.substring(0, separator), key.substring(separator + 1))
            })
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun validateRecord(record: NexusReplaySessionRecord) {
        require(record.requestId.matches(REQUEST_ID_PATTERN))
        require(record.senderFingerprint.matches(FINGERPRINT_PATTERN))
        require(record.expiresAtMillis > 0L)
        require(record.transcriptHashHex.matches(FINGERPRINT_PATTERN))
        require(record.lastSequence in 0 until MAX_FRAMES_PER_SESSION.toLong())
        require(record.seenNonceHashes.size == record.lastSequence.toInt() + 1)
        require(record.seenNonceHashes.all { it.matches(FINGERPRINT_PATTERN) })
        require(record.seenNonceHashes.toSet().size == record.seenNonceHashes.size)
    }

    companion object {
        const val DEFAULT_MAX_SESSIONS = 1_024
        const val HARD_MAX_SESSIONS = 4_096
        const val MAX_FRAMES_PER_SESSION = 256
        private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
        private val FINGERPRINT_PATTERN = Regex("^[a-f0-9]{64}$")
        private fun sessionKey(requestId: String, fingerprint: String) = "$requestId:$fingerprint"
        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

object NexusAuthenticatedHandshake {
    fun verifyAndAccept(
        frame: NexusHandshakeFrame,
        transcript: PairingTranscript,
        expectedSenderRole: PairingSignerRole,
        peerPublicKey: java.security.PublicKey,
        nowMillis: Long,
        replayGuard: NexusHandshakeReplayGuard
    ): NexusReplayDecision = when (
        NexusHandshakeFrameVerifier.verify(frame, transcript, expectedSenderRole, peerPublicKey, nowMillis)
    ) {
        NexusHandshakeVerificationResult.EXPIRED -> NexusReplayDecision.EXPIRED
        NexusHandshakeVerificationResult.INVALID -> NexusReplayDecision.INVALID_SIGNATURE_OR_TRANSCRIPT
        NexusHandshakeVerificationResult.VERIFIED -> replayGuard.accept(frame, nowMillis)
    }
}

