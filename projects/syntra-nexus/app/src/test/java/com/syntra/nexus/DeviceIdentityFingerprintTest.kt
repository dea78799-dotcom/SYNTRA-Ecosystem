package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdentityFingerprintTest {
    @Test
    fun formatsSha256FingerprintIntoReadableGroups() {
        val fingerprint = "0123456789abcdef".repeat(4)
        assertEquals(
            "0123 4567 89AB CDEF 0123 4567 89AB CDEF 0123 4567 89AB CDEF 0123 4567 89AB CDEF",
            PairingIdentityCrypto.formatFingerprint(fingerprint)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortFingerprint() {
        PairingIdentityCrypto.formatFingerprint("abcd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHexFingerprint() {
        PairingIdentityCrypto.formatFingerprint("z".repeat(64))
    }
}
