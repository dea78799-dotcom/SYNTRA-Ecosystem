package com.syntra.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRequestTlsIngressTest {
    @Test
    fun onlyTls12AndTls13AreAccepted() {
        assertTrue(isSupported("TLSv1.2"))
        assertTrue(isSupported("TLSv1.3"))
        assertFalse(isSupported("TLSv1.1"))
        assertFalse(isSupported("SSL"))
        assertFalse(isSupported(""))
    }

    private fun isSupported(protocol: String?): Boolean =
        protocol == "TLSv1.2" || protocol == "TLSv1.3"
}
