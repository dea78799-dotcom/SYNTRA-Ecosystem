package com.syntra.nexus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CapabilityRequestEnvelopeTest {
    private fun request() = CapabilityGrantRequest(
        grantId = "grant_identifier_123456",
        peerFingerprint = "a".repeat(64),
        pairingRequestId = "pairing_request_123456",
        transcriptHashHex = "b".repeat(64),
        sessionId = "session_identifier_123456",
        capability = NexusCapability.USAGE_SUMMARY_READ,
        issuedAtMillis = 1_000L,
        expiresAtMillis = 31_000L
    )

    @Test
    fun roundTripPreservesRequestAndSignature() {
        val signature = ByteArray(70) { it.toByte() }
        val decoded = CapabilityRequestEnvelope.decode(
            CapabilityRequestEnvelope(request(), signature).encode()
        )
        assertEquals(request(), decoded.request)
        assertArrayEquals(signature, decoded.peerApprovalSignatureCopy())
    }

    @Test
    fun signatureGetterReturnsDefensiveCopy() {
        val signature = ByteArray(64) { it.toByte() }
        val envelope = CapabilityRequestEnvelope(request(), signature)
        val exposed = envelope.peerApprovalSignatureCopy()
        exposed[0] = 99
        assertEquals(0, envelope.peerApprovalSignatureCopy()[0].toInt())
    }

    @Test
    fun rejectsWrongMarker() {
        val bytes = CapabilityRequestEnvelope(request(), ByteArray(64)).encode()
        bytes[0] = 0
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRequestEnvelope.decode(bytes)
        }
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val bytes = CapabilityRequestEnvelope(request(), ByteArray(64)).encode()
        bytes[4] = 2
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRequestEnvelope.decode(bytes)
        }
    }

    @Test
    fun rejectsTruncationAndTrailingBytes() {
        val bytes = CapabilityRequestEnvelope(request(), ByteArray(64)).encode()
        assertThrows(Exception::class.java) {
            CapabilityRequestEnvelope.decode(bytes.copyOf(bytes.size - 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRequestEnvelope.decode(bytes + byteArrayOf(1))
        }
    }

    @Test
    fun rejectsUnknownCapability() {
        val bytes = CapabilityRequestEnvelope(request(), ByteArray(64)).encode()
        // capability is the sixth length-prefixed field; replace its ASCII bytes
        val marker = "usage_summary_read".toByteArray(Charsets.UTF_8)
        val index = bytes.indices.first { start ->
            start + marker.size <= bytes.size &&
                bytes.copyOfRange(start, start + marker.size).contentEquals(marker)
        }
        bytes[index] = 'x'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRequestEnvelope.decode(bytes)
        }
    }

    @Test
    fun rejectsOversizedEnvelope() {
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRequestEnvelope.decode(ByteArray(1025))
        }
    }
}
