package com.syntra.nexus

import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager

/**
 * Presents this installation's non-exportable Android Keystore identity in TLS.
 * The certificate is the X.509 certificate already associated with the identity alias.
 */
class AndroidKeystoreX509KeyManager(
    keyStore: KeyStore? = null
) : X509KeyManager {
    private val store: KeyStore = keyStore ?: KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val privateKey: PrivateKey
    private val certificateChain: Array<X509Certificate>

    val localFingerprint: String

    init {
        require(store.containsAlias(IDENTITY_ALIAS)) {
            "Create the NEXUS device identity before initializing TLS."
        }
        privateKey = store.getKey(IDENTITY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Android Keystore private identity key is unavailable.")

        val rawChain = store.getCertificateChain(IDENTITY_ALIAS)
            ?: throw IllegalStateException("Android Keystore identity certificate is unavailable.")
        require(rawChain.isNotEmpty() && rawChain.size <= MAX_CERTIFICATE_CHAIN_LENGTH) {
            "Local TLS certificate chain length is invalid."
        }
        certificateChain = rawChain.map { certificate ->
            certificate as? X509Certificate
                ?: throw IllegalStateException("Local identity certificate is not X.509.")
        }.toTypedArray()

        val leaf = certificateChain.first()
        leaf.checkValidity()
        require(leaf.basicConstraints < 0) {
            "A CA certificate cannot be used as the local NEXUS device identity."
        }
        require(leaf.publicKey.algorithm.equals("EC", ignoreCase = true)) {
            "Local NEXUS TLS identity must use an EC public key."
        }
        PairingIdentityCrypto.requireP256PublicKey(leaf.publicKey.encoded)
        require(privateKey.algorithm.equals("EC", ignoreCase = true)) {
            "Local NEXUS TLS identity private key must use EC."
        }
        val keyUsage = leaf.keyUsage
        require(keyUsage == null || (keyUsage.isNotEmpty() && keyUsage[0])) {
            "Local identity certificate does not permit digital signatures."
        }

        verifyPrivateKeyMatchesCertificate(privateKey, leaf)
        localFingerprint = PairingIdentityCrypto.fingerprint(leaf.publicKey.encoded)
    }

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        if (supportsEc(keyType)) arrayOf(IDENTITY_ALIAS) else null

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? = if (keyType.orEmpty().any(::supportsEc)) IDENTITY_ALIAS else null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        if (supportsEc(keyType)) arrayOf(IDENTITY_ALIAS) else null

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? = if (supportsEc(keyType)) IDENTITY_ALIAS else null

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        if (alias == IDENTITY_ALIAS) certificateChain.copyOf() else null

    override fun getPrivateKey(alias: String?): PrivateKey? =
        if (alias == IDENTITY_ALIAS) privateKey else null

    private fun verifyPrivateKeyMatchesCertificate(
        key: PrivateKey,
        certificate: X509Certificate
    ) {
        val challenge = ByteArray(CHALLENGE_BYTES).also(SecureRandom()::nextBytes)
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(key)
        signer.update(challenge)
        val proof = signer.sign()

        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(certificate.publicKey)
        verifier.update(challenge)
        check(verifier.verify(proof)) {
            "Android Keystore private key does not match the local X.509 certificate."
        }
    }

    private fun supportsEc(keyType: String?): Boolean =
        keyType != null && (
            keyType.equals("EC", ignoreCase = true) ||
                keyType.contains("ECDSA", ignoreCase = true)
            )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val IDENTITY_ALIAS = "syntra.nexus.device.identity.v1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val CHALLENGE_BYTES = 32
        const val MAX_CERTIFICATE_CHAIN_LENGTH = 8
    }
}

/** TLS policy shared by client and server socket setup. */
object NexusTlsProtocolPolicy {
    private val PREFERRED_PROTOCOLS = listOf("TLSv1.3", "TLSv1.2")

    fun enabledProtocols(supportedProtocols: Array<String>): Array<String> {
        val supported = supportedProtocols.toSet()
        val selected = PREFERRED_PROTOCOLS.filter(supported::contains)
        require(selected.isNotEmpty()) {
            "This device does not support TLS 1.2 or TLS 1.3."
        }
        return selected.toTypedArray()
    }

    fun configureClientSocket(socket: SSLSocket) {
        socket.enabledProtocols = enabledProtocols(socket.supportedProtocols)
    }

    fun configureServerSocket(socket: SSLServerSocket) {
        socket.enabledProtocols = enabledProtocols(socket.supportedProtocols)
        socket.needClientAuth = true
    }
}

/**
 * Creates a TLS context pinned to one exact, active remote device.
 * This factory does not open sockets, start a listener, or send application data.
 */
object NexusTlsContextFactory {
    fun create(
        trustedDeviceRegistry: TrustedDeviceRegistry,
        expectedPeerFingerprint: String
    ): SSLContext {
        require(expectedPeerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) {
            "Expected peer fingerprint must be 64 lowercase hexadecimal characters."
        }
        require(trustedDeviceRegistry.activeDevices().any {
            it.fingerprint == expectedPeerFingerprint
        }) {
            "Expected peer is unknown or has been revoked."
        }

        val keyManager = AndroidKeystoreX509KeyManager()
        require(keyManager.localFingerprint != expectedPeerFingerprint) {
            "Local and remote device identities must be different."
        }

        val trustManager = PinnedPeerTrustManager(
            trustedDeviceRegistry = trustedDeviceRegistry,
            expectedPeerFingerprint = expectedPeerFingerprint
        )
        return SSLContext.getInstance("TLS").apply {
            init(
                arrayOf(keyManager),
                arrayOf(trustManager),
                SecureRandom()
            )
        }
    }
}
