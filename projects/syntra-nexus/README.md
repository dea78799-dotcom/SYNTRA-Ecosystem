# SYNTRA NEXUS

**Version:** 0.8.0-dev  
**Status:** Local one-time invitation-token registry prototype; no network pairing yet  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is an opt-in device-management concept for devices that the owner or an authorized user has agreed to pair. The Android application is the first controller. TV support remains deferred until the target platform is selected.

## Current prototype scope

- Android dashboard with an explicit prototype status.
- Generate a QR invitation containing a cryptographically random, short-lived request ID and token; invitation lifetime is limited to two minutes.
- Strict invitation parser validates scheme, host, version, required fields, duplicate fields, token format, payload size, and expiry window.
- Scan QR codes using Google Play services Code Scanner; the app does not request direct camera permission.
- Valid scanned invitations are shown in a local review dialog; scanning does not establish trust or a network connection.
- Create a per-installation EC P-256 signing identity in Android Keystore. Private key material is non-exportable; the app displays only the public-key SHA-256 fingerprint.
- Build a canonical, versioned transcript with fixed field ordering, length-prefixed values, both public identities, invitation request ID, expiry, and independent 32-byte peer nonces.
- Sign and verify transcript hashes with ECDSA/SHA-256, a NEXUS-specific domain separator, and explicit initiator/responder role binding.
- Verify that a peer's supplied public key exactly matches the identity assigned to that role in the transcript.
- Reject invalid signatures, wrong-role signatures, identity mismatches, tampered transcripts, and proofs presented after expiry.
- Pin each handshake session to the SHA-256 hash of its exact canonical transcript and verify that the local controller key is the transcript's initiator identity; an unbound or substituted transcript is rejected.
- The controller accepts only the responder role as the remote peer, preventing the local initiator identity from being reflected as the peer.
- Integrate peer-proof verification into the handshake state machine. The transition into local approval is private and can only be reached through the verifier.
- Require a separate role-bound signature over an explicit APPROVE decision from the peer after local approval; the Android Keystore identity store can produce an approval signature only when its public key matches the signer's transcript role.
- The state machine requires local approval and cryptographically verified peer approval before entering PAIRED.
- Add a synchronized one-time token registry with explicit ACTIVE, REDEEMED, and REVOKED states.
- Compute and retain only a SHA-256 fingerprint of each 256-bit invitation token; the raw token is not stored by the registry.
- Enforce atomic redemption so concurrent calls against one registered token can produce only one REDEEMED result.
- Reject malformed and unknown tokens, expired invitations, previously redeemed tokens, revoked tokens, duplicate registrations, and attempts to extend an already registered token's expiry.
- Bound registry memory use and retain token fingerprints as process-local tombstones until the registry is discarded.
- JVM unit tests cover cryptographic proof verification, transcript pinning, signed approval, token expiry, duplicate registration, revocation, malformed/unknown tokens, token re-registration, and concurrent redemption.

## Not implemented yet

- Connecting the QR invitation issuer's registry to a second device; the current registry is a local code primitive and is not wired into the QR screen or scanner flow.
- Persistent token tombstones across app restarts.
- Distributed one-time redemption or replay prevention across multiple devices.
- Live exchange of fresh nonces and signatures between two devices.
- Exchange and transport of the signed peer-approval message over a live authenticated connection.
- TLS handshake, LAN transport, or NSD/mDNS discovery.
- Persistent trusted-device registry or remote revocation.
- Remote actions, TV client, or cloud sync.

**Important:** The 0.8.0-dev token registry is an in-memory component for JVM-level logic and tests. It protects against duplicate redemption only inside the same live registry instance. It does not survive process death, coordinate with another device, or establish a trusted connection. The QR scanner remains a local prototype feature. Do not use this build as a remote controller.

## Build and test

Use Gradle 8.9 and JDK 17:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The GitHub Actions workflow is configured to run unit tests and a debug build. A successful CI build validates compilation and the included unit tests; it does not replace testing on two physical devices. No successful build or test result is claimed until a workflow reports it.

## Security baseline

- Both devices must explicitly approve pairing before trust is established.
- QR invitations are short-lived and must be single-use when redemption is connected to the real pairing flow.
- Long-lived private keys must be generated and protected with Android Keystore.
- LAN peers must be authenticated; IP address or mDNS discovery alone is never proof of identity.
- Every future capability must be scoped, visible, and revocable.
- Never store passwords, pairing secrets, or private keys in source control.
- No hidden collection of messages, photos, screens, or private app data.
