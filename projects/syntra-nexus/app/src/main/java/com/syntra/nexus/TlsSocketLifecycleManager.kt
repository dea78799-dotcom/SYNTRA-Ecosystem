package com.syntra.nexus

import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.SSLSocket

/**
 * Owns one authenticated TLS socket and serializes all protected I/O calls.
 * close() does not wait for the I/O lock: closing the socket can interrupt a
 * blocked read. Callers must route every socket operation through
 * withAuthorizedIo and must not retain the socket after the callback returns.
 */
class TlsSocketLifecycleManager private constructor(
    private val socket: SSLSocket,
    private val binding: TlsSocketChannelBinding,
    private val trustedDevices: TrustedDeviceRegistry,
    private val clock: () -> Long
) : AutoCloseable {
    private val stateLock = Any()
    private val ioLock = ReentrantLock(true)

    @Volatile
    private var currentState = TlsSocketLifecycleState.ACTIVE

    val state: TlsSocketLifecycleState
        get() = currentState

    @Throws(InterruptedException::class, IOException::class, SecurityException::class)
    fun <T> withAuthorizedIo(operation: (SSLSocket) -> T): T {
        ioLock.lockInterruptibly()
        try {
            requireAuthorized()
            val result = operation(socket)
            requireAuthorized()
            return result
        } finally {
            ioLock.unlock()
        }
    }

    fun refreshAuthorization(): Boolean = try {
        requireAuthorized()
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    private fun requireAuthorized() {
        var revoked = false
        synchronized(stateLock) {
            when (currentState) {
                TlsSocketLifecycleState.ACTIVE -> {
                    if (!binding.isAuthorized(socket, trustedDevices, clock())) {
                        currentState = TlsSocketLifecycleState.REVOKED
                        revoked = true
                    }
                }
                TlsSocketLifecycleState.REVOKED ->
                    throw SecurityException("TLS peer authorization has been revoked.")
                TlsSocketLifecycleState.CLOSED ->
                    throw IllegalStateException("TLS socket lifecycle is closed.")
            }
        }
        if (revoked) {
            closeSocketQuietly()
            throw SecurityException("TLS peer authorization is no longer valid.")
        }
    }

    override fun close() {
        synchronized(stateLock) {
            if (currentState == TlsSocketLifecycleState.ACTIVE) {
                currentState = TlsSocketLifecycleState.CLOSED
            }
        }
        closeSocketQuietly()
    }

    private fun closeSocketQuietly() {
        try {
            socket.close()
        } catch (_: IOException) {
            // Terminal state is retained; close is best-effort and idempotent.
        }
    }

    companion object {
        /** Takes ownership of an already-connected socket; performs no handshake. */
        fun capture(
            socket: SSLSocket,
            binding: TlsSocketChannelBinding,
            trustedDevices: TrustedDeviceRegistry,
            clock: () -> Long = System::currentTimeMillis
        ): TlsSocketLifecycleManager? {
            val manager = TlsSocketLifecycleManager(socket, binding, trustedDevices, clock)
            return manager.takeIf { it.refreshAuthorization() }
        }
    }
}

enum class TlsSocketLifecycleState {
    ACTIVE,
    REVOKED,
    CLOSED
}
