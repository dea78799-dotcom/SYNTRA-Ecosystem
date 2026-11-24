# Pairing Transaction Coordinator — 0.11.14-dev

## Scope
This release adds a local coordinator around the existing transcript-bound pairing state machine. It is not a network transport and does not establish a live connection.

## State handling
- CREATED: a local transaction has been opened with request ID, expiry, transcript hash and peer fingerprint.
- TOKEN_REDEEMED: issuer-side one-time token redemption has completed.
- IDENTITY_VERIFIED: the existing handshake verifier has verified the responder proof against the exact transcript.
- LOCAL_APPROVAL_PENDING: the local user approved and the handshake is waiting for the peer's separately signed approval.
- PAIRED: accepted only when the supplied handshake session is PAIRED and BOTH_APPROVED.
- ABORTED / EXPIRED: terminal states.
- RECOVERY_REQUIRED: a restored non-terminal transaction. It cannot continue as if previous process memory or consent were still live.

## Security boundaries
- The coordinator does not accept a peer-approved Boolean.
- Peer identity and approval are delegated to PairingHandshakeSession and its cryptographic verifiers.
- Storage is committed before in-memory state mutation; storage errors fail closed.
- A redeemed QR token is never reactivated. Interrupted pairing requires a newly generated invitation.
- This version does not yet add an Android Keystore persistence adapter or connect the coordinator to UI/socket code.
- The TrustedDeviceRegistry remains the only local trust-store insertion point and still requires a PAIRED, BOTH_APPROVED handshake bound to the exact transcript.

## Verification status
Unit tests have been authored but have not been run in this environment. No APK, Android build, device test, or successful CI run is claimed.
