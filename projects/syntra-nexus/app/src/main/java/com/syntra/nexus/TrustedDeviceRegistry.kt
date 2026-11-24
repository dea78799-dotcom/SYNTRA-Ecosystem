package com.syntra.nexus

/**
 * Local in-memory trust registry foundation.
 *
 * A peer can be added only from a completed, transcript-bound handshake
 * with both approvals. LAN discovery and QR scanning are not trust evidence.
 * This prototype is intentionally memory-only; encrypted persistence and
 * remote revocation are not implemented yet.
 */
data class TrustedDeviceRecord(
    val fingerprint: String,
    val publicKeyEncoded: ByteArray,
    val displayName: String,
    val trustedAtMillis: Long,
    val revokedAtMillis: Long? = null
) {
    val isRevoked: Boolean
        get() = revokedAtMillis != null
}

class TrustedDeviceRegistry {
    private val records = linkedMapOf<String, TrustedDeviceRecord>()

    @Synchronized
    fun registerAfterVerifiedPairing(
        session: PairingHandshakeSession,
        transcript: PairingTranscript,
        localPublicKeyEncoded: ByteArray,
        peerPublicKeyEncoded: ByteArray,
        peerRole: PairingSignerRole,
        displayName: String,
        nowMillis: Long
    ): TrustedDeviceRecord {
        require(session.state == PairingHandshakeState.PAIRED &&
            session.decision == PairingHandshakeDecision.BOTH_APPROVED) {
            "A completed handshake with both approvals is required."
        }
        require(session.isBoundToTranscript(transcript)) {
            "Handshake is not bound to this exact transcript."
        }
        require(nowMillis >= 0L) { "Timestamp cannot be negative." }
        require(displayName.isNotBlank() && displayName.length <= MAX_NAME_LENGTH) {
            "Device name must contain 1 to $MAX_NAME_LENGTH characters."
        }
        val localRole = when (peerRole) {
            PairingSignerRole.INITIATOR -> PairingSignerRole.RESPONDER
            PairingSignerRole.RESPONDER -> PairingSignerRole.INITIATOR
        }
        require(transcript.publicKeyFor(peerRole).contentEquals(peerPublicKeyEncoded)) {
            "Peer public key does not match the transcript."
        }
        require(transcript.publicKeyFor(localRole).contentEquals(localPublicKeyEncoded)) {
            "Local public key does not match the transcript."
        }
        val fingerprint = PairingIdentityCrypto.fingerprint(peerPublicKeyEncoded)
        val existing = records[fingerprint]
        require(existing == null || existing.isRevoked) {
            "This device is already registered."
        }
        val record = TrustedDeviceRecord(
            fingerprint = fingerprint,
            publicKeyEncoded = peerPublicKeyEncoded.copyOf(),
            displayName = displayName.trim(),
            trustedAtMillis = nowMillis
        )
        records[fingerprint] = record
        return record.copy(publicKeyEncoded = record.publicKeyEncoded.copyOf())
    }

    @Synchronized
    fun revoke(fingerprint: String, nowMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Timestamp cannot be negative." }
        val current = records[fingerprint] ?: return false
        if (current.isRevoked) return false
        records[fingerprint] = current.copy(revokedAtMillis = nowMillis)
        return true
    }

    @Synchronized
    fun activeDevices(): List<TrustedDeviceRecord> =
        records.values.filterNot { it.isRevoked }.map {
            it.copy(publicKeyEncoded = it.publicKeyEncoded.copyOf())
        }

    @Synchronized
    fun allRecords(): List<TrustedDeviceRecord> =
        records.values.map { it.copy(publicKeyEncoded = it.publicKeyEncoded.copyOf()) }

    private companion object {
        const val MAX_NAME_LENGTH = 48
    }
}
