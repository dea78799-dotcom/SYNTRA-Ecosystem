package com.syntra.nexus

import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory

/** Shared test-only certificate and private-key fixture loader. */
internal object NexusMutualTlsTestFixtures {
    fun certificate(identity: String): X509Certificate {
        val bytes = resource(identity + "_cert.pem")
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    fun keyManagers(identity: String): Array<javax.net.ssl.KeyManager> {
        val password = "nexus-test-only".toCharArray()
        val certificate = certificate(identity)
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(
                    resource(identity + "_private_pkcs8.pem").toString(Charsets.US_ASCII)
                        .lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
                )
            )
        )
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("nexus-loopback-test", privateKey, password, arrayOf(certificate))
        }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            init(store, password)
            keyManagers
        }
    }

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/tls/$name")) {
            "Missing test TLS fixture: $name"
        }.use { it.readBytes() }
}
