package com.syntra.nexus

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature

enum class NexusHandshakeKind(val wireCode: Int) {
    IDENTITY_PROOF(1),
    APPROVAL(2);

    companion object {
        internal fun fromWireCode(code: Int): NexusHandshakeKind? =
            values().firstOrNull { it.wireCode == code }
    }
}

/**
 * Signed, transcript-bound handshake frame. It contains no invitation secret.
 * Use the existing bounded transport packet as the outer network envelope.
 */
class NexusHandshakeFrame private constructor(
    val kind: NexusHandshakeKind,
    val requestId: String,
    val expiresAtMillis: Long,
    val senderRole: PairingSignerRole,
    val sequence: Long,
    val senderFingerprint: String,
    val recipientFingerprint: String,
    transcriptHash: ByteArray,
    nonce: ByteArray,
    signature: ByteArray
) {
    private val transcriptHashBytes = transcriptHash.copyOf()
    private val nonceBytes = nonce.copyOf()
    private val signatureBytes = signature.copyOf()

    val transcriptHash: ByteArray get() = transcriptHashBytes.copyOf()
    val nonce: ByteArray get() = nonceBytes.copyOf()
    val signature: ByteArray get() = signatureBytes.copyOf()

    internal fun canonicalBytes(): ByteArray = NexusHandshakeFrameCodec.canonicalBytes(
        kind, requestId, expiresAtMillis, senderRole, sequence,
        senderFingerprint, recipientFingerprint, transcriptHashBytes, nonceBytes
    )

    companion object {
        fun createSigned(
            kind: NexusHandshakeKind,
            transcript: PairingTranscript,
            senderRole: PairingSignerRole,
            sequence: Long,
            privateKey: PrivateKey,
            random: SecureRandom = SecureRandom()
        ): NexusHandshakeFrame {
            require(sequence >= 0L) { "Handshake sequence cannot be negative." }
            val recipientRole = opposite(senderRole)
            val senderFingerprint = PairingIdentityCrypto.fingerprint(
                transcript.publicKeyFor(senderRole)
            )
            val recipientFingerprint = PairingIdentityCrypto.fingerprint(
                transcript.publicKeyFor(recipientRole)
            )
            val nonce = ByteArray(NexusHandshakeFrameCodec.NONCE_BYTES).also(random::nextBytes)
            requirePrivateKeyMatchesTranscriptRole(
                privateKey,
                transcript.publicKeyFor(senderRole),
                random
            )
            val canonical = NexusHandshakeFrameCodec.canonicalBytes(
                kind, transcript.requestId, transcript.expiresAtMillis, senderRole,
                sequence, senderFingerprint, recipientFingerprint, transcript.sha256, nonce
            )
            val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
            signer.initSign(privateKey, random)
            signer.update(canonical)
            val signature = signer.sign()
            require(signature.size in 1..NexusHandshakeFrameCodec.MAX_SIGNATURE_BYTES) {
                "Handshake signature has an invalid size."
            }
            return NexusHandshakeFrame(
                kind, transcript.requestId, transcript.expiresAtMillis, senderRole,
                sequence, senderFingerprint, recipientFingerprint,
                transcript.sha256, nonce, signature
            )
        }

        internal fun decoded(
            kind: NexusHandshakeKind,
            requestId: String,
            expiresAtMillis: Long,
            senderRole: PairingSignerRole,
            sequence: Long,
            senderFingerprint: String,
            recipientFingerprint: String,
            transcriptHash: ByteArray,
            nonce: ByteArray,
            signature: ByteArray
        ) = NexusHandshakeFrame(
            kind, requestId, expiresAtMillis, senderRole, sequence,
            senderFingerprint, recipientFingerprint, transcriptHash, nonce, signature
        )

        private fun opposite(role: PairingSignerRole): PairingSignerRole =
            if (role == PairingSignerRole.INITIATOR) PairingSignerRole.RESPONDER
            else PairingSignerRole.INITIATOR

        private fun requirePrivateKeyMatchesTranscriptRole(
            privateKey: PrivateKey,
            expectedPublicKeyEncoded: ByteArray,
            random: SecureRandom
        ) {
            val publicKey = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(expectedPublicKeyEncoded))
            val challenge = ByteArray(32).also(random::nextBytes)
            val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
            signer.initSign(privateKey, random)
            signer.update(challenge)
            val proof = signer.sign()
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(challenge)
            require(verifier.verify(proof)) {
                "Local private key does not match the sender identity in the transcript."
            }
        }

        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}

object NexusHandshakeFrameCodec {
    const val VERSION = 1
    const val NONCE_BYTES = 32
    const val TRANSCRIPT_HASH_BYTES = 32
    const val FINGERPRINT_CHARS = 64
    const val MAX_SIGNATURE_BYTES = 80
    const val HEADER_BYTES = 239
    const val MAX_FRAME_BYTES = HEADER_BYTES + MAX_SIGNATURE_BYTES

