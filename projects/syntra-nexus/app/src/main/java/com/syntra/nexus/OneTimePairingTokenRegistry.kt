package com.syntra.nexus

import java.security.MessageDigest
import java.util.Base64

/**
 * Process-local, synchronized single-use invitation registry.
 *
 * Only a SHA-256 fingerprint of each high-entropy token is retained.
 * Entries remain as tombstones to prevent expiry-extension re-registration.
 * An optional persistence adapter stores records; the Android implementation
 * encrypts them with an Android Keystore AES-GCM key. Without persistence,
 * this registry is process-local. It never protects against cross-device replay.
 */
class OneTimePairingTokenRegistry(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val persistence: PairingTokenPersistence? = null
) {
    private enum class State {
        ACTIVE,
        REDEEMED,
        REVOKED
    }

    private data class Entry(
        val expiresAtMillis: Long,
        var state: State = State.ACTIVE
    )

    enum class RedemptionResult {
        REDEEMED,
        ALREADY_REDEEMED,
        EXPIRED,
        REVOKED,
        UNKNOWN_TOKEN,
        INVALID_TOKEN
    }

    private val entries = LinkedHashMap<String, Entry>()

    init {
        require(maxEntries in 1..HARD_MAX_ENTRIES) {
            "Registry capacity must be between 1 and $HARD_MAX_ENTRIES."
        }
        persistence?.load()?.forEach { record ->
            require(FINGERPRINT_PATTERN.matches(record.fingerprint)) {
                "Persisted token fingerprint is malformed."
            }
            val state = State.valueOf(record.state)
            require(entries.put(record.fingerprint, Entry(record.expiresAtMillis, state)) == null) {
                "Persisted registry contains duplicate fingerprints."
            }
        }
        require(entries.size <= maxEntries) { "Persisted registry exceeds configured capacity." }
    }

    /**
     * Registers an invitation before it is shown or distributed.
     * Returns false if this token fingerprint has ever been registered here.
     */
    @Synchronized
    fun register(
        invitation: ParsedPairingInvitation,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        require(invitation.expiresAtMillis > nowMillis) {
            "Cannot register an expired invitation."
        }
        val latestAllowedExpiry =
            if (nowMillis > Long.MAX_VALUE - PairingRequest.DEFAULT_TTL_MILLIS) {
                Long.MAX_VALUE
            } else {
                nowMillis + PairingRequest.DEFAULT_TTL_MILLIS
            }
        require(invitation.expiresAtMillis <= latestAllowedExpiry) {
            "Invitation lifetime exceeds two minutes."
        }

        val fingerprint = tokenFingerprint(invitation.token)
            ?: throw IllegalArgumentException("Invitation token is malformed.")

        if (entries.containsKey(fingerprint)) return false
        check(entries.size < maxEntries) {
            "Pairing token registry is full; create a new registry in a new app session."
        }

        val entry = Entry(invitation.expiresAtMillis)
        persistWith(fingerprint, entry)
        entries[fingerprint] = entry
        return true
    }

    /**
     * Atomically redeems a token. At most one concurrent caller can receive
     * REDEEMED for a registered token.
     */
    @Synchronized
    fun redeem(
        token: String,
        nowMillis: Long = System.currentTimeMillis()
    ): RedemptionResult {
        val fingerprint = tokenFingerprint(token) ?: return RedemptionResult.INVALID_TOKEN
        val entry = entries[fingerprint] ?: return RedemptionResult.UNKNOWN_TOKEN

        if (nowMillis >= entry.expiresAtMillis) return RedemptionResult.EXPIRED

        return when (entry.state) {
            State.ACTIVE -> {
                persistWith(fingerprint, entry.copy(state = State.REDEEMED))
                entry.state = State.REDEEMED
                RedemptionResult.REDEEMED
            }
            State.REDEEMED -> RedemptionResult.ALREADY_REDEEMED
            State.REVOKED -> RedemptionResult.REVOKED
        }
    }

    /** Revokes an active invitation in this process-local registry. */
    @Synchronized
    fun revoke(
        token: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val fingerprint = tokenFingerprint(token) ?: return false
        val entry = entries[fingerprint] ?: return false
        if (nowMillis >= entry.expiresAtMillis || entry.state != State.ACTIVE) return false
        persistWith(fingerprint, entry.copy(state = State.REVOKED))
        entry.state = State.REVOKED
        return true
    }

    @Synchronized
    fun registeredCount(): Int = entries.size

    private fun persistWith(fingerprint: String, replacement: Entry) {
        val store = persistence ?: return
        val records = entries.map { (key, value) ->
            val selected = if (key == fingerprint) replacement else value
            PairingTokenRecord(key, selected.expiresAtMillis, selected.state.name)
        }.toMutableList()
        if (fingerprint !in entries) {
            records += PairingTokenRecord(fingerprint, replacement.expiresAtMillis, replacement.state.name)
        }
        store.save(records)
    }

    private fun tokenFingerprint(token: String): String? {
        if (!TOKEN_PATTERN.matches(token)) return null
        val decoded = try {
            Base64.getUrlDecoder().decode(token)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (decoded.size != TOKEN_BYTES) return null
        val canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
        if (canonical != token) return null

        val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 1024
        private const val HARD_MAX_ENTRIES = 4096
        private const val TOKEN_BYTES = 32
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
        private val FINGERPRINT_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}
