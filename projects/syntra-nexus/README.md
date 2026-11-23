# SYNTRA NEXUS

**Version:** 0.2.0-dev  
**Status:** Pairing invitation prototype  
**Platform:** Android-first  
**Network model:** Local-first

SYNTRA NEXUS is an opt-in device-management concept for devices that the owner or an authorized user has agreed to pair. The Android application is the first controller. TV support remains deferred until the target platform is selected.

## Current 0.2-dev scope

- Android dashboard with an explicit prototype status.
- Generate a QR invitation containing a cryptographically random, short-lived request ID and token.
- Invitation lifetime is limited to two minutes.
- Generate a new invitation instead of reusing an expired one.
- Unit tests for token format, randomness, and expiration.
- QR generation happens locally on the Android device.
- Strict invitation parser validates scheme, host, version, required fields, duplicate fields, token format, payload size, and expiry window.
- Unit tests cover valid parsing and malformed, expired, duplicated, unexpected, and overlong invitation data.

## Not implemented yet

- Camera-based QR scanning UI and receiving-side flow.
- One-time token redemption and replay prevention across devices.
- Mutual device approval.
- Android Keystore-backed device identity.
- TLS handshake or LAN transport.
- NSD/mDNS discovery.
- Persistent trusted-device registry or revocation.
- Remote actions, TV client, cloud sync.

**Important:** A displayed QR is only a temporary invitation prototype. It does not mean another device has connected or become trusted. Do not use this build as a remote controller.

## Build and test

Use Gradle 8.9 and JDK 17:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

The GitHub Actions workflow runs the unit tests and debug build. A successful CI build validates compilation and the included unit tests; it does not replace testing on two physical devices.

## Security baseline

- Both devices must explicitly approve pairing before trust is established.
- QR invitations are short-lived and must be single-use when redemption is implemented.
- Long-lived private keys must be generated and protected with Android Keystore.
- LAN peers must be authenticated; IP address or mDNS discovery alone is never proof of identity.
- Every future capability must be scoped, visible, and revocable.
- Never store passwords, pairing secrets, or private keys in source control.
- No hidden collection of messages, photos, screens, or private app data.
