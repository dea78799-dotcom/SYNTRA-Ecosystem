package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OneTimePairingTokenRegistryTest {
    @Test
    fun redeemsRegisteredTokenOnlyOnce() {
        val request = PairingRequest.create(nowMillis = 10_000L)
        val invitation = request.toInvitation()
        val registry = OneTimePairingTokenRegistry()

        assertTrue(registry.register(invitation, nowMillis = 10_001L))
        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.REDEEMED,
            registry.redeem(request.token, nowMillis = 10_002L)
        )
        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.ALREADY_REDEEMED,
            registry.redeem(request.token, nowMillis = 10_003L)
        )
    }

    @Test
    fun rejectsUnknownAndMalformedTokens() {
        val request = PairingRequest.create(nowMillis = 1_000L)
        val registry = OneTimePairingTokenRegistry()
        registry.register(request.toInvitation(), nowMillis = 1_001L)

        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.UNKNOWN_TOKEN,
            registry.redeem(PairingRequest.create(nowMillis = 1_000L).token, nowMillis = 1_002L)
        )
        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.INVALID_TOKEN,
            registry.redeem("not-a-token", nowMillis = 1_002L)
        )
    }

    @Test
    fun rejectsExpiredToken() {
        val request = PairingRequest.create(nowMillis = 5_000L, ttlMillis = 1_000L)
        val registry = OneTimePairingTokenRegistry()
        registry.register(request.toInvitation(), nowMillis = 5_100L)

        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.EXPIRED,
            registry.redeem(request.token, nowMillis = request.expiresAtMillis)
        )
    }

    @Test
    fun rejectsDuplicateRegistration() {
        val request = PairingRequest.create(nowMillis = 5_000L)
        val registry = OneTimePairingTokenRegistry()
        val invitation = request.toInvitation()

        assertTrue(registry.register(invitation, nowMillis = 5_001L))
        assertFalse(registry.register(invitation, nowMillis = 5_002L))
        assertEquals(1, registry.registeredCount())
    }

    @Test
    fun expiredTokenFingerprintCannotBeReRegisteredWithNewExpiry() {
        val request = PairingRequest.create(nowMillis = 5_000L, ttlMillis = 1_000L)
        val registry = OneTimePairingTokenRegistry()
        assertTrue(registry.register(request.toInvitation(), nowMillis = 5_001L))

        val alteredInvitation = request.toInvitation().copy(
            expiresAtMillis = 10_000L
        )
        assertFalse(registry.register(alteredInvitation, nowMillis = 6_001L))
    }

    @Test
    fun revokedTokenCannotBeRedeemed() {
        val request = PairingRequest.create(nowMillis = 5_000L)
        val registry = OneTimePairingTokenRegistry()
        registry.register(request.toInvitation(), nowMillis = 5_001L)

        assertTrue(registry.revoke(request.token, nowMillis = 5_002L))
        assertEquals(
            OneTimePairingTokenRegistry.RedemptionResult.REVOKED,
            registry.redeem(request.token, nowMillis = 5_003L)
        )
    }

    @Test
    fun concurrentRedemptionHasExactlyOneWinner() {
        val request = PairingRequest.create(nowMillis = 5_000L)
        val registry = OneTimePairingTokenRegistry()
        registry.register(request.toInvitation(), nowMillis = 5_001L)

        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val redeemed = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(workers)

        try {
            repeat(workers) {
                executor.submit {
                    ready.countDown()
                    start.await()
                    if (registry.redeem(request.token, nowMillis = 5_002L) ==
                        OneTimePairingTokenRegistry.RedemptionResult.REDEEMED
                    ) {
                        redeemed.incrementAndGet()
                    }
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(1, redeemed.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvitationWithExcessiveLifetime() {
        val request = PairingRequest.create(nowMillis = 1_000L)
        OneTimePairingTokenRegistry().register(
            request.toInvitation().copy(
                expiresAtMillis = 1_000L + PairingRequest.DEFAULT_TTL_MILLIS + 1L
            ),
            nowMillis = 1_000L
        )
    }

    private fun PairingRequest.toInvitation() = ParsedPairingInvitation(
        requestId = requestId,
        token = token,
        expiresAtMillis = expiresAtMillis
    )
}
