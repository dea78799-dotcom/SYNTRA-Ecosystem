package com.syntra.nexus

import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Loopback tests for serialized I/O and terminal socket lifecycle states. */
class TlsSocketLifecycleManagerTest {
    @Test
    fun serializesIoAndAllowsOnlyTheCapturedAuthenticatedSocket() {
        val fixture = openFixture()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val first = fixture.executor.submit {
            fixture.manager.withAuthorizedIo { socket ->
                firstEntered.countDown()
                check(releaseFirst.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
                socket.outputStream.write(0x31)
                socket.outputStream.flush()
            }
        }

        try {
            assertTrue(firstEntered.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
            val second = fixture.executor.submit {
                fixture.manager.withAuthorizedIo { socket ->
                    secondEntered.countDown()
                    socket.outputStream.write(0x32)
                    socket.outputStream.flush()
                }
            }
            assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            second.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            assertTrue(secondEntered.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
            assertTrue(fixture.serverReceivedTwoBytes.get(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
            assertEquals(TlsSocketLifecycleState.ACTIVE, fixture.manager.state)
        } finally {
            releaseFirst.countDown()
            fixture.close()
        }
    }

    @Test
    fun revocationClosesSocketAndPermanentlyBlocksFurtherIo() {
        val fixture = openFixture()
        try {
            assertTrue(fixture.manager.refreshAuthorization())
            assertTrue(fixture.registry.revoke(fingerprint("device_a"), System.currentTimeMillis() + 1L))
            assertFalse(fixture.manager.refreshAuthorization())
            assertEquals(TlsSocketLifecycleState.REVOKED, fixture.manager.state)
            assertTrue(fixture.client.isClosed)
            assertThrows(SecurityException::class.java) {
                fixture.manager.withAuthorizedIo { it.outputStream.write(1) }
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun explicitCloseIsImmediateAndPreventsNewOperations() {
        val fixture = openFixture()
        try {
            fixture.manager.close()
            assertEquals(TlsSocketLifecycleState.CLOSED, fixture.manager.state)
            assertTrue(fixture.client.isClosed)
            assertThrows(IllegalStateException::class.java) {
                fixture.manager.withAuthorizedIo { it.outputStream.write(1) }
            }
        } finally {
            fixture.close()
        }
    }

    private fun openFixture(): Fixture {
        val serverContext = context("device_a", "device_b")
        val clientContext = context("device_b", "device_a")
        val registry = registryFor("device_a")
        val server = serverContext.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getLoopbackAddress()
        ) as SSLServerSocket
        NexusTlsProtocolPolicy.configureServerSocket(server)
        server.soTimeout = TIMEOUT_MILLIS

        val executor = Executors.newFixedThreadPool(3)
        val received = executor.submit<Boolean> {
            try {
                (server.accept() as SSLSocket).use { peer ->
                    peer.soTimeout = TIMEOUT_MILLIS
                    peer.startHandshake()
                    peer.inputStream.read() >= 0 && peer.inputStream.read() >= 0
                }
            } catch (_: Exception) {
                false
            }
        }

        val client = clientContext.socketFactory.createSocket(
            InetAddress.getLoopbackAddress(), server.localPort
        ) as SSLSocket
        NexusTlsProtocolPolicy.configureClientSocket(client)
        client.soTimeout = TIMEOUT_MILLIS
        client.startHandshake()

        val authorization = TlsSessionAuthorization.capture(
            tlsSession = client.session,
            expectedLocalFingerprint = fingerprint("device_b"),
            expectedPeerFingerprint = fingerprint("device_a"),
            expectedPairingRequestId = TEST_REQUEST_ID,
            expectedTranscriptHashHex = TEST_TRANSCRIPT_HASH,
            trustedDevices = registry,
            nowMillis = System.currentTimeMillis()
        )
        assertNotNull(authorization)
        val binding = TlsSocketChannelBinding.capture(
            client, requireNotNull(authorization), registry, System.currentTimeMillis()
        )
        assertNotNull(binding)
        val manager = TlsSocketLifecycleManager.capture(client, requireNotNull(binding), registry)
        assertNotNull(manager)
        return Fixture(server, client, executor, received, registry, requireNotNull(manager))
    }

    private fun context(identity: String, trustedPeer: String): SSLContext {
        val peerKey = NexusMutualTlsTestFixtures.certificate(trustedPeer).publicKey.encoded
        val trustRegistry = registryFor(trustedPeer)
        return SSLContext.getInstance("TLS").apply {
            init(
                NexusMutualTlsTestFixtures.keyManagers(identity),
                arrayOf(
                    PinnedPeerTrustManager(
                        trustRegistry,
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
        val key = NexusMutualTlsTestFixtures.certificate(name).publicKey.encoded
        val record = TrustedDeviceRecord(
            fingerprint = PairingIdentityCrypto.fingerprint(key),
            publicKeyEncoded = key,
            displayName = "Lifecycle test",
            trustedAtMillis = 0L,
            pairingRequestId = TEST_REQUEST_ID,
            transcriptHashHex = TEST_TRANSCRIPT_HASH
        )
        return TrustedDeviceRegistry(object : TrustedDevicePersistence {
            override fun load(): List<TrustedDeviceRecord> = listOf(record)
            override fun save(records: List<TrustedDeviceRecord>) = Unit
        })
    }

    private fun fingerprint(name: String): String =
        PairingIdentityCrypto.fingerprint(
            NexusMutualTlsTestFixtures.certificate(name).publicKey.encoded
        )

    private data class Fixture(
        val server: SSLServerSocket,
        val client: SSLSocket,
        val executor: java.util.concurrent.ExecutorService,
        val serverReceivedTwoBytes: java.util.concurrent.Future<Boolean>,
        val registry: TrustedDeviceRegistry,
        val manager: TlsSocketLifecycleManager
    ) {
        fun close() {
            manager.close()
            runCatching { server.close() }
            executor.shutdownNow()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000
        const val TEST_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        val TEST_TRANSCRIPT_HASH = "a".repeat(64)
    }
}
