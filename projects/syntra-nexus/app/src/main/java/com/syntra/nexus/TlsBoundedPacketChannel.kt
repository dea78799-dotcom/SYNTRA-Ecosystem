package com.syntra.nexus

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

/**
 * Reads and writes bounded SNX1 packets over one already-authorized TLS socket.
 *
 * All stream access is serialized by [TlsSocketLifecycleManager]. A timeout
 * before the first byte of a new header leaves the channel reusable. Once any
 * part of a frame has been consumed, timeout, EOF or malformed data closes
 * the manager because the byte stream can no longer be safely resynchronized.
 *
 * Socket SO_TIMEOUT bounds reads only. Java sockets do not expose a portable
 * write timeout; callers must use a bounded payload and close the manager to
 * interrupt a blocked write when cancellation is required.
 */
class TlsBoundedPacketChannel(
    private val lifecycle: TlsSocketLifecycleManager
) : BoundedPacketChannel {

    override val isOpen: Boolean
        get() = lifecycle.state == TlsSocketLifecycleState.ACTIVE

    /**
     * Reads exactly one packet. Returns null only for clean EOF between frames.
     *
     * @throws SocketTimeoutException if no frame byte arrives before the
     * timeout; if any frame byte was already consumed, the channel is closed.
     * @throws IOException for truncation or transport errors; the channel closes.
     * @throws IllegalArgumentException for an invalid SNX1 header or packet;
     * the channel closes.
     */
    @Throws(IOException::class)
    override fun readPacket(readTimeoutMillis: Int): NexusTransportPacket? {
        require(readTimeoutMillis in 1..MAX_READ_TIMEOUT_MILLIS) {
            "Read timeout must be between 1 and $MAX_READ_TIMEOUT_MILLIS milliseconds."
        }

        var frameStarted = false
        try {
            val packet = lifecycle.withAuthorizedIo { socket ->
                readOnePacket(socket, readTimeoutMillis) { frameStarted = true }
            }
            if (packet == null) lifecycle.close()
            return packet
        } catch (timeout: SocketTimeoutException) {
            if (frameStarted || timeout is PartialFrameTimeoutException) {
                lifecycle.close()
            }
            throw timeout
        } catch (error: IOException) {
            lifecycle.close()
            throw error
        } catch (error: IllegalArgumentException) {
            lifecycle.close()
            throw error
        }
    }

    /**
     * Encodes and writes one complete bounded packet under the lifecycle I/O
     * lock. A write failure closes the channel. No write-timeout guarantee is
     * made by the Java socket API.
     */
    @Throws(IOException::class)
    override fun writePacket(packet: NexusTransportPacket) {
        val frame = NexusTransportCodec.encode(packet)
        require(frame.size in NexusTransportCodec.HEADER_BYTES..NexusTransportCodec.MAX_PACKET_BYTES)
        try {
            lifecycle.withAuthorizedIo { socket ->
                socket.outputStream.write(frame)
                socket.outputStream.flush()
            }
        } catch (error: IOException) {
            lifecycle.close()
            throw error
        }
    }

    override fun close() = lifecycle.close()

    private fun readOnePacket(
        socket: SSLSocket,
        timeoutMillis: Int,
        onFrameStarted: () -> Unit
    ): NexusTransportPacket? {
        val previousTimeout = socket.soTimeout
        try {
            socket.soTimeout = timeoutMillis
            val input = socket.inputStream
            val header = ByteArray(NexusTransportCodec.HEADER_BYTES)
            if (!readExactly(input, header, allowCleanEof = true)) return null
            onFrameStarted()

            val payloadLength = ByteBuffer.wrap(header)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt(NexusTransportCodec.HEADER_BYTES - Int.SIZE_BYTES)
            require(payloadLength in 0..NexusTransportCodec.MAX_PAYLOAD_BYTES) {
                "SNX1 payload length is outside the permitted range."
            }

            val frame = ByteArray(NexusTransportCodec.HEADER_BYTES + payloadLength)
            header.copyInto(frame)
            if (payloadLength > 0) {
                readExactly(input, frame, NexusTransportCodec.HEADER_BYTES, payloadLength)
            }
            return NexusTransportCodec.decode(frame)
        } finally {
            socket.soTimeout = previousTimeout
        }
    }

    private fun readExactly(
        input: InputStream,
        destination: ByteArray,
        allowCleanEof: Boolean
    ): Boolean = readExactly(
        input, destination, 0, destination.size, allowCleanEof
    )

    private fun readExactly(
        input: InputStream,
        destination: ByteArray,
        offset: Int,
        length: Int
    ) {
        readExactly(input, destination, offset, length, allowCleanEof = false)
    }

    private fun readExactly(
        input: InputStream,
        destination: ByteArray,
        offset: Int,
        length: Int,
        allowCleanEof: Boolean
    ): Boolean {
        var consumed = 0
        while (consumed < length) {
            val count = try {
                input.read(destination, offset + consumed, length - consumed)
            } catch (timeout: SocketTimeoutException) {
                if (consumed > 0) throw PartialFrameTimeoutException(consumed, timeout)
                throw timeout
            }

            if (count < 0) {
                if (consumed == 0 && allowCleanEof) return false
                throw EOFException("TLS stream ended before the complete SNX1 frame arrived.")
            }
            if (count == 0) continue
            consumed += count
        }
        return true
    }

    private class PartialFrameTimeoutException(
        val bytesConsumed: Int,
        cause: SocketTimeoutException
    ) : SocketTimeoutException(
        "TLS read timed out after consuming $bytesConsumed bytes of an SNX1 frame."
    ) {
        init {
            initCause(cause)
        }
    }

    companion object {
        const val MAX_READ_TIMEOUT_MILLIS = 120_000
    }
}
