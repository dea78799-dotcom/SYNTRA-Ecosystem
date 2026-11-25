package com.syntra.nexus

import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

enum class PacketPumpOutcome {
    PACKET_RECEIVED,
    IDLE_TIMEOUT,
    DISCONNECTED,
    INACTIVE,
    PUMP_ALREADY_RUNNING
}

data class PacketPumpResult(
    val outcome: PacketPumpOutcome,
    val state: PacketSessionState,
    val receivedPacket: NexusTransportPacket? = null,
    val receiveResult: PacketReceiveResult? = null,
    val outboundPacketsWritten: Int = 0
)

/**
 * Single-step, caller-driven coordinator for one already-authenticated channel.
 *
 * No thread, executor, retry loop, socket or connection is created here.
 * The caller schedules pumpOnce() from its own transport owner. close() remains
 * non-blocking with respect to the pump so the underlying socket can interrupt
 * a blocked read.
 */
class TlsPacketSessionDriver(
    private val channel: BoundedPacketChannel,
    private val supervisor: PacketSessionSupervisor = PacketSessionSupervisor(),
    private val nanoTime: () -> Long = System::nanoTime
) : AutoCloseable {
    private val pumping = AtomicBoolean(false)

    fun start(): Boolean {
        if (!channel.isOpen) return false
        return supervisor.start(nanoTime())
    }

    fun enqueue(packet: NexusTransportPacket): Boolean = supervisor.enqueue(packet)

    fun snapshot(): PacketSessionSnapshot = supervisor.snapshot()

    fun pumpOnce(readTimeoutMillis: Int): PacketPumpResult {
        require(readTimeoutMillis in 1..TlsBoundedPacketChannel.MAX_READ_TIMEOUT_MILLIS)
        if (!pumping.compareAndSet(false, true)) {
            return result(PacketPumpOutcome.PUMP_ALREADY_RUNNING)
        }

        try {
            if (supervisor.snapshot().state != PacketSessionState.ACTIVE || !channel.isOpen) {
                failSession()
                return result(PacketPumpOutcome.INACTIVE)
            }

            supervisor.tick(nanoTime())
            if (supervisor.snapshot().state != PacketSessionState.ACTIVE) {
                failSession()
                return result(PacketPumpOutcome.DISCONNECTED)
            }

            var written = drainOutbound()
            if (supervisor.snapshot().state != PacketSessionState.ACTIVE || !channel.isOpen) {
                failSession()
                return result(PacketPumpOutcome.DISCONNECTED, written = written)
            }

            val incoming = try {
                channel.readPacket(readTimeoutMillis)
            } catch (timeout: SocketTimeoutException) {
                if (!channel.isOpen) {
                    failSession()
                    return result(PacketPumpOutcome.DISCONNECTED, written = written)
                }
                supervisor.tick(nanoTime())
                if (supervisor.snapshot().state != PacketSessionState.ACTIVE) {
                    failSession()
                    return result(PacketPumpOutcome.DISCONNECTED, written = written)
                }
                return result(PacketPumpOutcome.IDLE_TIMEOUT, written = written)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                failSession()
                return result(PacketPumpOutcome.DISCONNECTED, written = written)
            } catch (error: Exception) {
                failSession()
                return result(PacketPumpOutcome.DISCONNECTED, written = written)
            }

            if (incoming == null) {
                failSession()
                return result(PacketPumpOutcome.DISCONNECTED, written = written)
            }

            val receiveResult = supervisor.receive(incoming, nanoTime())
            if (receiveResult == PacketReceiveResult.DISCONNECTED ||
                receiveResult == PacketReceiveResult.REJECTED
            ) {
                failSession()
                return result(
                    PacketPumpOutcome.DISCONNECTED,
                    packet = incoming,
                    receive = receiveResult,
                    written = written
                )
            }

            written += drainOutbound()
            if (supervisor.snapshot().state != PacketSessionState.ACTIVE || !channel.isOpen) {
                failSession()
                return result(
                    PacketPumpOutcome.DISCONNECTED,
                    packet = incoming,
                    receive = receiveResult,
                    written = written
                )
            }

            return result(
                PacketPumpOutcome.PACKET_RECEIVED,
                packet = incoming,
                receive = receiveResult,
                written = written
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            failSession()
            return result(PacketPumpOutcome.DISCONNECTED)
        } catch (error: Exception) {
            failSession()
            return result(PacketPumpOutcome.DISCONNECTED)
        } finally {
            pumping.set(false)
        }
    }

    private fun drainOutbound(): Int {
        var written = 0
        while (supervisor.snapshot().state == PacketSessionState.ACTIVE && channel.isOpen) {
            val packet = supervisor.pollOutbound(nanoTime()) ?: break
            channel.writePacket(packet)
            written++
        }
        return written
    }

    private fun failSession() {
        supervisor.fail()
        channel.close()
    }

    private fun result(
        outcome: PacketPumpOutcome,
        packet: NexusTransportPacket? = null,
        receive: PacketReceiveResult? = null,
        written: Int = 0
    ) = PacketPumpResult(
        outcome = outcome,
        state = supervisor.snapshot().state,
        receivedPacket = packet,
        receiveResult = receive,
        outboundPacketsWritten = written
    )

    override fun close() {
        supervisor.close()
        channel.close()
    }
}
