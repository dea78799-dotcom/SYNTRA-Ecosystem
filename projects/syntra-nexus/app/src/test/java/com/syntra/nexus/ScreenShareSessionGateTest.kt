package com.syntra.nexus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenShareSessionGateTest {
    private fun approved(
        peerIdentityVerified: Boolean = true,
        pairingApprovedByBothDevices: Boolean = true,
        screenShareCapabilityGrantedByBothDevices: Boolean = true,
        sharerMediaProjectionConsentGranted: Boolean = true,
        receiverAcceptedThisSession: Boolean = true,
        encryptedTransportEstablished: Boolean = true,
        visibleForegroundNotificationActive: Boolean = true
    ) = ScreenShareAuthorization(
        peerIdentityVerified,
        pairingApprovedByBothDevices,
        screenShareCapabilityGrantedByBothDevices,
        sharerMediaProjectionConsentGranted,
        receiverAcceptedThisSession,
        encryptedTransportEstablished,
        visibleForegroundNotificationActive
    )

    @Test
    fun allRequirementsMustBeSatisfied() {
        assertTrue(approved().mayStartFrameTransmission())
        assertTrue(approved().missingRequirements().isEmpty())
    }

    @Test
    fun missingAnySingleRequirementBlocksTransmission() {
        val cases = listOf(
            approved(peerIdentityVerified = false),
            approved(pairingApprovedByBothDevices = false),
            approved(screenShareCapabilityGrantedByBothDevices = false),
            approved(sharerMediaProjectionConsentGranted = false),
            approved(receiverAcceptedThisSession = false),
            approved(encryptedTransportEstablished = false),
            approved(visibleForegroundNotificationActive = false)
        )

        cases.forEach { authorization ->
            assertFalse(authorization.mayStartFrameTransmission())
            assertEquals(1, authorization.missingRequirements().size)
        }
    }

    @Test
    fun frameMetadataRejectsInvalidDimensionsAndOversizedPayload() {
        val valid = ScreenShareFrameMetadata("session_identifier_123", 0, 1920, 1080, 1024)
        assertTrue(valid.isValid())
        assertFalse(valid.copy(width = 0).isValid())
        assertFalse(valid.copy(height = 4097).isValid())
        assertFalse(valid.copy(encodedBytes = ScreenShareFrameMetadata.MAX_ENCODED_FRAME_BYTES + 1).isValid())
        assertFalse(valid.copy(sequenceNumber = -1).isValid())
        assertFalse(valid.copy(sessionId = "short").isValid())
    }
}
