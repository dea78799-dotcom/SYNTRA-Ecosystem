package com.syntra.nexus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingTransactionSnapshotCodecTest {
    private fun snapshot() = PairingTransactionSnapshot(
        requestId = "abcdefghijklmnopqrstuv",
        expiresAtMillis = 120_000L,
        transcriptHashHex = "a".repeat(64),
        peerFingerprint = "b".repeat(64),
        state = PairingTransactionState.LOCAL_APPROVAL_PENDING,
        updatedAtMillis = 60_000L
    )

    @Test fun roundTripPreservesAllTransactionFields() {
        val original = snapshot()
        val encoded = PairingTransactionSnapshotCodec.encode(original)
        assertEquals(172, encoded.size)
        assertEquals(original, PairingTransactionSnapshotCodec.decode(encoded))
    }

    @Test fun encodingIsDeterministicForSameSnapshot() {
        val first = PairingTransactionSnapshotCodec.encode(snapshot())
        val second = PairingTransactionSnapshotCodec.encode(snapshot())
        assertArrayEquals(first, second)
    }

    @Test fun rejectsTruncatedAndTrailingBytes() {
        val encoded = PairingTransactionSnapshotCodec.encode(snapshot())
        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionSnapshotCodec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionSnapshotCodec.decode(encoded + byteArrayOf(0))
        }
    }

    @Test fun rejectsUnknownMarkerAndVersion() {
        val encoded = PairingTransactionSnapshotCodec.encode(snapshot())
        val badMarker = encoded.copyOf().also { it[0] = 0 }
        val badVersion = encoded.copyOf().also { it[4] = 99 }
        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionSnapshotCodec.decode(badMarker)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionSnapshotCodec.decode(badVersion)
        }
    }

    @Test fun rejectsUnknownState() {
        val encoded = PairingTransactionSnapshotCodec.encode(snapshot())
        encoded[4 + 1 + 22 + 8 + 64 + 64] = 0x7f
        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionSnapshotCodec.decode(encoded)
        }
    }

    @Test fun rejectsInvalidFieldsBeforeEncoding() {
        val invalid = snapshot().copy(peerFingerprint = "not-a-fingerprint")
        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionSnapshotCodec.encode(invalid)
        }
    }
}
