package com.syntra.nexus

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Real mutual-TLS loopback coverage using two temporary Android Keystore aliases.
 * The production installation identity is never read, replaced or deleted.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreMutualTlsLoopbackAndroidTest {

    @Before
    fun setUp() {
        deleteAlias(TEST_SERVER_IDENTITY_ALIAS)
        deleteAlias(TEST_CLIENT_IDENTITY_ALIAS)
    }

    @After
    fun tearDown() {
        deleteAlias(TEST_SERVER_IDENTITY_ALIAS)
        deleteAlias(TEST_CLIENT_IDENTITY_ALIAS)
    }

    @Test
    fun completesMutualTlsHandshakeAndEchoWithTwoKeystoreIdentities() {
        assertTrue(performLoopbackExchange(trustActualClient = true))
    }

    @Test
    fun rejectsClientWhenItsCertificateIsNotPinnedByServer() {
        assertFalse(performLoopbackExchange(trustActualClient = false))
    }

    private fun performLoopbackExchange(trustActualClient: Boolean): Boolean {
        createKeystoreIdentity(TEST_SERVER_IDENTITY_ALIAS)
        createKeystoreIdentity(TEST_CLIENT_IDENTITY_ALIAS)

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val serverCertificate = keyStore.getCertificate(TEST_SERVER_IDENTITY_ALIAS)
            as java.security.cert.X509Certificate
        val clientCertificate = keyStore.getCertificate(TEST_CLIENT_IDENTITY_ALIAS)
            as java.security.cert.X509Certificate

        val serverTrustKey = if (trustActualClient) {
            clientCertificate.publicKey.encoded
        } else {
            createUntrustedPublicKey()
        }
        val serverContext = NexusTlsContextFactory.create(
            registryFor(serverTrustKey),
            PairingIdentityCrypto.fingerprint(serverTrustKey),
            TEST_PAIRING_REQUEST_ID,
            TEST_TRANSCRIPT_HASH,
            TEST_SERVER_IDENTITY_ALIAS
        )
        val clientContext = NexusTlsContextFactory.create(
            registryFor(serverCertificate.publicKey.encoded),
            PairingIdentityCrypto.fingerprint(serverCertificate.publicKey.encoded),
            TEST_PAIRING_REQUEST_ID,
            TEST_TRANSCRIPT_HASH,
            TEST_CLIENT_IDENTITY_ALIAS
        )

        val serverSocket = serverContext.serverSocketFactory.createServerSocket(
            0,
            1,
            InetAddress.getLoopbackAddress()
        ) as SSLServerSocket
        NexusTlsProtocolPolicy.configureServerSocket(serverSocket)
        serverSocket.soTimeout = SOCKET_TIMEOUT_MILLIS

        val executor = Executors.newSingleThreadExecutor()
        val serverResult = executor.submit<Boolean> {
            try {
                (serverSocket.accept() as SSLSocket).use { accepted ->
                    accepted.soTimeout = SOCKET_TIMEOUT_MILLIS
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

        return try {
            val clientSocket = clientContext.socketFactory.createSocket(
                InetAddress.getLoopbackAddress(),
                serverSocket.localPort
            ) as SSLSocket
            val clientSucceeded = clientSocket.use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MILLIS
                NexusTlsProtocolPolicy.configureClientSocket(socket)
                socket.startHandshake()
                socket.outputStream.write(TEST_BYTE)
                socket.outputStream.flush()
                socket.inputStream.read() == TEST_BYTE
            }
            val serverSucceeded = serverResult.get(
                SOCKET_TIMEOUT_MILLIS.toLong(),
                TimeUnit.MILLISECONDS
            )
            clientSucceeded && serverSucceeded
        } catch (_: Exception) {
            runCatching {
                serverResult.get(SOCKET_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
            }
            false
        } finally {
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }
    }

    private fun createKeystoreIdentity(alias: String) {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKeyPair()
    }

    private fun registryFor(publicKeyEncoded: ByteArray): TrustedDeviceRegistry {
        val record = TrustedDeviceRecord(
            fingerprint = PairingIdentityCrypto.fingerprint(publicKeyEncoded),
            publicKeyEncoded = publicKeyEncoded.copyOf(),
            displayName = "Android loopback TLS test peer",
            trustedAtMillis = 1L,
            pairingRequestId = TEST_PAIRING_REQUEST_ID,
            transcriptHashHex = TEST_TRANSCRIPT_HASH
        )
        return TrustedDeviceRegistry(object : TrustedDevicePersistence {
            override fun load(): List<TrustedDeviceRecord> = listOf(record)
            override fun save(records: List<TrustedDeviceRecord>) = Unit
        })
    }

    private fun createUntrustedPublicKey(): ByteArray =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair().public.encoded

    private fun deleteAlias(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TEST_SERVER_IDENTITY_ALIAS = "syntra.nexus.instrumentation.server.v1"
        const val TEST_CLIENT_IDENTITY_ALIAS = "syntra.nexus.instrumentation.client.v1"
        const val TEST_PAIRING_REQUEST_ID = "EEEEEEEEEEEEEEEEEEEEEE"
        const val TEST_TRANSCRIPT_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val TEST_BYTE = 0x5A
        const val SOCKET_TIMEOUT_MILLIS = 8_000
    }
}
