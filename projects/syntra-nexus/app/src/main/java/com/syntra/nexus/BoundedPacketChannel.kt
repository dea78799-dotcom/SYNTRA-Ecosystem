package com.syntra.nexus

/**
 * Narrow packet-channel contract consumed by the session driver.
 * Implementations must preserve packet framing and enforce authenticated I/O.
 */
interface BoundedPacketChannel : AutoCloseable {
    val isOpen: Boolean
    fun readPacket(readTimeoutMillis: Int): NexusTransportPacket?
    fun writePacket(packet: NexusTransportPacket)
    override fun close()
}
