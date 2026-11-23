package com.syntra.nexus

/**
 * Local-only review state for an invitation scanned by this device.
 *
 * ACCEPTED_LOCALLY means only that the person using this device chose to proceed.
 * It does not mean the inviting device approved, a peer was authenticated,
 * a token was redeemed, or a connection/trusted-device record exists.
 */
enum class PairingReviewStatus {
    PENDING_REVIEW,
    ACCEPTED_LOCALLY,
    REJECTED_LOCALLY,
    EXPIRED
}

data class PairingReviewSession(
    val requestId: String,
    val expiresAtMillis: Long,
    val status: PairingReviewStatus = PairingReviewStatus.PENDING_REVIEW
) {
    fun refresh(nowMillis: Long = System.currentTimeMillis()): PairingReviewSession {
        if (status != PairingReviewStatus.PENDING_REVIEW || nowMillis < expiresAtMillis) {
            return this
        }
        return copy(status = PairingReviewStatus.EXPIRED)
    }

    fun acceptLocally(nowMillis: Long = System.currentTimeMillis()): PairingReviewSession {
        val current = refresh(nowMillis)
        require(current.status == PairingReviewStatus.PENDING_REVIEW) {
            "Only a pending, unexpired invitation can be accepted locally."
        }
        return current.copy(status = PairingReviewStatus.ACCEPTED_LOCALLY)
    }

    fun rejectLocally(nowMillis: Long = System.currentTimeMillis()): PairingReviewSession {
        val current = refresh(nowMillis)
        require(current.status == PairingReviewStatus.PENDING_REVIEW) {
            "Only a pending invitation can be rejected."
        }
        return current.copy(status = PairingReviewStatus.REJECTED_LOCALLY)
    }

    companion object {
        fun from(
            invitation: ParsedPairingInvitation,
            nowMillis: Long = System.currentTimeMillis()
        ): PairingReviewSession {
            require(invitation.requestId.isNotBlank()) { "Invitation ID is missing." }
            require(invitation.expiresAtMillis > nowMillis) { "Invitation has expired." }
            return PairingReviewSession(
                requestId = invitation.requestId,
                expiresAtMillis = invitation.expiresAtMillis
            )
        }
    }
}
