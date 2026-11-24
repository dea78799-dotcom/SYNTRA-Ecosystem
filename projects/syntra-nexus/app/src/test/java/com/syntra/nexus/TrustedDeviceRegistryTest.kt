package com.syntra.nexus

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedDeviceRegistryTest {
    private val local = createKeyPair()
    private val peer = createKeyPair()
    private val transcript = PairingTranscript.create(
        requestId = REQUEST_ID,
        expiresAtMillis = EXPIRY,
        initiatorPublicKeyEncoded = local.public.encoded,
        responderPublicKeyEncoded = peer.public.encoded,
        initiatorNonce = ByteArray(32) { (it + 1).toByte() },
        responderNonce = ByteArray(32) { (it + 65).toByte() },
        nowMillis = NOW
    )

    @Test
    fun registersOnlyAfterBothApprovalsAndReturnsDefensiveCopies() {
        val registry = TrustedDeviceRegistry()
        val record = registry.registerAfterVerifiedPairing(
            pairedSession(), transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "My phone", NOW + 6
        )
        assertEquals(PairingIdentityCrypto.fingerprint(peer.public.encoded), record.fingerprint)
        assertEquals("My phone", record.displayName)
        assertEquals(1, registry.activeDevices().size)
        val exposed = registry.activeDevices().single().publicKeyEncoded
        exposed[0] = (exposed[0].toInt() xor 0x7f).toByte()
        assertTrue(registry.activeDevices().single().publicKeyEncoded.contentEquals(peer.public.encoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHandshakeWithoutBothApprovals() {
        val session = session().invitationSent(NOW + 1).peerConnected(NOW + 2)
            .verifyPeerProofAndAdvance(
                transcript, peer.public.encoded, sign(PairingSignerRole.RESPONDER),
                PairingSignerRole.RESPONDER, NOW + 3
            ).session.approveLocally(NOW + 4)
        TrustedDeviceRegistry().registerAfterVerifiedPairing(
            session, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", NOW + 5
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPeerKeyNotBoundToTranscript() {
        val other = createKeyPair()
        TrustedDeviceRegistry().registerAfterVerifiedPairing(
            pairedSession(), transcript, local.public.encoded, other.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", NOW + 6
        )
    }

    @Test
    fun revocationRemovesDeviceFromActiveListAndCannotRepeat() {
        val registry = TrustedDeviceRegistry()
        val record = registry.registerAfterVerifiedPairing(
            pairedSession(), transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", NOW + 6
        )
        assertTrue(registry.revoke(record.fingerprint, NOW + 7))
        assertFalse(registry.revoke(record.fingerprint, NOW + 8))
        assertTrue(registry.activeDevices().isEmpty())
        assertTrue(registry.allRecords().single().isRevoked)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDifferentTranscriptEvenWhenSessionIsPaired() {
        val otherTranscript = PairingTranscript.create(
            requestId = "BBBBBBBBBBBBBBBBBBBBBB",
            expiresAtMillis = EXPIRY,
            initiatorPublicKeyEncoded = local.public.encoded,
            responderPublicKeyEncoded = peer.public.encoded,
            initiatorNonce = ByteArray(32) { (it + 2).toByte() },
            responderNonce = ByteArray(32) { (it + 66).toByte() },
            nowMillis = NOW
        )
        TrustedDeviceRegistry().registerAfterVerifiedPairing(
            pairedSession(), otherTranscript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", NOW + 6
        )
    }

    @Test
    fun restoresPersistedDeviceAndRevocationAfterRegistryRecreation() {
        val persistence = FakeTrustedDevicePersistence()
        val first = TrustedDeviceRegistry(persistence)
        val record = first.registerAfterVerifiedPairing(
            pairedSession(), transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Persisted peer", NOW + 6
        )
        val restored = TrustedDeviceRegistry(persistence)
        assertEquals(record.fingerprint, restored.activeDevices().single().fingerprint)
        assertTrue(restored.revoke(record.fingerprint, NOW + 7))
        val afterRevoke = TrustedDeviceRegistry(persistence)
        assertTrue(afterRevoke.activeDevices().isEmpty())
        assertTrue(afterRevoke.allRecords().single().isRevoked)
    }

    @Test
    fun renamePersistsAcrossRegistryRecreation() {
        val persistence = FakeTrustedDevicePersistence()
        val first = TrustedDeviceRegistry(persistence)
        val record = first.registerAfterVerifiedPairing(
            pairedSession(), transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Old name", NOW + 6
        )

        assertTrue(first.renameDevice(record.fingerprint, "Living room phone"))
        val restored = TrustedDeviceRegistry(persistence)

        assertEquals("Living room phone", restored.activeDevices().single().displayName)
        assertEquals(record.fingerprint, restored.activeDevices().single().fingerprint)
    }

    @Test
    fun renamingRevokedDeviceKeepsRevocationState() {
        val persistence = FakeTrustedDevicePersistence()
        val registry = TrustedDeviceRegistry(persistence)
        val record = registry.registerAfterVerifiedPairing(
            pairedSession(), transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Old name", NOW + 6
        )
        assertTrue(registry.revoke(record.fingerprint, NOW + 7))

        assertTrue(registry.renameDevice(record.fingerprint, "Archived phone"))
        val restored = TrustedDeviceRegistry(persistence)

        assertTrue(restored.activeDevices().isEmpty())
        assertEquals("Archived phone", restored.allRecords().single().displayName)
        assertTrue(restored.allRecords().single().isRevoked)
    }

    @Test
    fun renameReturnsFalseForUnknownFingerprint() {
        assertFalse(TrustedDeviceRegistry().renameDevice("0".repeat(64), "Unknown"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun renameRejectsBlankName() {
        TrustedDeviceRegistry().renameDevice("0".repeat(64), "   ")
    }

    @Test
    fun persistenceFailureDoesNotMutateInMemoryRegistry() {
        val persistence = FakeTrustedDevicePersistence().apply { failWrites = true }
        val registry = TrustedDeviceRegistry(persistence)
        try {
            registry.registerAfterVerifiedPairing(
                pairedSession(), transcript, local.public.encoded, peer.public.encoded,
                PairingSignerRole.RESPONDER, "Peer", NOW + 6
            )
            throw AssertionError("Expected persistence failure.")
        } catch (_: IllegalStateException) {
            assertTrue(registry.activeDevices().isEmpty())
            assertTrue(persistence.records.isEmpty())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPersistedFingerprintThatDoesNotMatchPublicKey() {
        val invalid = TrustedDeviceRecord(
            fingerprint = "0".repeat(64),
            publicKeyEncoded = peer.public.encoded,
            displayName = "Invalid",
            trustedAtMillis = NOW
        )
        TrustedDeviceRegistry(FakeTrustedDevicePersistence(mutableListOf(invalid)))
    }

    private class FakeTrustedDevicePersistence(
        val records: MutableList<TrustedDeviceRecord> = mutableListOf()
    ) : TrustedDevicePersistence {
        var failWrites: Boolean = false

        override fun load(): List<TrustedDeviceRecord> =
            records.map { it.copy(publicKeyEncoded = it.publicKeyEncoded.copyOf()) }

        override fun save(records: List<TrustedDeviceRecord>) {
            if (failWrites) throw IllegalStateException("Simulated storage failure.")
            this.records.clear()
            this.records.addAll(records.map { it.copy(publicKeyEncoded = it.publicKeyEncoded.copyOf()) })
        }
    }

    private fun pairedSession(): PairingHandshakeSession {
        val verified = session().invitationSent(NOW + 1).peerConnected(NOW + 2)
            .verifyPeerProofAndAdvance(
                transcript, peer.public.encoded, sign(PairingSignerRole.RESPONDER),
                PairingSignerRole.RESPONDER, NOW + 3
            ).session.approveLocally(NOW + 4)
        return verified.verifyPeerApprovalAndAdvance(
            transcript, peer.public.encoded, PairingApprovalVerifier.createApprovalSignature(
                peer.private, transcript, PairingSignerRole.RESPONDER
            ), PairingSignerRole.RESPONDER, NOW + 5
        ).session
    }

    private fun session() = PairingHandshakeSession.createForTranscript(
        transcript, local.public.encoded, NOW
    )

    private fun sign(role: PairingSignerRole) =
        PairingIdentityCrypto.signTranscriptHash(peer.private, transcript.sha256, role)

    private fun createKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private companion object {
        const val REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val NOW = 10_000L
        const val EXPIRY = 130_000L
    }
}
