package com.syntra.nexus

/**
 * Fail-closed gate for a future screen-sharing transport.
 *
 * LAN discovery, an IP address, or a QR scan alone must never satisfy these
 * requirements. This class does not open sockets or capture screen content.
 */
data class ScreenShareAuthorization(
    val peerIdentityVerified: Boolean,
    val pairingApprovedByBothDevices: Boolean,
    val screenShareCapabilityGrantedByBothDevices: Boolean,
    val sharerMediaProjectionConsentGranted: Boolean,
    val receiverAcceptedThisSession: Boolean,
    val encryptedTransportEstablished: Boolean,
    val visibleForegroundNotificationActive: Boolean
) {
    fun mayStartFrameTransmission(): Boolean =
        peerIdentityVerified &&
            pairingApprovedByBothDevices &&
            screenShareCapabilityGrantedByBothDevices &&
            sharerMediaProjectionConsentGranted &&
            receiverAcceptedThisSession &&
            encryptedTransportEstablished &&
            visibleForegroundNotificationActive

    fun missingRequirements(): List<String> = buildList {
        if (!peerIdentityVerified) add("peer_identity")
        if (!pairingApprovedByBothDevices) add("mutual_pairing")
        if (!screenShareCapabilityGrantedByBothDevices) add("capability_grant")
        if (!sharerMediaProjectionConsentGranted) add("media_projection_consent")
        if (!receiverAcceptedThisSession) add("receiver_session_approval")
        if (!encryptedTransportEstablished) add("encrypted_transport")
        if (!visibleForegroundNotificationActive) add("visible_notification")
    }
}

/**
 * Metadata validation for a future encoded frame. It deliberately accepts no
 * pixel bytes and performs no capture, storage, or network transmission.
 */
data class ScreenShareFrameMetadata(
    val sessionId: String,
    val sequenceNumber: Long,
    val width: Int,
    val height: Int,
    val encodedBytes: Int
) {
    fun isValid(): Boolean =
        sessionId.matches(Regex("[A-Za-z0-9_-]{16,64}")) &&
            sequenceNumber >= 0L &&
            width in 1..MAX_DIMENSION &&
            height in 1..MAX_DIMENSION &&
            encodedBytes in 1..MAX_ENCODED_FRAME_BYTES

    companion object {
        const val MAX_DIMENSION = 4096
        const val MAX_ENCODED_FRAME_BYTES = 4 * 1024 * 1024
    }
}
