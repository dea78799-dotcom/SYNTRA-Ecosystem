package com.syntra.nexus

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityGrantRegistryTest {
    private val local = keyPair()
    private val peer = keyPair()
    private val now = 10_000L
    private val expiry = 120_000L
    private val sessionId = "SSSSSSSSSSSSSSSSSSSSSS"
    private val transcript = PairingTranscript.create(
        requestId = "CCCCCCCCCCCCCCCCCCCCCC",
        expiresAtMillis = expiry,
        initiatorPublicKeyEncoded = local.public.encoded,
        responderPublicKeyEncoded = peer.public.encoded,
        initiatorNonce = ByteArray(32) { (it + 11).toByte() },
        responderNonce = ByteArray(32) { (it + 91).toByte() },
        nowMillis = now
    )

    private data class Setup(
        val session: PairingHandshakeSession,
        val transaction: PairingTransactionSnapshot,
        val trusted: TrustedDeviceRegistry
    )

    private fun setup(): Setup {
        val waiting = PairingHandshakeSession.createForTranscript(
            transcript, local.public.encoded, now
        ).invitationSent(now + 1).peerConnected(now + 2)
            .verifyPeerProofAndAdvance(
                transcript, peer.public.encoded,
                PairingIdentityCrypto.signTranscriptHash(
                    peer.private, transcript.sha256, PairingSignerRole.RESPONDER
                ),
                PairingSignerRole.RESPONDER, now + 3
            ).session
        val session = waiting.approveLocally(now + 4).verifyPeerApprovalAndAdvance(
            transcript, peer.public.encoded,
            PairingApprovalVerifier.createApprovalSignature(
                peer.private, transcript, PairingSignerRole.RESPONDER
            ),
            PairingSignerRole.RESPONDER, now + 5
        ).session

        val hash = transcript.sha256.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val fingerprint = PairingIdentityCrypto.fingerprint(peer.public.encoded)
        val coordinator = PairingTransactionCoordinator()
        check(coordinator.begin(transcript.requestId, expiry, hash, fingerprint, now) ==
            PairingTransactionResult.UPDATED)
        check(coordinator.markTokenRedeemed(now + 1) == PairingTransactionResult.UPDATED)
        val proofSession = PairingHandshakeSession.createForTranscript(
            transcript, local.public.encoded, now
        ).invitationSent(now + 1).peerConnected(now + 2)
            .verifyPeerProofAndAdvance(
                transcript, peer.public.encoded,
                PairingIdentityCrypto.signTranscriptHash(
                    peer.private, transcript.sha256, PairingSignerRole.RESPONDER
                ),
                PairingSignerRole.RESPONDER, now + 3
            ).session
        check(coordinator.markPeerIdentityVerified(proofSession, transcript, now + 3) ==
            PairingTransactionResult.UPDATED)
        val locallyApproved = proofSession.approveLocally(now + 4)
        check(coordinator.markLocalApproval(locallyApproved, transcript, now + 4) ==
            PairingTransactionResult.UPDATED)
        val paired = locallyApproved.verifyPeerApprovalAndAdvance(
            transcript, peer.public.encoded,
            PairingApprovalVerifier.createApprovalSignature(
                peer.private, transcript, PairingSignerRole.RESPONDER
            ),
            PairingSignerRole.RESPONDER, now + 5
        ).session
        check(coordinator.complete(paired, transcript, now + 5) == PairingTransactionResult.UPDATED)

        val trusted = TrustedDeviceRegistry()
        trusted.registerAfterVerifiedPairing(
            session, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "NEXUS peer", now + 6
        )
        return Setup(session, requireNotNull(coordinator.snapshot()), trusted)
    }

    private fun request(
        capability: NexusCapability = NexusCapability.USAGE_SUMMARY_READ,
        id: String = "GGGGGGGGGGGGGGGGGGGGGG",
        issued: Long = now + 7,
        expires: Long = now + 60_000L
    ) = CapabilityGrantRequest(
        grantId = id,
        peerFingerprint = PairingIdentityCrypto.fingerprint(peer.public.encoded),
        pairingRequestId = transcript.requestId,
        transcriptHashHex = transcript.sha256.joinToString("") { "%02x".format(it.toInt() and 0xff) },
        sessionId = sessionId,
        capability = capability,
        issuedAtMillis = issued,
        expiresAtMillis = expires
    )

    private fun signedPair(request: CapabilityGrantRequest) = Pair(
        CapabilityGrantRegistry.signApproval(
            local.private, request, PairingSignerRole.INITIATOR
        ),
        CapabilityGrantRegistry.signApproval(
            peer.private, request, PairingSignerRole.RESPONDER
        )
    )

    @Test
    fun bothRoleBoundApprovalsGrantOnlyTheRequestedCapability() {
        val setup = setup()
        val request = request()
        val signatures = signedPair(request)
        val registry = CapabilityGrantRegistry()
        val grant = registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        )
        assertNotNull(grant)
        assertTrue(registry.mayUse(
            request.grantId, request.capability, sessionId, setup.session,
            transcript, setup.transaction, setup.trusted, now + 8
        ))
        assertFalse(registry.mayUse(
            request.grantId, NexusCapability.SCREEN_SHARE, sessionId, setup.session,
            transcript, setup.transaction, setup.trusted, now + 8
        ))
    }

    @Test
    fun missingOrWrongRoleSignatureCannotIssueGrant() {
        val setup = setup()
        val request = request()
        val localSignature = CapabilityGrantRegistry.signApproval(
            local.private, request, PairingSignerRole.INITIATOR
        )
        val wrongRoleSignature = CapabilityGrantRegistry.signApproval(
            peer.private, request, PairingSignerRole.INITIATOR
        )
        assertNull(CapabilityGrantRegistry().issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            localSignature, wrongRoleSignature, now + 7
        ))
    }

    @Test
    fun grantCannotBeReplayedForAnotherSession() {
        val setup = setup()
        val request = request()
        val signatures = signedPair(request)
        val registry = CapabilityGrantRegistry()
        assertNotNull(registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        ))
        assertFalse(registry.mayUse(
            request.grantId, request.capability, "TTTTTTTTTTTTTTTTTTTTTT",
            setup.session, transcript, setup.transaction, setup.trusted, now + 8
        ))
    }

    @Test
    fun revokedCapabilityGrantIsDenied() {
        val setup = setup()
        val request = request()
        val signatures = signedPair(request)
        val registry = CapabilityGrantRegistry()
        assertNotNull(registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        ))
        assertTrue(registry.revoke(request.grantId, now + 8))
        assertFalse(registry.mayUse(
            request.grantId, request.capability, sessionId, setup.session,
            transcript, setup.transaction, setup.trusted, now + 9
        ))
    }

    @Test
    fun deviceRevocationInvalidatesAnExistingCapabilityGrant() {
        val setup = setup()
        val request = request()
        val signatures = signedPair(request)
        val registry = CapabilityGrantRegistry()
        assertNotNull(registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        ))
        val fingerprint = PairingIdentityCrypto.fingerprint(peer.public.encoded)
        assertTrue(setup.trusted.revoke(fingerprint, now + 8))
        assertFalse(registry.mayUse(
            request.grantId, request.capability, sessionId, setup.session,
            transcript, setup.transaction, setup.trusted, now + 9
        ))
    }

    @Test
    fun expiredGrantCannotBeUsed() {
        val setup = setup()
        val request = request(expires = now + 20)
        val signatures = signedPair(request)
        val registry = CapabilityGrantRegistry()
        assertNotNull(registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        ))
        assertFalse(registry.mayUse(
            request.grantId, request.capability, sessionId, setup.session,
            transcript, setup.transaction, setup.trusted, now + 20
        ))
    }

    @Test
    fun revokedDeviceCannotCreateNewCapabilityGrant() {
        val setup = setup()
        val request = request()
        val signatures = signedPair(request)
        val fingerprint = PairingIdentityCrypto.fingerprint(peer.public.encoded)
        assertTrue(setup.trusted.revoke(fingerprint, now + 7))
        assertNull(CapabilityGrantRegistry().issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 8
        ))
    }

    @Test
    fun consentRequiresVerifiedPeerApprovalAndExplicitLocalAction() {
        val setup = setup()
        val request = request()
        val peerSignature = CapabilityGrantRegistry.signApproval(
            peer.private, request, PairingSignerRole.RESPONDER
        )
        val registry = CapabilityGrantRegistry()
        val consent = CapabilityConsentCoordinator(registry)
        val pending = consent.prepare(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            peerSignature, now + 7
        )
        assertNotNull(pending)

        var localSigningCalls = 0
        val grant = consent.approve(
            requireNotNull(pending), setup.trusted, now + 8
        ) {
            localSigningCalls++
            CapabilityGrantRegistry.signApproval(
                local.private, request, PairingSignerRole.INITIATOR
            )
        }
        assertNotNull(grant)
        assertEquals(1, localSigningCalls)
    }

    @Test
    fun invalidPeerApprovalCannotOpenConsentOrInvokeLocalSigner() {
        val setup = setup()
        val request = request()
        val wrongRoleSignature = CapabilityGrantRegistry.signApproval(
            peer.private, request, PairingSignerRole.INITIATOR
        )
        val consent = CapabilityConsentCoordinator(CapabilityGrantRegistry())
        val pending = consent.prepare(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            wrongRoleSignature, now + 7
        )
        assertNull(pending)
    }

    @Test
    fun expiredPendingConsentDoesNotInvokeLocalSigner() {
        val setup = setup()
        val request = request(expires = now + 20)
        val peerSignature = CapabilityGrantRegistry.signApproval(
            peer.private, request, PairingSignerRole.RESPONDER
        )
        val consent = CapabilityConsentCoordinator(CapabilityGrantRegistry())
        val pending = consent.prepare(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            peerSignature, now + 7
        )
        assertNotNull(pending)

        var localSigningCalls = 0
        val grant = consent.approve(
            requireNotNull(pending), setup.trusted, request.expiresAtMillis
        ) {
            localSigningCalls++
            CapabilityGrantRegistry.signApproval(
                local.private, request, PairingSignerRole.INITIATOR
            )
        }
        assertNull(grant)
        assertEquals(0, localSigningCalls)
    }

    @Test
    fun grantsAndRevocationSurviveRegistryRecreation() {
        val setup = setup()
        val persistence = MemoryCapabilityGrantPersistence()
        val registry = CapabilityGrantRegistry(persistence)
        val request = request()
        val signatures = signedPair(request)

        assertNotNull(registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        ))
        assertEquals(1, CapabilityGrantRegistry(persistence).snapshot().size)

        val restored = CapabilityGrantRegistry(persistence)
        assertTrue(restored.revoke(request.grantId, now + 8))
        assertEquals(now + 8, CapabilityGrantRegistry(persistence).snapshot().single().revokedAtMillis)
    }

    @Test
    fun failedRevocationPersistenceKeepsExistingGrantUnchanged() {
        val setup = setup()
        val persistence = MemoryCapabilityGrantPersistence()
        val registry = CapabilityGrantRegistry(persistence)
        val request = request()
        val signatures = signedPair(request)

        assertNotNull(registry.issue(
            request, transcript, setup.session, setup.transaction, setup.trusted,
            signatures.first, signatures.second, now + 7
        ))
        persistence.failWrites = true

        try {
            registry.revoke(request.grantId, now + 8)
        } catch (_: IllegalStateException) {
            // Expected: failed durable revoke must not mutate memory.
        }

        assertNull(registry.snapshot().single().revokedAtMillis)
    }

    @Test
    fun failedPersistenceDoesNotMutateInMemoryGrantRegistry() {
        val setup = setup()
        val persistence = MemoryCapabilityGrantPersistence().apply { failWrites = true }
        val registry = CapabilityGrantRegistry(persistence)
        val request = request()
        val signatures = signedPair(request)

        try {
            registry.issue(
                request, transcript, setup.session, setup.transaction, setup.trusted,
                signatures.first, signatures.second, now + 7
            )
        } catch (_: IllegalStateException) {
            // Expected: durable commit fails closed.
        }

        assertTrue(registry.snapshot().isEmpty())
    }

    private class MemoryCapabilityGrantPersistence : CapabilityGrantPersistence {
        private var values = emptyList<CapabilityGrant>()
        var failWrites = false

        override fun load(): List<CapabilityGrant> = values.map {
            CapabilityGrant(
                it.request,
                it.localSignatureCopy(),
                it.peerSignatureCopy(),
                it.revokedAtMillis
            )
        }

        override fun save(grants: List<CapabilityGrant>) {
            check(!failWrites) { "Simulated durable storage failure." }
            values = grants.map {
                CapabilityGrant(
                    it.request,
                    it.localSignatureCopy(),
                    it.peerSignatureCopy(),
                    it.revokedAtMillis
                )
            }
        }
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
