package com.syntra.nexus

import java.security.MessageDigest
import java.util.Base64

/**
 * Process-local, synchronized single-use invitation registry.
 *
 * Only a SHA-256 fingerprint of each high-entropy token is retained.
 * This registry is intentionally in-memory: it does not provide persistence
 * across process death or replay protection across multiple devices.
 */
class OneTimePairingTokenRegistry(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
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
    }

    /**
     * Registers an invitation before it is shown or distributed.
     * Returns false if the same token fingerprint is already registered.
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

        pruneExpired(nowMillis)
        if (entries.containsKey(fingerprint)) return false
        check(entries.size < maxEntries) {
            "Pairing token registry is full; wait for invitations to expire."
        }

        entries[fingerprint] = Entry(invitation.expiresAtMillis)
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
        entry.state = State.REVOKED
        return true
    }

    @Synchronized
    fun registeredCount(): Int = entries.size

    private fun pruneExpired(nowMillis: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMillis >= iterator.next().value.expiresAtMillis) {
                iterator.remove()
            }
        }
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
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256
        private const val HARD_MAX_ENTRIES = 4096
        private const val TOKEN_BYTES = 32
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
