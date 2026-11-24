package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketSessionSupervisorTest {
    private val appId = "AbCdEf0123456789_-XYZa"
    private val pingId = "0123456789abcdefghij_-"

    @Test
    fun startsOnlyOnceAndRejectsEnqueueBeforeStart() {
        val supervisor = PacketSessionSupervisor(requestIdFactory = { pingId })
        assertFalse(supervisor.enqueue(packet(NexusMessageType.HELLO)))
        assertTrue(supervisor.start(100L))
        assertFalse(supervisor.start(101L))
        assertTrue(supervisor.enqueue(packet(NexusMessageType.HELLO)))
    }

    @Test
    fun outboundQueueIsBoundedAndDoesNotBlock() {
        val supervisor = PacketSessionSupervisor(queueCapacity = 2)
        supervisor.start(0L)
        assertTrue(supervisor.enqueue(packet(NexusMessageType.HELLO)))
        assertTrue(supervisor.enqueue(packet(NexusMessageType.ERROR)))
        assertFalse(supervisor.enqueue(packet(NexusMessageType.PAIRING_CHALLENGE)))
        assertEquals(2, supervisor.snapshot().queuedPackets)
    }

    @Test
    fun applicationCannotForgeControlPackets() {
        val supervisor = PacketSessionSupervisor()
        supervisor.start(0L)
        assertFalse(supervisor.enqueue(packet(NexusMessageType.PING)))
        assertFalse(supervisor.enqueue(packet(NexusMessageType.PONG)))
    }

    @Test
    fun heartbeatCreatesCorrelatedPingAndAcceptsMatchingPong() {
        val supervisor = PacketSessionSupervisor(
            heartbeatIntervalNanos = 10L,
            responseTimeoutNanos = 20L,
            requestIdFactory = { pingId }
        )
        supervisor.start(100L)

        val ping = supervisor.pollOutbound(110L)
        assertEquals(NexusMessageType.PING, ping?.type)
        assertEquals(pingId, ping?.requestId)
        assertTrue(supervisor.snapshot().awaitingPong)

        assertEquals(
            PacketReceiveResult.PONG_ACCEPTED,
            supervisor.receive(packet(NexusMessageType.PONG, pingId), 115L)
        )
        assertFalse(supervisor.snapshot().awaitingPong)
        assertEquals(PacketSessionState.ACTIVE, supervisor.snapshot().state)
    }

    @Test
    fun unsolicitedOrMismatchedPongDisconnectsSession() {
        val supervisor = PacketSessionSupervisor(
            heartbeatIntervalNanos = 10L,
            responseTimeoutNanos = 20L,
            requestIdFactory = { pingId }
        )
        supervisor.start(0L)
        assertEquals(
            PacketReceiveResult.DISCONNECTED,
            supervisor.receive(packet(NexusMessageType.PONG, appId), 1L)
        )
        assertEquals(PacketSessionState.DISCONNECTED, supervisor.snapshot().state)
    }

    @Test
    fun missingPongExpiresAndClearsQueue() {
        val supervisor = PacketSessionSupervisor(
            heartbeatIntervalNanos = 10L,
            responseTimeoutNanos = 5L,
            requestIdFactory = { pingId }
        )
        supervisor.start(0L)
        supervisor.enqueue(packet(NexusMessageType.HELLO))
        assertEquals(NexusMessageType.HELLO, supervisor.pollOutbound(10L)?.type)
        assertEquals(PacketSessionState.ACTIVE, supervisor.tick(14L).state)
        assertEquals(NexusMessageType.PING, supervisor.pollOutbound(14L)?.type)
        assertEquals(PacketSessionState.DISCONNECTED, supervisor.tick(19L).state)
        assertEquals(0, supervisor.snapshot().queuedPackets)
        assertNull(supervisor.pollOutbound(16L))
    }

    @Test
    fun respondsToPeerPingUsingSameCorrelationId() {
        val supervisor = PacketSessionSupervisor()
        supervisor.start(0L)
        assertEquals(
            PacketReceiveResult.PING_QUEUED,
            supervisor.receive(packet(NexusMessageType.PING, pingId), 1L)
        )
        val pong = supervisor.pollOutbound(2L)
        assertEquals(NexusMessageType.PONG, pong?.type)
        assertEquals(pingId, pong?.requestId)
    }

    @Test
    fun controlQueueOverflowDisconnectsInsteadOfDroppingPong() {
        val supervisor = PacketSessionSupervisor(queueCapacity = 1)
        supervisor.start(0L)
        assertTrue(supervisor.enqueue(packet(NexusMessageType.HELLO)))
        assertEquals(
            PacketReceiveResult.DISCONNECTED,
            supervisor.receive(packet(NexusMessageType.PING, pingId), 1L)
        )
        assertEquals(PacketSessionState.DISCONNECTED, supervisor.snapshot().state)
    }

    @Test
    fun closeIsTerminalAndClearsPendingWork() {
        val supervisor = PacketSessionSupervisor()
        supervisor.start(0L)
        supervisor.enqueue(packet(NexusMessageType.HELLO))
        supervisor.close()
        assertEquals(PacketSessionState.CLOSED, supervisor.snapshot().state)
        assertEquals(0, supervisor.snapshot().queuedPackets)
        assertFalse(supervisor.enqueue(packet(NexusMessageType.ERROR)))
        assertEquals(PacketReceiveResult.REJECTED, supervisor.receive(packet(NexusMessageType.HELLO), 1L))
    }

    @Test
    fun backwardsMonotonicClockFailsClosed() {
        val supervisor = PacketSessionSupervisor()
        supervisor.start(20L)
        assertEquals(PacketSessionState.DISCONNECTED, supervisor.tick(19L).state)
    }

    private fun packet(
        type: NexusMessageType,
        requestId: String = appId
    ) = NexusTransportPacket.create(type, requestId)
}
