package com.syntra.nexus

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsPacketSessionDriverTest {
    private val requestId = "AbCdEf0123456789_-XYZa"
    private val pongId = "0123456789abcdefghij_-"

    @Test
    fun sendsQueuedApplicationPacketBeforeWaitingForInbound() {
        val channel = FakeChannel()
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 10L })
        assertTrue(driver.start())
        assertTrue(driver.enqueue(packet(NexusMessageType.HELLO)))

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.IDLE_TIMEOUT, result.outcome)
        assertEquals(listOf(NexusMessageType.HELLO), channel.written.map { it.type })
        assertEquals(1, result.outboundPacketsWritten)
        assertEquals(PacketSessionState.ACTIVE, result.state)
    }

    @Test
    fun respondsToPeerPingAndPreservesCorrelationId() {
        val channel = FakeChannel().apply {
            incoming.add(packet(NexusMessageType.PING, pongId))
        }
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.PACKET_RECEIVED, result.outcome)
        assertEquals(PacketReceiveResult.PING_QUEUED, result.receiveResult)
        assertEquals(NexusMessageType.PONG, channel.written.single().type)
        assertEquals(pongId, channel.written.single().requestId)
    }

    @Test
    fun idleReadTimeoutKeepsSessionActiveWhenChannelRemainsOpen() {
        val channel = FakeChannel().apply { readFailure = SocketTimeoutException("idle") }
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.IDLE_TIMEOUT, result.outcome)
        assertEquals(PacketSessionState.ACTIVE, result.state)
        assertTrue(channel.isOpen)
    }

    @Test
    fun timeoutAfterChannelClosedDisconnectsSession() {
        val channel = FakeChannel().apply {
            readFailure = SocketTimeoutException("partial frame")
            closeOnReadFailure = true
        }
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.DISCONNECTED, result.outcome)
        assertEquals(PacketSessionState.DISCONNECTED, result.state)
        assertFalse(channel.isOpen)
    }

    @Test
    fun malformedTransportFailureClosesSession() {
        val channel = FakeChannel().apply { readFailure = IOException("broken stream") }
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()

        assertEquals(PacketPumpOutcome.DISCONNECTED, driver.pumpOnce(100).outcome)
        assertFalse(channel.isOpen)
    }

    @Test
    fun outboundWriteFailureDisconnectsSession() {
        val channel = FakeChannel().apply {
            writeFailure = java.io.IOException("write failed")
        }
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()
        assertTrue(driver.enqueue(packet(NexusMessageType.HELLO)))

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.DISCONNECTED, result.outcome)
        assertEquals(PacketSessionState.DISCONNECTED, result.state)
        assertFalse(channel.isOpen)
    }

    @Test
    fun cleanEofDisconnectsSession() {
        val channel = FakeChannel()
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.DISCONNECTED, result.outcome)
        assertEquals(PacketSessionState.DISCONNECTED, result.state)
    }

    @Test
    fun invalidPongDisconnectsAndClosesChannel() {
        val channel = FakeChannel().apply {
            incoming.add(packet(NexusMessageType.PONG, pongId))
        }
        val driver = TlsPacketSessionDriver(
            channel,
            PacketSessionSupervisor(
                heartbeatIntervalNanos = 10L,
                responseTimeoutNanos = 50L,
                requestIdFactory = { requestId }
            ),
            nanoTime = { 1L }
        )
        driver.start()

        val result = driver.pumpOnce(100)

        assertEquals(PacketPumpOutcome.DISCONNECTED, result.outcome)
        assertEquals(PacketSessionState.DISCONNECTED, result.state)
        assertFalse(channel.isOpen)
    }

    @Test
    fun closedDriverRejectsFurtherWork() {
        val channel = FakeChannel()
        val driver = TlsPacketSessionDriver(channel, nanoTime = { 1L })
        driver.start()
        driver.close()

        assertFalse(driver.enqueue(packet(NexusMessageType.HELLO)))
        assertEquals(PacketSessionState.CLOSED, driver.snapshot().state)
        assertNull(channel.incoming.pollFirst())
    }

    private fun packet(
        type: NexusMessageType,
        id: String = requestId
    ) = NexusTransportPacket.create(type, id)

    private class FakeChannel : BoundedPacketChannel {
        override var isOpen: Boolean = true
        val incoming = ArrayDeque<NexusTransportPacket>()
        val written = mutableListOf<NexusTransportPacket>()
        var readFailure: Exception? = null
        var closeOnReadFailure: Boolean = false
        var writeFailure: Exception? = null

        override fun readPacket(readTimeoutMillis: Int): NexusTransportPacket? {
            readFailure?.let {
                if (closeOnReadFailure) close()
                throw it
            }
            return incoming.pollFirst()
        }

        override fun writePacket(packet: NexusTransportPacket) {
            writeFailure?.let { throw it }
            if (!isOpen) throw java.io.IOException("closed")
            written += packet
        }

        override fun close() {
            isOpen = false
        }
    }
}
