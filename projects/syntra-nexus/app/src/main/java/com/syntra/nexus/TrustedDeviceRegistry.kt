package com.syntra.nexus

/**
 * Local trust record. Public-key bytes are defensively copied at registry boundaries.
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

interface TrustedDevicePersistence {
    fun load(): List<TrustedDeviceRecord>
    fun save(records: List<TrustedDeviceRecord>)
}

/**
 * Registry accepts peers only from a completed transcript-bound handshake
 * with both approvals. Persistence is committed before in-memory mutation.
 */
class TrustedDeviceRegistry(
    private val persistence: TrustedDevicePersistence? = null
) {
    private val records = linkedMapOf<String, TrustedDeviceRecord>()

    init {
        val loaded = persistence?.load().orEmpty()
        require(loaded.size <= MAX_RECORDS) { "Trusted-device registry exceeds capacity." }
        loaded.forEach { record ->
            validateRecord(record)
            require(records.put(record.fingerprint, copyRecord(record)) == null) {
                "Trusted-device registry contains duplicate fingerprints."
            }
        }
    }

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
        require(existing != null || records.size < MAX_RECORDS) {
            "Trusted-device registry is full."
        }
        val record = TrustedDeviceRecord(
            fingerprint = fingerprint,
            publicKeyEncoded = peerPublicKeyEncoded.copyOf(),
            displayName = displayName.trim(),
            trustedAtMillis = nowMillis
        )
        commit(records + (fingerprint to record))
        return copyRecord(record)
    }

    @Synchronized
    fun revoke(fingerprint: String, nowMillis: Long): Boolean {
        require(nowMillis >= 0L) { "Timestamp cannot be negative." }
        val current = records[fingerprint] ?: return false
        if (current.isRevoked) return false
        val revoked = current.copy(revokedAtMillis = nowMillis)
        commit(records + (fingerprint to revoked))
        return true
    }

    @Synchronized
    fun renameDevice(fingerprint: String, newDisplayName: String): Boolean {
        require(newDisplayName.isNotBlank() && newDisplayName.trim().length <= MAX_NAME_LENGTH) {
            "Device name must contain 1 to $MAX_NAME_LENGTH characters."
        }
        val current = records[fingerprint] ?: return false
        val renamed = current.copy(displayName = newDisplayName.trim())
        commit(records + (fingerprint to renamed))
        return true
    }

    @Synchronized
    fun activeDevices(): List<TrustedDeviceRecord> =
        records.values.filterNot { it.isRevoked }.map(::copyRecord)

    @Synchronized
    fun allRecords(): List<TrustedDeviceRecord> = records.values.map(::copyRecord)

    private fun commit(next: Map<String, TrustedDeviceRecord>) {
        persistence?.save(next.values.map(::copyRecord))
        records.clear()
        next.forEach { (fingerprint, record) -> records[fingerprint] = copyRecord(record) }
    }

    private fun validateRecord(record: TrustedDeviceRecord) {
        require(record.fingerprint.matches(Regex("^[a-f0-9]{64}$"))) {
            "Stored fingerprint has an invalid format."
        }
        require(record.publicKeyEncoded.isNotEmpty() && record.publicKeyEncoded.size <= 512) {
            "Stored public key has an invalid length."
        }
        require(PairingIdentityCrypto.fingerprint(record.publicKeyEncoded) == record.fingerprint) {
            "Stored public key does not match its fingerprint."
        }
        require(record.displayName.isNotBlank() && record.displayName.length <= MAX_NAME_LENGTH) {
            "Stored device name is invalid."
        }
        require(record.trustedAtMillis >= 0L &&
            (record.revokedAtMillis == null || record.revokedAtMillis >= record.trustedAtMillis)) {
            "Stored timestamps are invalid."
        }
    }

    private fun copyRecord(record: TrustedDeviceRecord) =
        record.copy(publicKeyEncoded = record.publicKeyEncoded.copyOf())

    private companion object {
        const val MAX_NAME_LENGTH = 48
        const val MAX_RECORDS = 128
    }
}
