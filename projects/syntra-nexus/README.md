# SYNTRA NEXUS

**Version:** 0.6.0-dev  
**Status:** Local pairing protocol foundations and handshake state machine; no network pairing yet  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is an opt-in device-management concept for devices that the owner or an authorized user has agreed to pair. The Android application is the first controller. TV support remains deferred until the target platform is selected.

## Current prototype scope

- Android dashboard with an explicit prototype status.
- Generate a QR invitation containing a cryptographically random, short-lived request ID and token; invitation lifetime is limited to two minutes.
- Strict invitation parser validates scheme, host, version, required fields, duplicate fields, token format, payload size, and expiry window.
- Scan QR codes using Google Play services Code Scanner; the app does not request direct camera permission.
- Valid scanned invitations are shown in a local review dialog; scanning does not establish trust or a network connection.
- Local-only invitation review state tracks pending, locally accepted, locally rejected, and expired invitations.
- Create a per-installation EC P-256 signing identity in Android Keystore. Private key material is non-exportable; the app displays only the public-key SHA-256 fingerprint.
- Pairing transcript signatures use ECDSA with SHA-256, a NEXUS-specific domain separator, and explicit initiator/responder role binding.
- Build a canonical, versioned transcript with fixed field ordering, length-prefixed values, both public identities, invitation request ID, expiry, and independent 32-byte peer nonces.
- A pure pairing handshake state machine enforces the sequence: invitation sent, peer connected, peer proof verified, local approval, peer approval, then paired.
- Terminal rejection, expiry, and failure states cannot be advanced to paired.
- JVM unit tests cover transcript determinism, field binding, input validation, defensive copies, role-bound signatures, and handshake transition rules.

## Not implemented yet

- Live exchange of fresh nonces and signatures between two devices.
- Actual cryptographic peer-proof verification wired into the state machine.
- One-time token redemption and replay prevention across devices.
- A remote peer's explicit approval message authenticated over a live connection.
- TLS handshake, LAN transport, or NSD/mDNS discovery.
- Persistent trusted-device registry or remote revocation.
- Remote actions, TV client, or cloud sync.

**Important:** The transcript, Android Keystore identity, and state machine are protocol foundations, not a paired device. The state machine assumes its caller has already verified peer signatures and authenticated peer approval; it does not perform those operations itself. Creating or scanning a QR still does not authenticate another device, redeem an invitation, or create a trusted-device record. Do not use this prototype as a remote controller.

## Build and test

Use Gradle 8.9 and JDK 17:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The GitHub Actions workflow is configured to run unit tests and a debug build. A successful CI build validates compilation and the included unit tests; it does not replace testing on two physical devices. No successful build or test result is claimed until a workflow reports it.

## Security baseline

- Both devices must explicitly approve pairing before trust is established.
- QR invitations are short-lived and must be single-use when redemption is implemented.
- Long-lived private keys must be generated and protected with Android Keystore.
- LAN peers must be authenticated; IP address or mDNS discovery alone is never proof of identity.
- Every future capability must be scoped, visible, and revocable.
- Never store passwords, pairing secrets, or private keys in source control.
- No hidden collection of messages, photos, screens, or private app data.
