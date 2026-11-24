package com.syntra.nexus

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.PrivateKey
import java.security.Signature

private fun sha256Hex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

/**
 * Narrow, explicit capabilities. No blanket "device access" permission exists.
 */
enum class NexusCapability(val wireName: String) {
    USAGE_SUMMARY_READ("usage_summary_read"),
    SHARED_FILES_READ("shared_files_read"),
    FILE_RECEIVE("file_receive"),
    SCREEN_SHARE("screen_share")
}

/**
 * One short-lived capability request bound to a specific paired identity,
 * pairing transcript and logical session. It contains no permission booleans:
 * approval is represented by role-bound cryptographic signatures.
 */
data class CapabilityGrantRequest(
    val grantId: String,
    val peerFingerprint: String,
    val pairingRequestId: String,
    val transcriptHashHex: String,
    val sessionId: String,
    val capability: NexusCapability,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long
) {
    init {
        require(grantId.matches(Regex("^[A-Za-z0-9_-]{22,64}$"))) {
            "Capability grant ID has an invalid format."
        }
        require(peerFingerprint.matches(Regex("^[a-f0-9]{64}$"))) {
            "Peer fingerprint has an invalid format."
        }
        require(pairingRequestId.matches(Regex("^[A-Za-z0-9_-]{22}$"))) {
            "Pairing request ID has an invalid format."
        }
        require(transcriptHashHex.matches(Regex("^[a-f0-9]{64}$"))) {
            "Transcript hash has an invalid format."
        }
        require(sessionId.matches(Regex("^[A-Za-z0-9_-]{22,64}$"))) {
            "Session ID has an invalid format."
        }
        require(issuedAtMillis >= 0L && expiresAtMillis > issuedAtMillis) {
            "Capability grant timestamps are invalid."
        }
        require(issuedAtMillis <= Long.MAX_VALUE - MAX_GRANT_TTL_MILLIS &&
            expiresAtMillis <= issuedAtMillis + MAX_GRANT_TTL_MILLIS
        ) {
            "Capability grant lifetime exceeds the configured maximum."
        }
    }

    internal fun canonicalBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(PROTOCOL_VERSION)
            writeField(data, grantId)
            writeField(data, peerFingerprint)
            writeField(data, pairingRequestId)
            writeField(data, transcriptHashHex)
            writeField(data, sessionId)
            writeField(data, capability.wireName)
            data.writeLong(issuedAtMillis)
            data.writeLong(expiresAtMillis)
        }
        return output.toByteArray()
    }

    private fun writeField(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    companion object {
        const val MAX_GRANT_TTL_MILLIS = 5L * 60L * 1000L
        private const val PROTOCOL_VERSION = 1
    }
}

/**
 * Immutable in-memory grant. Signature bytes are never exposed by reference.
 */
class CapabilityGrant internal constructor(
    val request: CapabilityGrantRequest,
    localApprovalSignature: ByteArray,
    peerApprovalSignature: ByteArray,
    val revokedAtMillis: Long? = null
) {
    private val localSignature = localApprovalSignature.copyOf()
    private val peerSignature = peerApprovalSignature.copyOf()

    internal fun localSignatureCopy(): ByteArray = localSignature.copyOf()
    internal fun peerSignatureCopy(): ByteArray = peerSignature.copyOf()

    internal fun withRevocation(timestamp: Long) = CapabilityGrant(
        request, localSignature, peerSignature, timestamp
    )
}

/**
 * Local capability ledger. It is intentionally memory-only in this milestone:
 * after process death no old capability silently becomes active again.
 *
 * The current pairing coordinator treats this installation as INITIATOR and
 * the registered peer as RESPONDER. A future role-symmetric transport must
 * explicitly extend and test that role mapping before enabling network use.
 */
