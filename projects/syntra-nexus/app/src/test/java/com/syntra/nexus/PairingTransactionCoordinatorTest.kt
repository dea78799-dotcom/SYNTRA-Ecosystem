package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingTransactionCoordinatorTest {
    private class MemoryPersistence : PairingTransactionPersistence {
        var value: PairingTransactionSnapshot? = null
        var failWrites = false
        var failClear = false

        override fun load() = value

        override fun save(snapshot: PairingTransactionSnapshot) {
            if (failWrites) error("disk unavailable")
            value = snapshot
        }

        override fun clear() {
            if (failClear) error("disk unavailable")
            value = null
        }
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

    private fun stored(state: PairingTransactionState) = PairingTransactionSnapshot(
        "abcdefghijklmnopqrstuv", 110_000L, "a".repeat(64), "b".repeat(64),
        state, 20_000L
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

    @Test fun tokenCannotBeRedeemedWithoutActiveTransaction() {
        val (coordinator) = coordinator()
        assertEquals(
            PairingTransactionResult.INVALID_TRANSITION,
            coordinator.markTokenRedeemed(11_000L)
        )
    }

    @Test fun persistenceFailureDoesNotAdvanceMemory() {
        val (coordinator, persistence) = coordinator()
        persistence.failWrites = true
        assertEquals(PairingTransactionResult.PERSISTENCE_FAILURE, begin(coordinator))
        assertNull(coordinator.snapshot())
    }

    @Test fun restoreDowngradesPendingTransactionAndPersistsRecoveryState() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.LOCAL_APPROVAL_PENDING)

        val restored = PairingTransactionCoordinator(persistence)

        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, restored.snapshot()?.state)
        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, persistence.value?.state)
    }

    @Test fun restoreDowngradesPreviouslyPairedTransaction() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.PAIRED)

        val restored = PairingTransactionCoordinator(persistence)

        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, restored.snapshot()?.state)
        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, persistence.value?.state)
    }

    @Test fun restoringRecoveryRequiredIsIdempotent() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.RECOVERY_REQUIRED)

        val restored = PairingTransactionCoordinator(persistence)

        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, restored.snapshot()?.state)
        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, persistence.value?.state)
    }

    @Test fun restoreFailsClosedWhenRecoveryDowngradeCannotBePersisted() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.PAIRED)
        persistence.failWrites = true

        assertThrows(IllegalStateException::class.java) {
            PairingTransactionCoordinator(persistence)
        }
        assertEquals(PairingTransactionState.PAIRED, persistence.value?.state)
    }

    @Test fun restoreRejectsInvalidStoredFields() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.CREATED).copy(peerFingerprint = "invalid")

        assertThrows(IllegalArgumentException::class.java) {
            PairingTransactionCoordinator(persistence)
        }
    }

    @Test fun restoreKeepsTerminalState() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.ABORTED)

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

    @Test fun recoveryRequiredCanBeExplicitlyDiscarded() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.RECOVERY_REQUIRED)
        val coordinator = PairingTransactionCoordinator(persistence)

        assertEquals(PairingTransactionResult.UPDATED, coordinator.clearTerminal())
        assertNull(coordinator.snapshot())
        assertNull(persistence.value)
    }

    @Test fun clearFailurePreservesRecoverySnapshot() {
        val persistence = MemoryPersistence()
        persistence.value = stored(PairingTransactionState.RECOVERY_REQUIRED)
        val coordinator = PairingTransactionCoordinator(persistence)
        persistence.failClear = true

        assertEquals(PairingTransactionResult.PERSISTENCE_FAILURE, coordinator.clearTerminal())
        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, coordinator.snapshot()?.state)
        assertEquals(PairingTransactionState.RECOVERY_REQUIRED, persistence.value?.state)
    }

    @Test fun expiryIsPersistedBeforeReturning() {
        val (coordinator, persistence) = coordinator()
        begin(coordinator)
        assertEquals(PairingTransactionResult.EXPIRED, coordinator.markTokenRedeemed(110_000L))
        assertEquals(PairingTransactionState.EXPIRED, persistence.value?.state)
    }

    @Test fun expiryPersistenceFailureDoesNotAdvanceMemory() {
        val (coordinator, persistence) = coordinator()
        begin(coordinator)
        persistence.failWrites = true

        assertEquals(
            PairingTransactionResult.PERSISTENCE_FAILURE,
            coordinator.markTokenRedeemed(110_000L)
        )
        assertEquals(PairingTransactionState.CREATED, coordinator.snapshot()?.state)
    }
}