    private val MAGIC = byteArrayOf(0x53, 0x4e, 0x48, 0x31) // SNH1
    private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
    private val FINGERPRINT_PATTERN = Regex("^[a-f0-9]{64}$")

    internal fun canonicalBytes(
        kind: NexusHandshakeKind,
        requestId: String,
        expiresAtMillis: Long,
        senderRole: PairingSignerRole,
        sequence: Long,
        senderFingerprint: String,
        recipientFingerprint: String,
        transcriptHash: ByteArray,
        nonce: ByteArray
    ): ByteArray {
        require(requestId.matches(REQUEST_ID_PATTERN))
        require(expiresAtMillis > 0L && sequence >= 0L)
        require(senderFingerprint.matches(FINGERPRINT_PATTERN))
        require(recipientFingerprint.matches(FINGERPRINT_PATTERN))
        require(senderFingerprint != recipientFingerprint)
        require(transcriptHash.size == TRANSCRIPT_HASH_BYTES)
        require(nonce.size == NONCE_BYTES)

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeByte(VERSION)
            data.writeByte(kind.wireCode)
            data.writeByte(senderRole.wireValue)
            data.writeLong(sequence)
            data.writeLong(expiresAtMillis)
            data.write(requestId.toByteArray(StandardCharsets.US_ASCII))
            data.write(transcriptHash)
            data.write(senderFingerprint.toByteArray(StandardCharsets.US_ASCII))
            data.write(recipientFingerprint.toByteArray(StandardCharsets.US_ASCII))
            data.write(nonce)
        }
        return output.toByteArray()
    }

    fun encode(frame: NexusHandshakeFrame): ByteArray {
        val canonical = frame.canonicalBytes()
        val signature = frame.signature
        require(signature.size in 1..MAX_SIGNATURE_BYTES)
        return ByteBuffer.allocate(canonical.size + 2 + signature.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(canonical)
            .putShort(signature.size.toShort())
            .put(signature)
            .array()
    }

    fun decode(encoded: ByteArray): NexusHandshakeFrame {
        require(encoded.size in (HEADER_BYTES + 1)..MAX_FRAME_BYTES) {
            "Handshake frame length is outside the permitted range."
        }
        val input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size)
        input.get(magic)
        require(magic.contentEquals(MAGIC)) { "Invalid handshake marker." }
        require((input.get().toInt() and 0xff) == VERSION) {
            "Unsupported handshake version."
        }
        val kind = NexusHandshakeKind.fromWireCode(input.get().toInt() and 0xff)
            ?: throw IllegalArgumentException("Unknown handshake message kind.")
        val roleCode = input.get().toInt() and 0xff
        val role = PairingSignerRole.values().firstOrNull { it.wireValue == roleCode }
            ?: throw IllegalArgumentException("Unknown handshake sender role.")
        val sequence = input.long
        val expiry = input.long
        require(sequence >= 0L && expiry > 0L) { "Invalid handshake counters." }

        val requestBytes = ByteArray(22)
        input.get(requestBytes)
        val requestId = String(requestBytes, StandardCharsets.US_ASCII)
        require(requestId.matches(REQUEST_ID_PATTERN)) { "Invalid handshake request ID." }

        val transcriptHash = ByteArray(TRANSCRIPT_HASH_BYTES).also(input::get)
        val senderFingerprintBytes = ByteArray(FINGERPRINT_CHARS).also(input::get)
        val recipientFingerprintBytes = ByteArray(FINGERPRINT_CHARS).also(input::get)
        val senderFingerprint = String(senderFingerprintBytes, StandardCharsets.US_ASCII)
        val recipientFingerprint = String(recipientFingerprintBytes, StandardCharsets.US_ASCII)
        require(senderFingerprint.matches(FINGERPRINT_PATTERN))
        require(recipientFingerprint.matches(FINGERPRINT_PATTERN))
        require(senderFingerprint != recipientFingerprint)
        val nonce = ByteArray(NONCE_BYTES).also(input::get)

        val signatureLength = input.short.toInt() and 0xffff
        require(signatureLength in 1..MAX_SIGNATURE_BYTES)
        require(input.remaining() == signatureLength) {
            "Handshake signature length does not match the frame."
        }
        val signature = ByteArray(signatureLength).also(input::get)

        return NexusHandshakeFrame.decoded(
            kind, requestId, expiry, role, sequence, senderFingerprint,
            recipientFingerprint, transcriptHash, nonce, signature
        )
    }
}

enum class NexusHandshakeVerificationResult {
    VERIFIED,
    EXPIRED,
    INVALID
}

