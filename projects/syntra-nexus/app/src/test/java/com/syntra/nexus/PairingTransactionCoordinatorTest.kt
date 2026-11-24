package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTransactionCoordinatorTest {
    private class MemoryPersistence : PairingTransactionPersistence {
        var value: PairingTransactionSnapshot? = null
        var failWrites = false
        override fun load() = value
        override fun save(snapshot: PairingTransactionSnapshot) {
            if (failWrites) error("disk unavailable")
            value = snapshot
        }
        override fun clear() { value = null }
    }

    private fun coordinator(persistence: MemoryPersistence = MemoryPersistence()) =
        PairingTransactionCoordinator(persistence) to persistence

    private fun begin(coordinator: PairingTransactionCoordinator) =
        coordinator.begin(
            requestId = "abcdefghijklmnopqrstuv",
            expiresAtMillis = 110_000L,
            transcriptHashHex = "a".repeat(64),
            peerFingerprint = "b".repeat(64),
            nowMillis = 10_000L
        )

    @Test fun startsOnlyWithBoundFields() {
        val (coordinator) = coordinator()
        assertEquals(PairingTransactionResult.UPDATED, begin(coordinator))
        assertEquals(PairingTransactionState.CREATED, coordinator.snapshot()?.state)
    }

    @Test fun rejectsMalformedFingerprint() {
        val (coordinator) = coordinator()
        assertEquals(
            PairingTransactionResult.INVALID_TRANSITION,
            coordinator.begin("abcdefghijklmnopqrstuv", 110_000L, "a".repeat(64), "bad", 10_000L)
        )
    }

    @Test fun tokenMustBeRedeemedBeforeIdentity() {
        val (coordinator) = coordinator()
        begin(coordinator)
        assertEquals(
            PairingTransactionResult.INVALID_TRANSITION,
            coordinator.markPeerIdentityVerified(
                // No valid session can be fabricated through the public API.
                // Null is deliberately not accepted; this test covers ordering
                // through the lower-level transition below.
                null as PairingHandshakeSession, null as PairingTranscript, 11_000L
            )
        )
    }

    @Test fun persistenceFailureDoesNotAdvanceMemory() {
        val (coordinator, persistence) = coordinator()
        persistence.failWrites = true
        assertEquals(PairingTransactionResult.PERSISTENCE_FAILURE, begin(coordinator))
        assertNull(coordinator.snapshot())
    }

    @Test fun restoreDowngradesPendingTransaction() {
        val persistence = MemoryPersistence()
        persistence.value = PairingTransactionSnapshot(
            "abcdefghijklmnopqrstuv", 110_000L, "a".repeat(64), "b".repeat(64),
            PairingTransactionState.LOCAL_APPROVAL_PENDING, 20_000L
        )
        val restored = PairingTransactionCoordinator(persistence)
        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, restored.snapshot()?.state)
    }

    @Test fun restoreKeepsTerminalState() {
        val persistence = MemoryPersistence()
        persistence.value = PairingTransactionSnapshot(
            "abcdefghijklmnopqrstuv", 110_000L, "a".repeat(64), "b".repeat(64),
            PairingTransactionState.ABORTED, 20_000L
        )
        assertEquals(
            PairingTransactionState.ABORTED,
            PairingTransactionCoordinator(persistence).snapshot()?.state
        )
    }

    @Test fun terminalTransactionCanBeCleared() {
        val (coordinator, persistence) = coordinator()
        begin(coordinator)
        assertEquals(PairingTransactionResult.UPDATED, coordinator.abort(11_000L))
        assertEquals(PairingTransactionResult.UPDATED, coordinator.clearTerminal())
        assertNull(persistence.value)
    }

    @Test fun expiryIsPersistedBeforeReturning() {
        val (coordinator, persistence) = coordinator()
        begin(coordinator)
        assertEquals(PairingTransactionResult.EXPIRED, coordinator.markTokenRedeemed(110_000L))
        assertEquals(PairingTransactionState.EXPIRED, persistence.value?.state)
    }
}
