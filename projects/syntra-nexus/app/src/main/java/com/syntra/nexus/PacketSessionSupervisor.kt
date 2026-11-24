package com.syntra.nexus

import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.Base64

enum class PacketSessionState { NEW, ACTIVE, DISCONNECTED, CLOSED }
enum class PacketReceiveResult { APPLICATION_PACKET, PONG_ACCEPTED, PING_QUEUED, REJECTED, DISCONNECTED }

data class PacketSessionSnapshot(
    val state: PacketSessionState,
    val queuedPackets: Int,
    val awaitingPong: Boolean,
    val lastActivityNanos: Long?
)

/**
 * Deterministic session policy layer for a future transport owner.
 *
 * The caller drives start/tick/receive/pollOutbound using System.nanoTime().
 * This class owns no thread, scheduler, socket, or network operation.
 */
class PacketSessionSupervisor(
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    private val heartbeatIntervalNanos: Long = DEFAULT_HEARTBEAT_INTERVAL_NANOS,
    private val responseTimeoutNanos: Long = DEFAULT_RESPONSE_TIMEOUT_NANOS,
    private val requestIdFactory: () -> String = ::newRequestId
) {
    private val outbound = ArrayDeque<NexusTransportPacket>()
    private var pendingPingId: String? = null
    private var pingSentAtNanos: Long? = null
    private var lastActivityNanos: Long? = null
    private var lastObservedNanos: Long? = null

    @Volatile
    private var currentState = PacketSessionState.NEW

    init {
        require(queueCapacity in 1..MAX_QUEUE_CAPACITY)
        require(heartbeatIntervalNanos in 1..MAX_INTERVAL_NANOS)
        require(responseTimeoutNanos in 1..MAX_INTERVAL_NANOS)
    }

    @Synchronized
    fun start(nowNanos: Long): Boolean {
        if (currentState != PacketSessionState.NEW || !observeClock(nowNanos)) return false
        currentState = PacketSessionState.ACTIVE
        lastActivityNanos = nowNanos
        return true
    }

    /** Application packets cannot impersonate the supervisor's PING/PONG control frames. */
    @Synchronized
    fun enqueue(packet: NexusTransportPacket): Boolean {
        if (currentState != PacketSessionState.ACTIVE) return false
        if (packet.type == NexusMessageType.PING || packet.type == NexusMessageType.PONG) return false
        if (outbound.size >= queueCapacity) return false
        outbound.addLast(packet)
        return true
    }

    /** Called by the transport loop on its monotonic timer; never creates a thread. */
    @Synchronized
    fun tick(nowNanos: Long): PacketSessionSnapshot {
        if (currentState != PacketSessionState.ACTIVE || !observeClock(nowNanos)) return snapshot()
        if (hasTimedOut(nowNanos)) {
            disconnect()
            return snapshot()
        }
        schedulePingIfDue(nowNanos)
        return snapshot()
    }

    /** Returns one queued packet after applying timeout and heartbeat policy. */
    @Synchronized
    fun pollOutbound(nowNanos: Long): NexusTransportPacket? {
        if (currentState != PacketSessionState.ACTIVE || !observeClock(nowNanos)) return null
        if (hasTimedOut(nowNanos)) {
            disconnect()
            return null
        }
        schedulePingIfDue(nowNanos)
        return outbound.pollFirst()
    }

    @Synchronized
    fun receive(packet: NexusTransportPacket, nowNanos: Long): PacketReceiveResult {
        if (currentState != PacketSessionState.ACTIVE || !observeClock(nowNanos)) {
            return PacketReceiveResult.REJECTED
        }
        if (hasTimedOut(nowNanos)) {
            disconnect()
            return PacketReceiveResult.DISCONNECTED
        }

        when (packet.type) {
            NexusMessageType.PONG -> {
                if (pendingPingId != packet.requestId) {
                    disconnect()
                    return PacketReceiveResult.DISCONNECTED
                }
                pendingPingId = null
                pingSentAtNanos = null
                lastActivityNanos = nowNanos
                return PacketReceiveResult.PONG_ACCEPTED
            }
            NexusMessageType.PING -> {
                if (outbound.size >= queueCapacity) {
                    disconnect()
                    return PacketReceiveResult.DISCONNECTED
                }
                outbound.addLast(
                    NexusTransportPacket.create(NexusMessageType.PONG, packet.requestId)
                )
                lastActivityNanos = nowNanos
                return PacketReceiveResult.PING_QUEUED
            }
            else -> {
                lastActivityNanos = nowNanos
                return PacketReceiveResult.APPLICATION_PACKET
            }
        }
    }

    @Synchronized
    fun close() {
        if (currentState != PacketSessionState.CLOSED) {
            currentState = PacketSessionState.CLOSED
            outbound.clear()
            pendingPingId = null
            pingSentAtNanos = null
        }
    }

    @Synchronized
    fun snapshot(): PacketSessionSnapshot = PacketSessionSnapshot(
        state = currentState,
        queuedPackets = outbound.size,
        awaitingPong = pendingPingId != null,
        lastActivityNanos = lastActivityNanos
    )

    private fun schedulePingIfDue(nowNanos: Long) {
        val lastActivity = lastActivityNanos ?: return
        if (pendingPingId != null || nowNanos - lastActivity < heartbeatIntervalNanos) return
        if (outbound.size >= queueCapacity) {
            disconnect()
            return
        }

        val requestId = requestIdFactory()
        val ping = NexusTransportPacket.create(NexusMessageType.PING, requestId)
        outbound.addLast(ping)
        pendingPingId = requestId
        pingSentAtNanos = nowNanos
    }

    private fun hasTimedOut(nowNanos: Long): Boolean {
        val sentAt = pingSentAtNanos ?: return false
        return nowNanos - sentAt >= responseTimeoutNanos
    }

    private fun observeClock(nowNanos: Long): Boolean {
        val previous = lastObservedNanos
        if (previous != null && nowNanos - previous < 0L) {
            disconnect()
            return false
        }
        lastObservedNanos = nowNanos
        return true
    }

    private fun disconnect() {
        currentState = PacketSessionState.DISCONNECTED
        outbound.clear()
        pendingPingId = null
        pingSentAtNanos = null
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 32
        const val MAX_QUEUE_CAPACITY = 256
        const val DEFAULT_HEARTBEAT_INTERVAL_NANOS = 15_000_000_000L
        const val DEFAULT_RESPONSE_TIMEOUT_NANOS = 5_000_000_000L
        const val MAX_INTERVAL_NANOS = 3_600_000_000_000L

        private fun newRequestId(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
