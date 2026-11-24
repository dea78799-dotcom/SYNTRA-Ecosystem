package com.syntra.nexus

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDurableReplayGuardTest {
    private class MemoryReplayStore : NexusReplayPersistence {
        var records: List<NexusReplaySessionRecord> = emptyList()
        var failWrites = false
        override fun load() = records
        override fun save(records: List<NexusReplaySessionRecord>) {
            if (failWrites) error("simulated disk failure")
            this.records = records.map { it.copy(seenNonceHashes = it.seenNonceHashes.toList()) }
        }
    }

    private val initiator = ecKeyPair()
    private val responder = ecKeyPair()
    private val transcript = PairingTranscript.create(
        "AAAAAAAAAAAAAAAAAAAAAA", EXPIRY,
        initiator.public.encoded, responder.public.encoded,
        ByteArray(32) { (it + 1).toByte() },
        ByteArray(32) { (it + 65).toByte() }, NOW
    )

    @Test fun acceptedFrameSurvivesGuardRecreationAndRejectsReplay() {
        val store = MemoryReplayStore()
        val firstGuard = NexusHandshakeReplayGuard(persistence = store)
        val frame = signedFrame(0)
        assertEquals(NexusReplayDecision.ACCEPTED, firstGuard.accept(frame, NOW + 1))
        val restored = NexusHandshakeReplayGuard(persistence = store)
        assertEquals(NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER, restored.accept(frame, NOW + 2))
        assertEquals(NexusReplayDecision.ACCEPTED, restored.accept(signedFrame(1), NOW + 3))
    }

    @Test fun storageFailureDoesNotAdvanceInMemoryState() {
        val store = MemoryReplayStore()
        val guard = NexusHandshakeReplayGuard(persistence = store)
        store.failWrites = true
        assertEquals(NexusReplayDecision.PERSISTENCE_FAILURE, guard.accept(signedFrame(0), NOW + 1))
        store.failWrites = false
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(signedFrame(0), NOW + 2))
    }

    @Test fun malformedPersistedLedgerIsRejectedAtStartup() {
        val store = MemoryReplayStore()
        store.records = listOf(NexusReplaySessionRecord(
            "bad", "f".repeat(64), EXPIRY, "a".repeat(64), 0, listOf("b".repeat(64))
        ))
        assertFalse(runCatching { NexusHandshakeReplayGuard(persistence = store) }.isSuccess)
    }

    @Test fun expiredSessionsAreRemovedFromDurableSnapshot() {
        val store = MemoryReplayStore()
        val guard = NexusHandshakeReplayGuard(maxSessions = 1, persistence = store)
        val shortExpiry = NOW + 30_000L
        val earlyTranscript = PairingTranscript.create(
            "AAAAAAAAAAAAAAAAAAAAAA", shortExpiry,
            initiator.public.encoded, responder.public.encoded,
            ByteArray(32) { (it + 3).toByte() },
            ByteArray(32) { (it + 67).toByte() }, NOW
        )
        val earlyFrame = NexusHandshakeFrame.createSigned(
            NexusHandshakeKind.IDENTITY_PROOF, earlyTranscript,
            PairingSignerRole.RESPONDER, 0, responder.private
        )
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(earlyFrame, NOW + 1))
        val nextTranscript = PairingTranscript.create(
            "BBBBBBBBBBBBBBBBBBBBBB", EXPIRY,
            initiator.public.encoded, responder.public.encoded,
            ByteArray(32) { (it + 2).toByte() },
            ByteArray(32) { (it + 66).toByte() }, NOW
        )
        val later = NexusHandshakeFrame.createSigned(
            NexusHandshakeKind.IDENTITY_PROOF, nextTranscript,
            PairingSignerRole.RESPONDER, 0, responder.private
        )
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(later, shortExpiry))
        assertEquals(1, store.records.size)
        assertEquals(nextTranscript.requestId, store.records.single().requestId)
    }

    @Test fun sequenceAndNonceHistoryAreRestoredTogether() {
        val store = MemoryReplayStore()
        val guard = NexusHandshakeReplayGuard(persistence = store)
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(signedFrame(0), NOW + 1))
        assertEquals(NexusReplayDecision.ACCEPTED, guard.accept(signedFrame(1), NOW + 2))
        val restored = NexusHandshakeReplayGuard(persistence = store)
        assertEquals(NexusReplayDecision.REPLAYED_OR_OUT_OF_ORDER, restored.accept(signedFrame(1), NOW + 3))
        assertEquals(NexusReplayDecision.ACCEPTED, restored.accept(signedFrame(2), NOW + 4))
        assertEquals(3, store.records.single().seenNonceHashes.size)
    }

    @Test fun emptyStoreRestoresAnEmptyGuard() {
        val restored = NexusHandshakeReplayGuard(persistence = MemoryReplayStore())
        assertTrue(restored.accept(signedFrame(0), NOW + 1) == NexusReplayDecision.ACCEPTED)
    }

    private fun signedFrame(sequence: Long) = NexusHandshakeFrame.createSigned(
        NexusHandshakeKind.IDENTITY_PROOF, transcript,
        PairingSignerRole.RESPONDER, sequence, responder.private
    )

    private fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private companion object {
        const val NOW = 20_000L
        const val EXPIRY = 140_000L
    }
}
