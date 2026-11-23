# SYNTRA NEXUS

**Version:** 0.5.0-dev  
**Status:** Canonical mutual-pairing transcript foundation; no network pairing yet  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is an opt-in device-management concept for devices that the owner or an authorized user has agreed to pair. The Android application is the first controller. TV support remains deferred until the target platform is selected.

## Current 0.4-dev scope

- Android dashboard with an explicit prototype status.
- Generate a QR invitation containing a cryptographically random, short-lived request ID and token.
- Invitation lifetime is limited to two minutes.
- Generate a new invitation instead of reusing an expired one.
- Unit tests for token format, randomness, and expiration.
- QR generation happens locally on the Android device.
- Strict invitation parser validates scheme, host, version, required fields, duplicate fields, token format, payload size, and expiry window.
- Unit tests cover valid parsing and malformed, expired, duplicated, unexpected, and overlong invitation data.
- Scan QR codes using Google Play services Code Scanner; the app does not request direct camera permission.
- Valid scanned invitations are shown in a review dialog without establishing trust or a network connection.
- A local-only review state machine tracks pending, locally accepted, locally rejected, and expired invitations.
- Expiry arithmetic is guarded against timestamp overflow, with boundary-condition unit tests.
- Create a per-installation EC P-256 signing identity in Android Keystore.
- Private key material is non-exportable; the app displays only the public-key SHA-256 fingerprint.
- Pairing transcript signatures use ECDSA with SHA-256 and a NEXUS-specific domain separator.
- Build a canonical, versioned transcript with fixed field ordering, length-prefixed values, both public identities, the invitation request ID, expiry, and independent 32-byte peer nonces.\n- Bind ECDSA/SHA-256 signatures to the initiator or responder role to prevent signature reflection between protocol roles.\n- JVM unit tests cover transcript determinism, field binding, input validation, defensive copies, role-bound signatures, wrong-key rejection and transcript tampering.

## Not implemented yet

- Exchange fresh nonces with a remote peer and verify both role-bound signatures over the same transcript.\n- Mutual peer authentication and verification of a remote device identity.
- A canonical pairing transcript builder with fresh nonces from both peers.
- One-time token redemption and replay prevention across devices.
- Mutual device approval.
- TLS handshake or LAN transport.
- NSD/mDNS discovery.
- Persistent trusted-device registry or revocation.
- Remote actions, TV client, cloud sync.

**Important:** The canonical transcript and local Android Keystore identity are protocol foundations, not a paired device. Creating or scanning a QR still does not authenticate another device, redeem an invitation, or create a trusted-device record. Do not use this build as a remote controller.

## Build and test

Use Gradle 8.9 and JDK 17:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The GitHub Actions workflow runs the unit tests and debug build. The scanner uses Google Play services; its scanner module may need to be available or downloaded before first use. A successful CI build validates compilation and the included unit tests; it does not replace testing on two physical devices.

## Security baseline

- Both devices must explicitly approve pairing before trust is established.
- QR invitations are short-lived and must be single-use when redemption is implemented.
- Long-lived private keys must be generated and protected with Android Keystore.
- LAN peers must be authenticated; IP address or mDNS discovery alone is never proof of identity.
- Every future capability must be scoped, visible, and revocable.
- Never store passwords, pairing secrets, or private keys in source control.
- No hidden collection of messages, photos, screens, or private app data.
