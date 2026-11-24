package com.syntra.nexus

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Bounded, versioned message envelope for a future NEXUS LAN transport.
 *
 * This codec only parses bytes. It does not authenticate a peer, establish TLS,
 * redeem invitations, approve pairing, or execute commands. Callers must treat
 * decoded packets as untrusted until the authenticated handshake is complete.
 */
enum class NexusMessageType(val wireCode: Int) {
    HELLO(1),
    PAIRING_CHALLENGE(2),
    PAIRING_PROOF(3),
    PAIRING_APPROVAL(4),
    ERROR(5),
    PING(6),
    PONG(7);

    companion object {
        internal fun fromWireCode(code: Int): NexusMessageType? =
            values().firstOrNull { it.wireCode == code }
    }
}

class NexusTransportPacket private constructor(
    val type: NexusMessageType,
    val requestId: String,
    payload: ByteArray
) {
    private val payloadBytes = payload.copyOf()

    val payload: ByteArray
        get() = payloadBytes.copyOf()

    internal fun payloadCopy(): ByteArray = payloadBytes.copyOf()

    companion object {
        fun create(
            type: NexusMessageType,
            requestId: String,
            payload: ByteArray = byteArrayOf()
        ): NexusTransportPacket {
            require(requestId.matches(REQUEST_ID_PATTERN)) {
                "Request ID must contain exactly 22 URL-safe characters."
            }
            require(payload.size <= NexusTransportCodec.MAX_PAYLOAD_BYTES) {
                "Transport payload exceeds the configured limit."
            }
            return NexusTransportPacket(type, requestId, payload)
        }

        private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
    }
}

/**
 * Wire format (big-endian):
 * magic[4] | version[1] | type[1] | requestId[22] | payloadLength[4] | payload[N]
 *
 * The fixed-size header allows length checks before payload allocation.
 */
object NexusTransportCodec {
    const val PROTOCOL_VERSION = 1
    const val REQUEST_ID_BYTES = 22
    const val MAX_PAYLOAD_BYTES = 64 * 1024
    const val HEADER_BYTES = 4 + 1 + 1 + REQUEST_ID_BYTES + 4
    const val MAX_PACKET_BYTES = HEADER_BYTES + MAX_PAYLOAD_BYTES

    private val MAGIC = byteArrayOf(0x53, 0x4e, 0x58, 0x31) // "SNX1"
    private val REQUEST_ID_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")

    fun encode(packet: NexusTransportPacket): ByteArray {
        val requestBytes = packet.requestId.toByteArray(StandardCharsets.US_ASCII)
        val payload = packet.payloadCopy()
        require(requestBytes.size == REQUEST_ID_BYTES && packet.requestId.matches(REQUEST_ID_PATTERN)) {
            "Invalid request ID."
        }
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Payload exceeds the configured limit." }

        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(PROTOCOL_VERSION.toByte())
            .put(packet.type.wireCode.toByte())
            .put(requestBytes)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    /**
     * Decodes exactly one packet. Truncated packets, trailing bytes, unknown
     * versions/types, invalid identifiers and oversized lengths are rejected.
     */
    fun decode(encoded: ByteArray): NexusTransportPacket {
        require(encoded.size in HEADER_BYTES..MAX_PACKET_BYTES) {
            "Packet length is outside the permitted range."
        }

        val input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size)
        input.get(magic)
        require(magic.contentEquals(MAGIC)) { "Invalid NEXUS packet marker." }

        val version = input.get().toInt() and 0xff
        require(version == PROTOCOL_VERSION) { "Unsupported NEXUS protocol version." }

        val typeCode = input.get().toInt() and 0xff
        val type = NexusMessageType.fromWireCode(typeCode)
            ?: throw IllegalArgumentException("Unknown NEXUS message type.")

        val requestBytes = ByteArray(REQUEST_ID_BYTES)
        input.get(requestBytes)
        val requestId = String(requestBytes, StandardCharsets.US_ASCII)
        require(requestId.matches(REQUEST_ID_PATTERN)) { "Invalid request ID." }

        val payloadLength = input.int
        require(payloadLength in 0..MAX_PAYLOAD_BYTES) { "Invalid payload length." }
        require(input.remaining() == payloadLength) {
            "Packet is truncated or contains trailing bytes."
        }

        val payload = ByteArray(payloadLength)
        input.get(payload)
        return NexusTransportPacket.create(type, requestId, payload)
    }
}
