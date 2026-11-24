package com.syntra.nexus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NexusTlsProtocolPolicyTest {

    @Test
    fun prefersTls13ThenTls12() {
        assertArrayEquals(
            arrayOf("TLSv1.3", "TLSv1.2"),
            NexusTlsProtocolPolicy.enabledProtocols(
                arrayOf("TLSv1", "TLSv1.2", "TLSv1.3")
            )
        )
    }

    @Test
    fun fallsBackToTls12OnOlderSupportedAndroidVersions() {
        assertArrayEquals(
            arrayOf("TLSv1.2"),
            NexusTlsProtocolPolicy.enabledProtocols(arrayOf("TLSv1.1", "TLSv1.2"))
        )
    }

    @Test
    fun rejectsDevicesWithoutTls12OrTls13() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusTlsProtocolPolicy.enabledProtocols(arrayOf("TLSv1", "TLSv1.1"))
        }
    }

    @Test
    fun doesNotEnableDeprecatedTlsProtocols() {
        val enabled = NexusTlsProtocolPolicy.enabledProtocols(
            arrayOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
        )
        assertEquals(listOf("TLSv1.3", "TLSv1.2"), enabled.toList())
    }
}
