package com.syntra.nexus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Bounded, versioned wire envelope for a peer-signed capability request.
 *
 * Decoding is deliberately separate from trust decisions: callers MUST verify
 * the signature and bind the request to the authenticated peer, transcript,
 * session and current trusted-device record before presenting consent UI.
 */
class CapabilityRequestEnvelope(
    val request: CapabilityGrantRequest,
    peerApprovalSignature: ByteArray
) {
    private val signature = peerApprovalSignature.copyOf()

    fun peerApprovalSignatureCopy(): ByteArray = signature.copyOf()

    init {
        require(signature.size in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES) {
            "Capability approval signature length is invalid."
        }
    }

    fun encode(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeByte(VERSION)
            data.writeText(request.grantId)
            data.writeText(request.peerFingerprint)
            data.writeText(request.pairingRequestId)
            data.writeText(request.transcriptHashHex)
            data.writeText(request.sessionId)
            data.writeText(request.capability.wireName)
            data.writeLong(request.issuedAtMillis)
            data.writeLong(request.expiresAtMillis)
            data.writeShort(signature.size)
            data.write(signature)
        }
        return output.toByteArray().also {
            require(it.size <= MAX_ENVELOPE_BYTES) { "Capability request envelope is too large." }
        }
    }

    companion object {
        private val MAGIC = byteArrayOf(0x53, 0x4e, 0x43, 0x51) // SNCQ
        private const val VERSION = 1
        const val MAX_ENVELOPE_BYTES = 1024
        private const val MAX_TEXT_BYTES = 128
        private const val MIN_SIGNATURE_BYTES = 8
        private const val MAX_SIGNATURE_BYTES = 128

        fun decode(bytes: ByteArray): CapabilityRequestEnvelope {
            require(bytes.size in 32..MAX_ENVELOPE_BYTES) { "Capability envelope size is invalid." }
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Capability envelope marker is invalid." }
            require(input.readUnsignedByte() == VERSION) { "Capability envelope version is unsupported." }

            val grantId = input.readText()
            val fingerprint = input.readText()
            val requestId = input.readText()
            val transcriptHash = input.readText()
            val sessionId = input.readText()
            val capabilityName = input.readText()
            val capability = NexusCapability.entries.firstOrNull { it.wireName == capabilityName }
                ?: throw IllegalArgumentException("Capability value is unknown.")
            val issuedAt = input.readLong()
            val expiresAt = input.readLong()
            val signatureLength = input.readUnsignedShort()
            require(signatureLength in MIN_SIGNATURE_BYTES..MAX_SIGNATURE_BYTES) {
                "Capability signature length is invalid."
            }
            require(input.available() == signatureLength) { "Capability envelope has invalid trailing or missing data." }
            val signature = ByteArray(signatureLength)
            input.readFully(signature)
            require(input.available() == 0) { "Capability envelope contains trailing data." }

            val request = CapabilityGrantRequest(
                grantId = grantId,
                peerFingerprint = fingerprint,
                pairingRequestId = requestId,
                transcriptHashHex = transcriptHash,
                sessionId = sessionId,
                capability = capability,
                issuedAtMillis = issuedAt,
                expiresAtMillis = expiresAt
            )
            return CapabilityRequestEnvelope(request, signature)
        }

        private fun DataOutputStream.writeText(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size in 1..MAX_TEXT_BYTES) { "Capability field length is invalid." }
            writeShort(bytes.size)
            write(bytes)
        }

        private fun DataInputStream.readText(): String {
            val length = readUnsignedShort()
            require(length in 1..MAX_TEXT_BYTES) { "Capability field length is invalid." }
            val bytes = ByteArray(length)
            readFully(bytes)
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }
    }
}
