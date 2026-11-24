package com.syntra.nexus

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real JSSE mutual-TLS tests using loopback sockets and test-only identities. */
class NexusMutualTlsIntegrationTest {

    @Test
    fun completesHandshakeAndExchangeWhenBothPeerKeysArePinned() {
        assertTrue(performExchange("device_a", "device_b", listOf("device_b"), listOf("device_a")))
    }

    @Test
    fun rejectsPeerWhenPinnedKeyHasDifferentTranscriptProvenance() {
        assertFalse(
            performExchange(
                "device_a",
                "device_b",
                listOf("device_b"),
                listOf("device_a"),
                serverExpectedTranscriptHashHex = "b".repeat(64)
            )
        )
    }

    @Test
    fun rejectsPeerWhenPinnedKeyHasDifferentPairingRequestId() {
        assertFalse(
            performExchange(
                "device_a",
                "device_b",
                listOf("device_b"),
                listOf("device_a"),
                serverExpectedPairingRequestId = "BBBBBBBBBBBBBBBBBBBBBB"
            )
        )
    }

    @Test
    fun rejectsClientWhoseCertificateIsNotInServerTrustRegistry() {
        assertFalse(performExchange("device_a", "unknown_device", listOf("device_b"), listOf("device_a")))
    }

    @Test
    fun rejectsRevokedClientIdentityDuringTlsHandshake() {
        assertFalse(
            performExchange(
                "device_a", "device_b", listOf("device_b"), listOf("device_a"),
                revokedServerTrusts = setOf("device_b")
            )
        )
    }

    @Test
    fun serverRequiresClientCertificate() {
        assertFalse(performExchange("device_a", null, listOf("device_b"), listOf("device_a")))
    }

    @Test
    fun rejectsExpiredServerCertificateEvenWhenItsPublicKeyIsPinned() {
        assertFalse(
            performExchange(
                "device_a", "device_b", listOf("device_b"), listOf("device_a"),
                expiredServerCertificate = true
            )
        )
    }

    private fun performExchange(
        serverIdentity: String,
        clientIdentity: String?,
        serverTrusts: List<String>,
        clientTrusts: List<String>,
        revokedServerTrusts: Set<String> = emptySet(),
        expiredServerCertificate: Boolean = false,
        serverExpectedPairingRequestId: String = TEST_PAIRING_REQUEST_ID,
        serverExpectedTranscriptHashHex: String = TEST_TRANSCRIPT_HASH
    ): Boolean {
        val serverContext = createContext(
            serverIdentity, serverTrusts, revokedServerTrusts, expiredServerCertificate,
            serverExpectedPairingRequestId, serverExpectedTranscriptHashHex
        )
        val clientContext = createContext(clientIdentity, clientTrusts)
        val serverSocket = serverContext.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getLoopbackAddress()
        ) as SSLServerSocket
        NexusTlsProtocolPolicy.configureServerSocket(serverSocket)
        serverSocket.soTimeout = SOCKET_TIMEOUT_MILLIS

        val executor = Executors.newSingleThreadExecutor()
        val serverResult = executor.submit<Boolean> {
            try {
                (serverSocket.accept() as SSLSocket).use { accepted ->
                    accepted.startHandshake()
                    val received = accepted.inputStream.read()
                    accepted.outputStream.write(received)
                    accepted.outputStream.flush()
                    received == TEST_BYTE
                }
            } catch (_: Exception) {
                false
            }
        }

        try {
            val clientSocket = clientContext.socketFactory.createSocket(
                InetAddress.getLoopbackAddress(), serverSocket.localPort
            ) as SSLSocket
            clientSocket.soTimeout = SOCKET_TIMEOUT_MILLIS
            val clientSucceeded = clientSocket.use { socket ->
                NexusTlsProtocolPolicy.configureClientSocket(socket)
                socket.startHandshake()
                socket.outputStream.write(TEST_BYTE)
                socket.outputStream.flush()
                socket.inputStream.read() == TEST_BYTE
            }
            val serverSucceeded = serverResult.get(
                SOCKET_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS
            )
            return clientSucceeded && serverSucceeded
        } catch (_: Exception) {
            return false
        } finally {
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }
    }

    private fun createContext(
        identityName: String?,
        trustedNames: List<String>,
        revokedNames: Set<String> = emptySet(),
        expiredIdentityCertificate: Boolean = false,
        expectedPairingRequestId: String = TEST_PAIRING_REQUEST_ID,
        expectedTranscriptHashHex: String = TEST_TRANSCRIPT_HASH
    ): SSLContext {
        val keyManagers = if (identityName == null) {
            emptyArray()
        } else {
            val certFile = if (expiredIdentityCertificate) {
                "device_a_expired_cert.pem"
            } else {
                identityName + "_cert.pem"
            }
            val certificate = readCertificate(certFile)
            val privateKey = readPrivateKey(identityName + "_private_pkcs8.pem")
            val password = TEST_KEY_PASSWORD
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setKeyEntry(TEST_KEY_ALIAS, privateKey, password, arrayOf(certificate))
            }
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
                init(keyStore, password)
                keyManagers
            }
        }

        val records = trustedNames.map { name ->
            val publicKey = readCertificate(name + "_cert.pem").publicKey.encoded
            TrustedDeviceRecord(
                fingerprint = PairingIdentityCrypto.fingerprint(publicKey),
                publicKeyEncoded = publicKey,
                displayName = "TLS test " + name,
                trustedAtMillis = TRUSTED_AT_MILLIS,
                revokedAtMillis = if (name in revokedNames) REVOKED_AT_MILLIS else null,
                pairingRequestId = TEST_PAIRING_REQUEST_ID,
                transcriptHashHex = TEST_TRANSCRIPT_HASH
            )
        }
        val registry = TrustedDeviceRegistry(object : TrustedDevicePersistence {
            override fun load(): List<TrustedDeviceRecord> = records
            override fun save(records: List<TrustedDeviceRecord>) = Unit
        })

        return SSLContext.getInstance("TLS").apply {
            val expectedPeerName = trustedNames.first()
            val expectedPeerPublicKey = readCertificate(expectedPeerName + "_cert.pem").publicKey.encoded
            init(
                keyManagers,
                arrayOf(PinnedPeerTrustManager(
                    registry,
                    PairingIdentityCrypto.fingerprint(expectedPeerPublicKey),
                    expectedPairingRequestId,
                    expectedTranscriptHashHex
                )),
                SecureRandom()
            )
        }
    }

    private fun readCertificate(fileName: String): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(
            ByteArrayInputStream(readResource(fileName))
        ) as X509Certificate

    private fun readPrivateKey(fileName: String) = KeyFactory.getInstance("EC").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.getMimeDecoder().decode(
                readResource(fileName).toString(Charsets.US_ASCII)
                    .lineSequence()
                    .filterNot { it.startsWith("-----") }
                    .joinToString("")
            )
        )
    )

    private fun readResource(fileName: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/tls/" + fileName)) {
            "Missing test TLS fixture: " + fileName
        }.use { it.readBytes() }

    private companion object {
        const val TEST_KEY_ALIAS = "nexus-loopback-test"
        const val TEST_BYTE = 0x5A
        const val SOCKET_TIMEOUT_MILLIS = 8_000
        const val TEST_PAIRING_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        const val TEST_TRANSCRIPT_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val TRUSTED_AT_MILLIS = 1_000L
        const val REVOKED_AT_MILLIS = 2_000L
        val TEST_KEY_PASSWORD = "nexus-test-only".toCharArray()
    }
}
