package com.syntra.nexus

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedDeviceAuthorizationTest {
    private val local = keyPair()
    private val peer = keyPair()
    private val now = 10_000L
    private val expiry = 120_000L
    private val transcript = createTranscript("CCCCCCCCCCCCCCCCCCCCCC", expiry)

    private fun createTranscript(requestId: String, expires: Long) = PairingTranscript.create(
        requestId = requestId,
        expiresAtMillis = expires,
        initiatorPublicKeyEncoded = local.public.encoded,
        responderPublicKeyEncoded = peer.public.encoded,
        initiatorNonce = ByteArray(32) { (it + 11).toByte() },
        responderNonce = ByteArray(32) { (it + 91).toByte() },
        nowMillis = now
    )

    private fun pairedSessionFor(value: PairingTranscript): PairingHandshakeSession {
        val waiting = PairingHandshakeSession.createForTranscript(
            value, local.public.encoded, now
        ).invitationSent(now + 1).peerConnected(now + 2)
            .verifyPeerProofAndAdvance(
                value, peer.public.encoded,
                PairingIdentityCrypto.signTranscriptHash(
                    peer.private, value.sha256, PairingSignerRole.RESPONDER
                ),
                PairingSignerRole.RESPONDER, now + 3
            ).session.approveLocally(now + 4)
        return waiting.verifyPeerApprovalAndAdvance(
            value, peer.public.encoded,
            PairingApprovalVerifier.createApprovalSignature(
                peer.private, value, PairingSignerRole.RESPONDER
            ),
            PairingSignerRole.RESPONDER, now + 5
        ).session
    }

    private fun pairedTransactionFor(value: PairingTranscript): PairingTransactionSnapshot {
        val hash = value.sha256.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val fingerprint = PairingIdentityCrypto.fingerprint(
            value.publicKeyFor(PairingSignerRole.RESPONDER)
        )
        val coordinator = PairingTransactionCoordinator()
        check(coordinator.begin(value.requestId, value.expiresAtMillis, hash, fingerprint, now) ==
            PairingTransactionResult.UPDATED)
        check(coordinator.markTokenRedeemed(now + 1) == PairingTransactionResult.UPDATED)
        val proofSession = PairingHandshakeSession.createForTranscript(
            value, local.public.encoded, now
        ).invitationSent(now + 1).peerConnected(now + 2)
            .verifyPeerProofAndAdvance(
                value, peer.public.encoded,
                PairingIdentityCrypto.signTranscriptHash(
                    peer.private, value.sha256, PairingSignerRole.RESPONDER
                ),
                PairingSignerRole.RESPONDER, now + 3
            ).session
        check(coordinator.markPeerIdentityVerified(proofSession, value, now + 3) ==
            PairingTransactionResult.UPDATED)
        val locallyApproved = proofSession.approveLocally(now + 4)
        check(coordinator.markLocalApproval(locallyApproved, value, now + 4) ==
            PairingTransactionResult.UPDATED)
        val paired = locallyApproved.verifyPeerApprovalAndAdvance(
            value, peer.public.encoded,
            PairingApprovalVerifier.createApprovalSignature(
                peer.private, value, PairingSignerRole.RESPONDER
            ),
            PairingSignerRole.RESPONDER, now + 5
        ).session
        check(coordinator.complete(paired, value, now + 5) == PairingTransactionResult.UPDATED)
        return requireNotNull(coordinator.snapshot())
    }

    @Test
    fun authorizesOnlyExactCurrentPairedTrustProvenance() {
        val registry = TrustedDeviceRegistry()
        val session = pairedSessionFor(transcript)
        registry.registerAfterVerifiedPairing(
            session, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", now + 6
        )
        assertTrue(TrustedDeviceAuthorization.mayAuthorizeNewSession(
            session, transcript, pairedTransactionFor(transcript), registry, now + 7
        ))
    }

    @Test
    fun revokedDeviceCannotAuthorizeAnExistingPairedSnapshot() {
        val registry = TrustedDeviceRegistry()
        val session = pairedSessionFor(transcript)
        val record = registry.registerAfterVerifiedPairing(
            session, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", now + 6
        )
        val transaction = pairedTransactionFor(transcript)
        assertTrue(registry.revoke(record.fingerprint, now + 7))
        assertFalse(TrustedDeviceAuthorization.mayAuthorizeNewSession(
            session, transcript, transaction, registry, now + 8
        ))
    }

    @Test
    fun oldTranscriptCannotAuthorizeAfterSameKeyIsPairedAgain() {
        val registry = TrustedDeviceRegistry()
        val oldSession = pairedSessionFor(transcript)
        val oldTransaction = pairedTransactionFor(transcript)
        val oldRecord = registry.registerAfterVerifiedPairing(
            oldSession, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", now + 6
        )
        assertTrue(registry.revoke(oldRecord.fingerprint, now + 7))

        val replacement = createTranscript("DDDDDDDDDDDDDDDDDDDDDD", expiry + 5_000L)
        val replacementSession = pairedSessionFor(replacement)
        registry.registerAfterVerifiedPairing(
            replacementSession, replacement, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer re-paired", now + 9
        )
        assertFalse(TrustedDeviceAuthorization.mayAuthorizeNewSession(
            oldSession, transcript, oldTransaction, registry, now + 10
        ))
    }

    @Test
    fun rejectsTransactionFromDifferentTranscriptOrPeer() {
        val registry = TrustedDeviceRegistry()
        val session = pairedSessionFor(transcript)
        registry.registerAfterVerifiedPairing(
            session, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", now + 6
        )
        val wrong = pairedTransactionFor(transcript).copy(transcriptHashHex = "f".repeat(64))
        assertFalse(TrustedDeviceAuthorization.mayAuthorizeNewSession(
            session, transcript, wrong, registry, now + 7
        ))
    }

    @Test
    fun rejectsExpiredOrIncompleteAuthorization() {
        val registry = TrustedDeviceRegistry()
        val session = pairedSessionFor(transcript)
        registry.registerAfterVerifiedPairing(
            session, transcript, local.public.encoded, peer.public.encoded,
            PairingSignerRole.RESPONDER, "Peer", now + 6
        )
        val transaction = pairedTransactionFor(transcript)
        assertFalse(TrustedDeviceAuthorization.mayAuthorizeNewSession(
            session, transcript, transaction, registry, expiry
        ))
        val unpaired = PairingHandshakeSession.createForTranscript(
            transcript, local.public.encoded, now
        )
        assertFalse(TrustedDeviceAuthorization.mayAuthorizeNewSession(
            unpaired, transcript, transaction, registry, now + 7
        ))
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
}
