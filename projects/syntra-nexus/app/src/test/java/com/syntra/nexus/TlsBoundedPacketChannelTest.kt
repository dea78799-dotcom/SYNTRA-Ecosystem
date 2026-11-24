package com.syntra.nexus

import java.io.DataInputStream
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsBoundedPacketChannelTest {
    @Test
    fun readsFragmentedFrameAndPreservesPayload() {
        val requestId = "AbCdEf0123456789_-XYZa"
        val expected = NexusTransportPacket.create(
            NexusMessageType.PAIRING_CHALLENGE, requestId, byteArrayOf(1, 2, 3, 4, 5)
        )
        val frame = NexusTransportCodec.encode(expected)
        val fixture = openFixture { peer ->
            peer.outputStream.write(frame, 0, 7)
            peer.outputStream.flush()
            peer.outputStream.write(frame, 7, frame.size - 7)
            peer.outputStream.flush()
            fixtureHold.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        }
        try {
            val actual = fixture.channel.readPacket(2_000)
            assertNotNull(actual)
            assertEquals(expected.type, actual!!.type)
            assertEquals(expected.requestId, actual.requestId)
            assertArrayEquals(expected.payload, actual.payload)
        } finally {
            fixture.release.countDown()
            fixture.close()
        }
    }

    @Test
    fun idleReadTimeoutDoesNotConsumeFrameOrCloseChannel() {
        val fixture = openFixture { fixtureHold.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS) }
        try {
            assertThrows(SocketTimeoutException::class.java) {
                fixture.channel.readPacket(150)
            }
            assertEquals(TlsSocketLifecycleState.ACTIVE, fixture.manager.state)
            assertTrue(fixture.client.isConnected && !fixture.client.isClosed)
        } finally {
            fixture.release.countDown()
            fixture.close()
        }
    }

    @Test
    fun partialHeaderTimeoutClosesChannelToPreventStreamDesynchronization() {
        val fixture = openFixture { peer ->
            peer.outputStream.write(byteArrayOf(0x53, 0x4e, 0x58, 0x31, 0x01, 0x06))
            peer.outputStream.flush()
            fixtureHold.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        }
        try {
            assertThrows(SocketTimeoutException::class.java) {
                fixture.channel.readPacket(150)
            }
            assertEquals(TlsSocketLifecycleState.CLOSED, fixture.manager.state)
            assertTrue(fixture.client.isClosed)
        } finally {
            fixture.release.countDown()
            fixture.close()
        }
    }

    @Test
    fun oversizedLengthIsRejectedBeforePayloadAllocationAndClosesChannel() {
        val fixture = openFixture { peer ->
            val header = ByteBuffer.allocate(NexusTransportCodec.HEADER_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .put(byteArrayOf(0x53, 0x4e, 0x58, 0x31))
                .put(NexusTransportCodec.PROTOCOL_VERSION.toByte())
                .put(NexusMessageType.PING.wireCode.toByte())
                .put("AbCdEf0123456789_-XYZa".toByteArray(Charsets.US_ASCII))
                .putInt(NexusTransportCodec.MAX_PAYLOAD_BYTES + 1)
                .array()
            peer.outputStream.write(header)
            peer.outputStream.flush()
            fixtureHold.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                fixture.channel.readPacket(2_000)
            }
            assertEquals(TlsSocketLifecycleState.CLOSED, fixture.manager.state)
            assertTrue(fixture.client.isClosed)
        } finally {
            fixture.release.countDown()
            fixture.close()
        }
    }

    @Test
    fun writePacketSendsOneCompleteEncodedFrame() {
        val expected = NexusTransportPacket.create(
            NexusMessageType.PING, "AbCdEf0123456789_-XYZa", byteArrayOf(8, 9, 10)
        )
        val fixture = openFixture { peer ->
            val input = DataInputStream(peer.inputStream)
            val header = ByteArray(NexusTransportCodec.HEADER_BYTES)
            input.readFully(header)
            val payloadLength = ByteBuffer.wrap(header)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt(NexusTransportCodec.HEADER_BYTES - Int.SIZE_BYTES)
            val frame = header + ByteArray(payloadLength).also(input::readFully)
            assertEquals(expected.type, NexusTransportCodec.decode(frame).type)
            assertArrayEquals(expected.payload, NexusTransportCodec.decode(frame).payload)
            fixtureHold.await(TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        }
        try {
            fixture.channel.writePacket(expected)
            assertEquals(TlsSocketLifecycleState.ACTIVE, fixture.manager.state)
        } finally {
            fixture.release.countDown()
            fixture.close()
        }
    }

    private fun openFixture(serverAction: (SSLSocket) -> Unit): Fixture {
        val serverContext = context("device_a", "device_b")
        val clientContext = context("device_b", "device_a")
        val registry = registryFor("device_a")
        val server = serverContext.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getLoopbackAddress()
        ) as SSLServerSocket
        NexusTlsProtocolPolicy.configureServerSocket(server)
        server.soTimeout = TIMEOUT_MILLIS

        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val serverFuture = executor.submit {
            server.accept().use { raw ->
                val peer = raw as SSLSocket
                peer.soTimeout = TIMEOUT_MILLIS
                peer.startHandshake()
                serverAction(peer)
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
        val manager = TlsSocketLifecycleManager.capture(
            client, requireNotNull(binding), registry
        )
        assertNotNull(manager)
        return Fixture(server, client, executor, serverFuture, release, registry, requireNotNull(manager))
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
            displayName = "Bounded channel test",
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
        val serverFuture: java.util.concurrent.Future<*>,
        val release: CountDownLatch,
        val registry: TrustedDeviceRegistry,
        val manager: TlsSocketLifecycleManager
    ) {
        val channel = TlsBoundedPacketChannel(manager)

        fun close() {
            channel.close()
            runCatching { server.close() }
            executor.shutdownNow()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
        const val TEST_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        const val TEST_TRANSCRIPT_HASH = "a".repeat(64)
        val fixtureHold = CountDownLatch(1)
    }
}
