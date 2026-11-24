package com.syntra.nexus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredNexusPeerTest {
    @Test
    fun acceptsExpectedNexusServiceShape() {
        val peer = DiscoveredNexusPeer(
            serviceName = "SYNTRA-NEXUS-phone",
            hostAddress = "192.168.1.20",
            port = 43210,
            serviceType = "_syntranexus._tcp."
        )
        assertTrue(peer.isStructurallyValid())
        assertTrue(peer.isUntrusted)
    }

    @Test
    fun rejectsUnexpectedNameTypeOrPort() {
        val base = DiscoveredNexusPeer(
            serviceName = "SYNTRA-NEXUS-phone",
            hostAddress = "192.168.1.20",
            port = 43210,
            serviceType = "_syntranexus._tcp."
        )
        assertFalse(base.copy(serviceName = "printer").isStructurallyValid())
        assertFalse(base.copy(serviceType = "_http._tcp.").isStructurallyValid())
        assertFalse(base.copy(port = 0).isStructurallyValid())
        assertFalse(base.copy(port = 65536).isStructurallyValid())
        assertFalse(base.copy(hostAddress = "").isStructurallyValid())
    }
}
