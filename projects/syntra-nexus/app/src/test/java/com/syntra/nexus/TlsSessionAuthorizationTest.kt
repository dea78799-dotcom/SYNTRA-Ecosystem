package com.syntra.nexus

import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsSessionAuthorizationTest {
    @Test
    fun capturesSessionOnlyWhenBothIdentitiesAndPairingProvenanceMatch() {
        val fixtures = fixtures()
        val registry = registryFor(fixtures.peer)
        val session = fakeSession(fixtures.local, fixtures.peer)
        val authorization = TlsSessionAuthorization.capture(
            tlsSession = session,
            expectedLocalFingerprint = fingerprint(fixtures.local),
            expectedPeerFingerprint = fingerprint(fixtures.peer),
            expectedPairingRequestId = TEST_REQUEST_ID,
            expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
            trustedDevices = registry,
            nowMillis = fixtures.validAtMillis
        )

        assertNotNull(authorization)
        assertTrue(requireNotNull(authorization).isAuthorized(registry, fixtures.validAtMillis))
    }

    @Test
    fun revocationImmediatelyInvalidatesPreviouslyCapturedSessionAuthorization() {
        val fixtures = fixtures()
        val registry = registryFor(fixtures.peer)
        val authorization = requireNotNull(
            TlsSessionAuthorization.capture(
                tlsSession = fakeSession(fixtures.local, fixtures.peer),
                expectedLocalFingerprint = fingerprint(fixtures.local),
                expectedPeerFingerprint = fingerprint(fixtures.peer),
                expectedPairingRequestId = TEST_REQUEST_ID,
                expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
                trustedDevices = registry,
                nowMillis = fixtures.validAtMillis
            )
        )

        assertTrue(registry.revoke(fingerprint(fixtures.peer), fixtures.validAtMillis + 1L))
        assertFalse(authorization.isAuthorized(registry, fixtures.validAtMillis + 1L))
    }

    @Test
    fun changedPairingProvenanceInvalidatesCapturedAuthorization() {
        val fixtures = fixtures()
        val originalRegistry = registryFor(fixtures.peer)
        val authorization = requireNotNull(
            TlsSessionAuthorization.capture(
                tlsSession = fakeSession(fixtures.local, fixtures.peer),
                expectedLocalFingerprint = fingerprint(fixtures.local),
                expectedPeerFingerprint = fingerprint(fixtures.peer),
                expectedPairingRequestId = TEST_REQUEST_ID,
                expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
                trustedDevices = originalRegistry,
                nowMillis = fixtures.validAtMillis
            )
        )

        val changedRegistry = registryFor(
            fixtures.peer,
            pairingRequestId = "FFFFFFFFFFFFFFFFFFFFFF"
        )
        assertFalse(authorization.isAuthorized(changedRegistry, fixtures.validAtMillis))
    }

    @Test
    fun rejectsWrongLocalIdentityAndUnsupportedTlsProtocol() {
        val fixtures = fixtures()
        val registry = registryFor(fixtures.peer)

        assertFalse(
            TlsSessionAuthorization.capture(
                tlsSession = fakeSession(fixtures.local, fixtures.peer),
                expectedLocalFingerprint = fingerprint(fixtures.peer),
                expectedPeerFingerprint = fingerprint(fixtures.peer),
                expectedPairingRequestId = TEST_REQUEST_ID,
                expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
                trustedDevices = registry,
                nowMillis = fixtures.validAtMillis
            ) != null
        )
        assertFalse(
            TlsSessionAuthorization.capture(
                tlsSession = fakeSession(fixtures.local, fixtures.peer, protocol = "TLSv1.1"),
                expectedLocalFingerprint = fingerprint(fixtures.local),
                expectedPeerFingerprint = fingerprint(fixtures.peer),
                expectedPairingRequestId = TEST_REQUEST_ID,
                expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
                trustedDevices = registry,
                nowMillis = fixtures.validAtMillis
            ) != null
        )
    }

    @Test
    fun invalidatedJsseSessionCannotBeCaptured() {
        val fixtures = fixtures()
        val registry = registryFor(fixtures.peer)
        assertFalse(
            TlsSessionAuthorization.capture(
                tlsSession = fakeSession(fixtures.local, fixtures.peer, valid = false),
                expectedLocalFingerprint = fingerprint(fixtures.local),
                expectedPeerFingerprint = fingerprint(fixtures.peer),
                expectedPairingRequestId = TEST_REQUEST_ID,
                expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
                trustedDevices = registry,
                nowMillis = fixtures.validAtMillis
            ) != null
        )
    }

    private data class CertificateFixtures(
        val local: X509Certificate,
        val peer: X509Certificate,
        val validAtMillis: Long
    )

    private fun fixtures(): CertificateFixtures {
        val local = readCertificate("device_a_cert.pem")
        val peer = readCertificate("device_b_cert.pem")
        val validAt = maxOf(local.notBefore.time, peer.notBefore.time) + 1_000L
        require(validAt < minOf(local.notAfter.time, peer.notAfter.time)) {
            "Test certificates do not share a validity interval."
        }
        return CertificateFixtures(local, peer, validAt)
    }

    private fun registryFor(
        peer: X509Certificate,
        pairingRequestId: String = TEST_REQUEST_ID,
        transcriptHashHex: String = TEST_TRANSCRIPT_HASH
    ): TrustedDeviceRegistry {
        val key = peer.publicKey.encoded
        val record = TrustedDeviceRecord(
            fingerprint = fingerprint(peer),
            publicKeyEncoded = key,
            displayName = "TLS authorization test peer",
            trustedAtMillis = 0L,
            pairingRequestId = pairingRequestId,
            transcriptHashHex = transcriptHashHex
        )
        return TrustedDeviceRegistry(object : TrustedDevicePersistence {
            override fun load(): List<TrustedDeviceRecord> = listOf(record)
            override fun save(records: List<TrustedDeviceRecord>) = Unit
        })
    }

    private fun fakeSession(
        local: X509Certificate,
        peer: X509Certificate,
        protocol: String = "TLSv1.3",
        valid: Boolean = true
    ): SSLSession {
        return Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "isValid" -> valid
                "getProtocol" -> protocol
                "getLocalCertificates" -> arrayOf(local)
                "getPeerCertificates" -> arrayOf(peer)
                "getId" -> byteArrayOf(0x11, 0x22, 0x33)
                "getCreationTime" -> 123_456L
                "getCipherSuite" -> "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
                "getPeerHost" -> "localhost"
                "getPeerPort" -> 443
                "getPacketBufferSize", "getApplicationBufferSize" -> 16_384
                "toString" -> "FakeSSLSession"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> null
            }
        } as SSLSession
    }

    private fun readCertificate(name: String): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(
                requireNotNull(javaClass.getResourceAsStream("/tls/$name")) {
                    "Missing TLS test certificate: $name"
                }.use { it.readBytes() }
            )
        ) as X509Certificate

    private fun fingerprint(certificate: X509Certificate): String =
        PairingIdentityCrypto.fingerprint(certificate.publicKey.encoded)

    private companion object {
        const val TEST_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        const val TEST_TRANSCRIPT_HASH =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
