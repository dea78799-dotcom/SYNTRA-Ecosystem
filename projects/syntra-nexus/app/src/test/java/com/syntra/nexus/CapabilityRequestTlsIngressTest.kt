package com.syntra.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRequestTlsIngressTest {
    @Test
    fun onlyTls12AndTls13AreAccepted() {
        assertTrue(CapabilityRequestTlsIngress.isSupportedTlsProtocol("TLSv1.2"))
        assertTrue(CapabilityRequestTlsIngress.isSupportedTlsProtocol("TLSv1.3"))
        assertFalse(CapabilityRequestTlsIngress.isSupportedTlsProtocol("TLSv1.1"))
        assertFalse(CapabilityRequestTlsIngress.isSupportedTlsProtocol("SSL"))
        assertFalse(CapabilityRequestTlsIngress.isSupportedTlsProtocol(""))
    }
}
