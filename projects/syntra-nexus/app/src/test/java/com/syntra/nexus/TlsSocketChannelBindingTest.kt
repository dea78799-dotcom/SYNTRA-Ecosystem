package com.syntra.nexus

import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real loopback JSSE coverage for socket/session channel binding. */
class TlsSocketChannelBindingTest {
    @Test
    fun bindingAuthorizesOnlyCapturedOpenSocketAndFailsAfterClose() {
        val serverContext = context("device_a", listOf("device_b"))
        val clientContext = context("device_b", listOf("device_a"))
        val registry = registryFor("device_a")
        val server = serverContext.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getLoopbackAddress()
        ) as SSLServerSocket
        NexusTlsProtocolPolicy.configureServerSocket(server)
        server.soTimeout = TIMEOUT_MILLIS

        val executor = Executors.newSingleThreadExecutor()
        val accepted = executor.submit<Boolean> {
            try {
                (server.accept() as SSLSocket).use { peer ->
                    peer.startHandshake()
                    peer.inputStream.read()
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        try {
            val client = clientContext.socketFactory.createSocket(
                InetAddress.getLoopbackAddress(), server.localPort
            ) as SSLSocket
            NexusTlsProtocolPolicy.configureClientSocket(client)
            client.soTimeout = TIMEOUT_MILLIS
            client.startHandshake()

            val local = readFingerprint("device_b")
            val peer = readFingerprint("device_a")
            val session = client.session
            val authorization = TlsSessionAuthorization.capture(
                tlsSession = session,
                expectedLocalFingerprint = local,
                expectedPeerFingerprint = peer,
                expectedPairingRequestId = TEST_REQUEST_ID,
                expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
                trustedDevices = registry,
                nowMillis = System.currentTimeMillis()
            )
            assertNotNull(authorization)

            val binding = TlsSocketChannelBinding.capture(
                socket = client,
                authorization = requireNotNull(authorization),
                trustedDevices = registry,
                nowMillis = System.currentTimeMillis()
            )
            assertNotNull(binding)
            assertTrue(
                requireNotNull(binding).isAuthorized(
                    client, registry, System.currentTimeMillis()
                )
            )

            val unrelated = clientContext.socketFactory.createSocket() as SSLSocket
            assertFalse(
                requireNotNull(binding).isAuthorized(
                    unrelated, registry, System.currentTimeMillis()
                )
            )
            unrelated.close()

            client.outputStream.write(0x5A)
            client.outputStream.flush()
            assertTrue(accepted.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))

            val peerFingerprint = readFingerprint("device_a")
            assertTrue(registry.revoke(peerFingerprint, System.currentTimeMillis() + 1L))
            assertFalse(
                requireNotNull(binding).isAuthorized(
                    client, registry, System.currentTimeMillis() + 2L
                )
            )

            client.close()
            assertFalse(
                requireNotNull(binding).isAuthorized(
                    client, registry, System.currentTimeMillis()
                )
            )
        } finally {
            runCatching { server.close() }
            executor.shutdownNow()
        }
    }

    private fun context(identity: String, trusts: List<String>): SSLContext {
        // Reuse the same test-only JSSE fixtures and pinned trust manager as
        // the mutual-TLS integration suite.
        val certificate = NexusMutualTlsTestFixtures.certificate(identity)
        val keyManagers = NexusMutualTlsTestFixtures.keyManagers(identity)
        val registry = registryFor(trusts.first())
        val peerKey = NexusMutualTlsTestFixtures.certificate(trusts.first()).publicKey.encoded
        return SSLContext.getInstance("TLS").apply {
            init(
                keyManagers,
                arrayOf(
                    PinnedPeerTrustManager(
                        registry,
                        PairingIdentityCrypto.fingerprint(peerKey),
                        TEST_REQUEST_ID,
                        TEST_TRANSCRIPT_HASH
                    )
                ),
                SecureRandom()
            )
        }
    }

    private fun registryFor(name: String): TrustedDeviceRegistry {
        val cert = NexusMutualTlsTestFixtures.certificate(name)
        val key = cert.publicKey.encoded
        val record = TrustedDeviceRecord(
            fingerprint = PairingIdentityCrypto.fingerprint(key),
            publicKeyEncoded = key,
            displayName = "Channel binding test",
            trustedAtMillis = 0L,
            pairingRequestId = TEST_REQUEST_ID,
            transcriptHashHex = TEST_TRANSCRIPT_HASH
        )
        return TrustedDeviceRegistry(object : TrustedDevicePersistence {
            override fun load(): List<TrustedDeviceRecord> = listOf(record)
            override fun save(records: List<TrustedDeviceRecord>) = Unit
        })
    }

    private fun readFingerprint(name: String): String =
        PairingIdentityCrypto.fingerprint(
            NexusMutualTlsTestFixtures.certificate(name).publicKey.encoded
        )

    private companion object {
        const val TIMEOUT_MILLIS = 8_000
        const val TEST_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        const val TEST_TRANSCRIPT_HASH = "a".repeat(64)
    }
}
