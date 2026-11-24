package com.syntra.nexus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusTransportProtocolTest {
    private val requestId = "AbCdEf0123456789_-XYZa"

    @Test
    fun roundTripPreservesTypeRequestIdAndPayload() {
        val payload = byteArrayOf(0, 1, 2, 3, -1)
        val packet = NexusTransportPacket.create(NexusMessageType.PAIRING_CHALLENGE, requestId, payload)

        val decoded = NexusTransportCodec.decode(NexusTransportCodec.encode(packet))

        assertEquals(NexusMessageType.PAIRING_CHALLENGE, decoded.type)
        assertEquals(requestId, decoded.requestId)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun packetCopiesPayloadAtBoundaries() {
        val source = byteArrayOf(1, 2, 3)
        val packet = NexusTransportPacket.create(NexusMessageType.PING, requestId, source)
        source[0] = 9
        val exposed = packet.payload
        exposed[1] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), packet.payload)
    }

    @Test
    fun acceptsEmptyPayload() {
        val packet = NexusTransportPacket.create(NexusMessageType.PONG, requestId)
        val decoded = NexusTransportCodec.decode(NexusTransportCodec.encode(packet))

        assertTrue(decoded.payload.isEmpty())
    }

    @Test
    fun rejectsInvalidRequestId() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportPacket.create(NexusMessageType.HELLO, "short")
        }
    }

    @Test
    fun rejectsPayloadAboveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportPacket.create(
                NexusMessageType.HELLO,
                requestId,
                ByteArray(NexusTransportCodec.MAX_PAYLOAD_BYTES + 1)
            )
        }
    }

    @Test
    fun rejectsTruncatedPacket() {
        val encoded = NexusTransportCodec.encode(
            NexusTransportPacket.create(NexusMessageType.HELLO, requestId, byteArrayOf(1, 2))
        )
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportCodec.decode(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun rejectsTrailingBytes() {
        val encoded = NexusTransportCodec.encode(NexusTransportPacket.create(NexusMessageType.PING, requestId))
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportCodec.decode(encoded + byteArrayOf(0))
        }
    }

    @Test
    fun rejectsUnknownProtocolVersion() {
        val encoded = NexusTransportCodec.encode(NexusTransportPacket.create(NexusMessageType.PING, requestId))
        encoded[4] = 99
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsUnknownMessageType() {
        val encoded = NexusTransportCodec.encode(NexusTransportPacket.create(NexusMessageType.PING, requestId))
        encoded[5] = 99
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsInvalidMagic() {
        val encoded = NexusTransportCodec.encode(NexusTransportPacket.create(NexusMessageType.PING, requestId))
        encoded[0] = 0
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsPacketAboveMaximumSize() {
        val oversized = ByteArray(NexusTransportCodec.MAX_PACKET_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) {
            NexusTransportCodec.decode(oversized)
        }
    }

    @Test
    fun encodedHeaderContainsExpectedProtocolMarker() {
        val encoded = NexusTransportCodec.encode(NexusTransportPacket.create(NexusMessageType.HELLO, requestId))
        assertArrayEquals(byteArrayOf(0x53, 0x4e, 0x58, 0x31), encoded.copyOfRange(0, 4))
        assertEquals(NexusTransportCodec.PROTOCOL_VERSION.toByte(), encoded[4])
    }
}