/** Verifies the signed frame against one exact transcript and peer role. */
object NexusHandshakeFrameVerifier {
    fun verify(
        frame: NexusHandshakeFrame,
        transcript: PairingTranscript,
        expectedSenderRole: PairingSignerRole,
        peerPublicKey: PublicKey,
        nowMillis: Long
    ): NexusHandshakeVerificationResult {
        if (nowMillis < 0L) return NexusHandshakeVerificationResult.INVALID
        if (nowMillis >= frame.expiresAtMillis || nowMillis >= transcript.expiresAtMillis) {
            return NexusHandshakeVerificationResult.EXPIRED
        }
        if (frame.expiresAtMillis != transcript.expiresAtMillis ||
            frame.requestId != transcript.requestId ||
            frame.senderRole != expectedSenderRole ||
            frame.senderRole == opposite(expectedSenderRole) ||
            !MessageDigest.isEqual(frame.transcriptHash, transcript.sha256)
        ) return NexusHandshakeVerificationResult.INVALID

        val recipientRole = opposite(expectedSenderRole)
        val expectedSenderKey = transcript.publicKeyFor(expectedSenderRole)
        val expectedRecipientKey = transcript.publicKeyFor(recipientRole)
        if (!MessageDigest.isEqual(peerPublicKey.encoded, expectedSenderKey)) {
            return NexusHandshakeVerificationResult.INVALID
        }
        if (frame.senderFingerprint != PairingIdentityCrypto.fingerprint(expectedSenderKey) ||
            frame.recipientFingerprint != PairingIdentityCrypto.fingerprint(expectedRecipientKey)
        ) return NexusHandshakeVerificationResult.INVALID

        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(peerPublicKey)
            verifier.update(frame.canonicalBytes())
            if (verifier.verify(frame.signature)) NexusHandshakeVerificationResult.VERIFIED
            else NexusHandshakeVerificationResult.INVALID
        } catch (_: Exception) {
            NexusHandshakeVerificationResult.INVALID
        }
    }

    private fun opposite(role: PairingSignerRole): PairingSignerRole =
        if (role == PairingSignerRole.INITIATOR) PairingSignerRole.RESPONDER
        else PairingSignerRole.INITIATOR
}

enum class NexusReplayDecision {
    ACCEPTED,
    REPLAYED_OR_OUT_OF_ORDER,
    EXPIRED,
    CAPACITY_REACHED,
    INVALID_SIGNATURE_OR_TRANSCRIPT
}

/**
 * Bounded, session-scoped replay guard. Call only after signature verification.
 * This in-memory guard is not durable across process death or shared between peers.
 */
class NexusHandshakeReplayGuard(
    private val maxSessions: Int = DEFAULT_MAX_SESSIONS
) {
    private data class SessionReplayState(
        val expiresAtMillis: Long,
        var lastSequence: Long,
        val seenNonceHashes: MutableSet<String>
    )

    private val sessions = LinkedHashMap<String, SessionReplayState>()

    init {
        require(maxSessions in 1..HARD_MAX_SESSIONS)
    }

    @Synchronized
    fun accept(frame: NexusHandshakeFrame, nowMillis: Long): NexusReplayDecision {
        require(nowMillis >= 0L)
        if (nowMillis >= frame.expiresAtMillis) return NexusReplayDecision.EXPIRED
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.expiresAtMillis <= nowMillis) iterator.remove()
        }

        val key = frame.requestId + ":" + frame.senderFingerprint + ":" +
            frame.transcriptHash.toHexString()
        val nonceHash = MessageDigest.getInstance("SHA-256").digest(frame.nonce).toHexString()
        val current = sessions[key]
        if (current == null) {
            if (sessions.size >= maxSessions) return NexusReplayDecision.CAPACITY_REACHED
            if (frame.sequence != 0L) return NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER
            sessions[key] = SessionReplayState(
                frame.expiresAtMillis, frame.sequence, mutableSetOf(nonceHash)
            )
            return NexusReplayDecision.ACCEPTED
        }

        if (frame.expiresAtMillis != current.expiresAtMillis ||
            frame.sequence != current.lastSequence + 1L ||
            nonceHash in current.seenNonceHashes ||
            current.seenNonceHashes.size >= MAX_FRAMES_PER_SESSION
        ) return NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER

        current.lastSequence = frame.sequence
        current.seenNonceHashes.add(nonceHash)
        return NexusReplayDecision.ACCEPTED
    }

    companion object {
        const val DEFAULT_MAX_SESSIONS = 1_024
        const val HARD_MAX_SESSIONS = 4_096
        const val MAX_FRAMES_PER_SESSION = 256
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

object NexusAuthenticatedHandshake {
    fun verifyAndAccept(
        frame: NexusHandshakeFrame,
        transcript: PairingTranscript,
        expectedSenderRole: PairingSignerRole,
        peerPublicKey: PublicKey,
        nowMillis: Long,
        replayGuard: NexusHandshakeReplayGuard
    ): NexusReplayDecision {
        return when (
            NexusHandshakeFrameVerifier.verify(
                frame, transcript, expectedSenderRole, peerPublicKey, nowMillis
            )
        ) {
            NexusHandshakeVerificationResult.EXPIRED -> NexusReplayDecision.EXPIRED
            NexusHandshakeVerificationResult.INVALID -> NexusReplayDecision.INVALID_SIGNATURE_OR_TRANSCRIPT
            NexusHandshakeVerificationResult.VERIFIED -> replayGuard.accept(frame, nowMillis)
        }
    }
}
