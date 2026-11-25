package com.syntra.nexus

import java.net.SocketAddress
import java.security.MessageDigest
import javax.net.ssl.SSLSocket

internal fun interface TlsChannelBindingAuthorization {
    fun isAuthorized(currentTrustedDevices: TrustedDeviceRegistry, nowMillis: Long): Boolean
}

/**
 * Binds an authorization handle to one exact, already-connected SSLSocket.
 *
 * The object retains the socket reference and snapshots its endpoints and
 * negotiated JSSE session identity. It never creates, connects or closes a
 * socket. Call isAuthorized immediately before each protected I/O operation.
 */
class TlsSocketChannelBinding private constructor(
    private val socket: SSLSocket,
    private val authorization: TlsSessionAuthorization,
    private val localAddress: SocketAddress,
    private val remoteAddress: SocketAddress,
    private val localPort: Int,
    private val remotePort: Int,
    private val sessionId: ByteArray,
    private val sessionCreationTime: Long,
    private val protocol: String
) : TlsChannelBindingAuthorization {
    /**
     * Candidate identity must be the same socket object captured after the
     * handshake. A different socket cannot reuse this authorization handle.
     */
    override fun isAuthorized(
        currentTrustedDevices: TrustedDeviceRegistry,
        nowMillis: Long
    ): Boolean = isAuthorized(socket, currentTrustedDevices, nowMillis)

    fun isAuthorized(
        candidate: SSLSocket,
        currentTrustedDevices: TrustedDeviceRegistry,
        nowMillis: Long
    ): Boolean {
        if (candidate !== socket || nowMillis < 0L) return false
        return try {
            if (!isOpen(socket) ||
                socket.localSocketAddress != localAddress ||
                socket.remoteSocketAddress != remoteAddress ||
                socket.localPort != localPort ||
                socket.port != remotePort
            ) return false

            val currentSession = socket.session
            if (!currentSession.isValid ||
                currentSession.protocol != protocol ||
                currentSession.creationTime != sessionCreationTime ||
                !MessageDigest.isEqual(currentSession.id ?: ByteArray(0), sessionId) ||
                !authorization.isBoundTo(currentSession)
            ) return false

            authorization.isAuthorized(currentTrustedDevices, nowMillis)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /**
         * Capture only after a completed mutual-TLS handshake. Empty session
         * identifiers are rejected because they cannot provide a stable
         * per-session binding for this policy.
         */
        fun capture(
            socket: SSLSocket,
            authorization: TlsSessionAuthorization,
            trustedDevices: TrustedDeviceRegistry,
            nowMillis: Long
        ): TlsSocketChannelBinding? {
            if (nowMillis < 0L || !isOpen(socket)) return null
            return try {
                val session = socket.session
                val id = session.id ?: return null
                if (id.isEmpty() || id.size > MAX_SESSION_ID_BYTES ||
                    !session.isValid ||
                    !CapabilityRequestTlsIngress.isSupportedTlsProtocol(session.protocol) ||
                    !authorization.isBoundTo(session) ||
                    !authorization.isAuthorized(trustedDevices, nowMillis)
                ) return null

                val local = socket.localSocketAddress ?: return null
                val remote = socket.remoteSocketAddress ?: return null
                val localPort = socket.localPort
                val remotePort = socket.port
                if (localPort !in 1..65535 || remotePort !in 1..65535) return null

                TlsSocketChannelBinding(
                    socket = socket,
                    authorization = authorization,
                    localAddress = local,
                    remoteAddress = remote,
                    localPort = localPort,
                    remotePort = remotePort,
                    sessionId = id.copyOf(),
                    sessionCreationTime = session.creationTime,
                    protocol = session.protocol
                )
            } catch (_: Exception) {
                null
            }
        }

        private const val MAX_SESSION_ID_BYTES = 64

        private fun isOpen(socket: SSLSocket): Boolean =
            socket.isConnected && !socket.isClosed &&
                !socket.isInputShutdown && !socket.isOutputShutdown
    }
}