class CapabilityGrantRegistry(
    private val persistence: CapabilityGrantPersistence? = null
) {
    private val grants = linkedMapOf<String, CapabilityGrant>()

    init {
        val restored = persistence?.load().orEmpty()
        require(restored.size <= MAX_GRANTS) { "Capability ledger exceeds capacity." }
        restored.forEach { grant ->
            require(grants.put(grant.request.grantId, copyGrant(grant)) == null) {
                "Capability ledger contains duplicate grant IDs."
            }
        }
    }

    @Synchronized
    fun issue(
        request: CapabilityGrantRequest,
        transcript: PairingTranscript,
        session: PairingHandshakeSession,
        transaction: PairingTransactionSnapshot?,
        trustedDevices: TrustedDeviceRegistry,
        localApprovalSignature: ByteArray,
        peerApprovalSignature: ByteArray,
        nowMillis: Long
    ): CapabilityGrant? {
        if (nowMillis < request.issuedAtMillis ||
            nowMillis >= request.expiresAtMillis ||
            nowMillis - request.issuedAtMillis > MAX_REQUEST_AGE_MILLIS ||
            request.pairingRequestId != transcript.requestId ||
            request.transcriptHashHex != sha256Hex(transcript.sha256) ||
            request.peerFingerprint != PairingIdentityCrypto.fingerprint(
                transcript.publicKeyFor(PairingSignerRole.RESPONDER)
            ) ||
            request.expiresAtMillis > transcript.expiresAtMillis ||
            grants.containsKey(request.grantId)
        ) return null

        if (!TrustedDeviceAuthorization.mayAuthorizeNewSession(
                session, transcript, transaction, trustedDevices, nowMillis
            )
        ) return null

        if (!verifyApproval(
                transcript.publicKeyFor(PairingSignerRole.INITIATOR),
                request,
                PairingSignerRole.INITIATOR,
                localApprovalSignature
            ) || !verifyApproval(
                transcript.publicKeyFor(PairingSignerRole.RESPONDER),
                request,
                PairingSignerRole.RESPONDER,
                peerApprovalSignature
            )
        ) return null

        val grant = CapabilityGrant(
            request, localApprovalSignature, peerApprovalSignature
        )
        commit(grants + (request.grantId to grant))
        return copyGrant(grant)
    }

    /**
     * Rechecks the exact live grant, both signatures, session binding and
     * current trusted-device registry before every future capability operation.
     */
    @Synchronized
    fun mayUse(
        grantId: String,
        capability: NexusCapability,
        sessionId: String,
        session: PairingHandshakeSession,
        transcript: PairingTranscript,
        transaction: PairingTransactionSnapshot?,
        trustedDevices: TrustedDeviceRegistry,
        nowMillis: Long
    ): Boolean {
        if (nowMillis < 0L) return false
        val grant = grants[grantId] ?: return false
        val request = grant.request
        if (grant.revokedAtMillis != null ||
            request.capability != capability ||
            request.sessionId != sessionId ||
            nowMillis < request.issuedAtMillis ||
            nowMillis >= request.expiresAtMillis ||
            request.pairingRequestId != transcript.requestId ||
            request.transcriptHashHex != sha256Hex(transcript.sha256) ||
            request.peerFingerprint != PairingIdentityCrypto.fingerprint(
                transcript.publicKeyFor(PairingSignerRole.RESPONDER)
            )
        ) return false

        if (!TrustedDeviceAuthorization.mayAuthorizeNewSession(
                session, transcript, transaction, trustedDevices, nowMillis
            )
        ) return false

        return verifyApproval(
            transcript.publicKeyFor(PairingSignerRole.INITIATOR),
            request,
            PairingSignerRole.INITIATOR,
            grant.localSignatureCopy()
        ) && verifyApproval(
            transcript.publicKeyFor(PairingSignerRole.RESPONDER),
            request,
            PairingSignerRole.RESPONDER,
            grant.peerSignatureCopy()
        )
    }

    @Synchronized
    fun revoke(grantId: String, nowMillis: Long): Boolean {
        if (nowMillis < 0L) return false
        val current = grants[grantId] ?: return false
        if (current.revokedAtMillis != null || nowMillis < current.request.issuedAtMillis) {
            return false
        }
        commit(grants + (grantId to current.withRevocation(nowMillis)))
        return true
    }

    @Synchronized
    fun snapshot(): List<CapabilityGrant> = grants.values.map(::copyGrant)

    private fun commit(next: Map<String, CapabilityGrant>) {
        persistence?.save(next.values.map(::copyGrant))
        grants.clear()
        next.forEach { (id, grant) -> grants[id] = copyGrant(grant) }
    }

    private fun copyGrant(grant: CapabilityGrant) = CapabilityGrant(
        grant.request,
        grant.localSignatureCopy(),
        grant.peerSignatureCopy(),
        grant.revokedAtMillis
    )

    companion object {
        private const val MAX_GRANTS = 128
        private const val MAX_REQUEST_AGE_MILLIS = 30_000L
        private val SIGNATURE_DOMAIN =
            "SYNTRA-NEXUS-CAPABILITY-GRANT-v1\u0000".toByteArray(Charsets.UTF_8)

        /** Used by each device only after its owner explicitly approves the exact request. */
        fun signApproval(
            privateKey: PrivateKey,
            request: CapabilityGrantRequest,
            role: PairingSignerRole
        ): ByteArray {
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(privateKey)
            signer.update(SIGNATURE_DOMAIN)
            signer.update(role.wireValue.toByte())
            signer.update(request.canonicalBytes())
            return signer.sign()
        }

        private fun verifyApproval(
            publicKeyEncoded: ByteArray,
            request: CapabilityGrantRequest,
            role: PairingSignerRole,
            signatureBytes: ByteArray
        ): Boolean {
            if (signatureBytes.isEmpty() || signatureBytes.size > 128) return false
            return try {
                val publicKey = PairingIdentityCrypto.requireP256PublicKey(publicKeyEncoded)
                val verifier = Signature.getInstance("SHA256withECDSA")
                verifier.initVerify(publicKey)
                verifier.update(SIGNATURE_DOMAIN)
                verifier.update(role.wireValue.toByte())
                verifier.update(request.canonicalBytes())
                verifier.verify(signatureBytes)
            } catch (_: Exception) {
                false
            }
        }
    }
}
